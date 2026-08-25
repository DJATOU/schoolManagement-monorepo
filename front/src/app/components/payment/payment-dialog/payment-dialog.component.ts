import { Component, Inject, OnInit } from '@angular/core';
import { Observable } from 'rxjs';
import {
  AbstractControl, FormBuilder, FormGroup, ReactiveFormsModule,
  ValidationErrors, Validators
} from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef, MatDialog, MatDialogModule } from '@angular/material/dialog';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatOptionModule } from '@angular/material/core';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Group } from '../../../models/group/group';
import { SessionSeries } from '../../../models/sessionSerie/sessionSerie';
import { SeriesService } from '../../../services/series.service';
import { PaymentService } from '../../../services/payment.service';
import { Payment } from '../../../models/payment/payment';
import { PaymentConfirmationDialogComponent } from '../payment-confirmation-dialog/payment-confirmation-dialog.component';
import { MatSnackBar } from '@angular/material/snack-bar';
import { PaymentDetail } from '../../../models/paymentDetail/paymentDetail';
import { PaymentQuote } from '../../../models/payment/payment-quote';
import { PaymentAllocationResult } from '../../../models/payment/payment-allocation';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { AdminOnlyDirective } from '../../../shared/admin-only.directive';
import { PaymentReceiptPdfService } from '../../../services/payment-receipt-pdf.service';
import { AuthService } from '../../../services/auth.service';
import { GroupChangeNoticeComponent } from '../../shared/group-change-notice/group-change-notice.component';

/**
 * Éléments du reçu figés au moment de la soumission.
 *
 * <p>Le dialogue se ferme dès la réponse serveur : sans cette copie, les libellés et les
 * montants du devis ne seraient plus accessibles à la génération du reçu.</p>
 */
interface ReceiptContext {
  amountPaid: number;
  groupName: string;
  seriesName: string;
  paymentMethodLabel: string;
  description?: string;
  isCatchUp: boolean;
  /** Coût au prorata de la série, ou montant dû à ce jour en mode rattrapage. */
  seriesTotalCost?: number;
  /** Séances facturables retenues dans ce coût, imprimées à côté de lui sur le reçu. */
  billableSessions?: number;
  /**
   * Cumul versé sur la série <strong>avant</strong> ce versement.
   *
   * <p>Le cumul après versement ne peut pas être figé ici : il dépend de la part réellement
   * imputée, connue de la seule réponse serveur. L'ajout de la totalité du montant saisi
   * gonflerait le cumul et minorerait le reste à payer dès qu'un report a lieu.</p>
   */
  seriesAlreadyPaid?: number;
}

/** Une part de versement reportée sur une série, telle que prévue avant validation. */
interface AllocationPreviewLine {
  seriesId: number;
  seriesName: string;
  amount: number;
}

/**
 * Répartition prévisionnelle du montant saisi sur la chaîne des séries du groupe.
 *
 * <p>Reproduit dans le navigateur la règle appliquée par le serveur : imputation sur la série
 * visée à hauteur de son plafond, puis report du surplus sur les séries suivantes par
 * identifiant croissant. Le serveur reste l'autorité — cet aperçu sert à annoncer le report
 * avant validation (exigence 9.3), et à éviter que l'administrateur découvre un refus après
 * avoir encaissé l'argent.</p>
 */
interface AllocationPreview {
  /** Montant saisi. */
  amountReceived: number;
  /** Part imputée sur la série visée. */
  allocated: number;
  /** Parts reportées, par identifiant de série croissant. */
  carryOvers: AllocationPreviewLine[];
  /** Reliquat qu'aucune série ne peut recevoir : le serveur refuserait le versement en entier. */
  unplaceable: number;
  /**
   * Nom de la première série rencontrée sans aucune séance. Une telle série existe mais n'est
   * pas ouverte : elle ne peut rien recevoir tant que ses séances n'ont pas été créées.
   */
  blockingSeriesName: string | null;
}

/**
 * Exige un montant strictement positif.
 *
 * <p>{@link Validators.min} est inclusif : {@code min(0)} laissait passer un versement nul,
 * qui créait une ligne de paiement sans rien encaisser (cas d'une série soldée, exemptée ou
 * sans tarif, où le plafond encaissable vaut 0). Le contrôle vide reste du ressort de
 * {@link Validators.required}, pour ne pas afficher deux erreurs à la fois.</p>
 */
function positiveAmountValidator(control: AbstractControl): ValidationErrors | null {
  const value = control.value;
  if (value === null || value === undefined || value === '') {
    return null;
  }
  return Number(value) > 0 ? null : { nonPositiveAmount: true };
}

@Component({
  selector: 'app-payment-dialog',
  standalone: true,
  templateUrl: './payment-dialog.component.html',
  styleUrls: ['./payment-dialog.component.scss'],
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatOptionModule,
    MatCheckboxModule,
    MatIconModule,
    MatTooltipModule,
    TranslateModule,
    AdminOnlyDirective,
    GroupChangeNoticeComponent
  ]
})
export class PaymentDialogComponent implements OnInit {
  paymentForm: FormGroup;
  groups: Group[];
  sessionSeries: SessionSeries[] = [];
  paymentMethods = [
    { value: 'cash', labelKey: 'payment.dialog.methods.cash' },
    { value: 'cheque', labelKey: 'payment.dialog.methods.cheque' },
    { value: 'carte_bancaire', labelKey: 'payment.dialog.methods.card' },
    { value: 'autre', labelKey: 'payment.dialog.methods.other' }
  ];
  studentId: number;
  /** Nom de l'étudiant, imprimé sur le reçu. Vide si l'appelant ne l'a pas fourni. */
  studentName: string;
  paymentDetails: PaymentDetail[] = [];
  nextCatchUpSessionId: number | null = null;

  /**
   * Devis de la série sélectionnée, calculé par le serveur : tarif catalogue, réduction,
   * prix net, coût de la série, déjà versé et plafond encaissable.
   *
   * <p>Ces montants étaient auparavant recalculés dans le navigateur à partir du tarif
   * catalogue, sans appliquer la réduction : un étudiant réduit se voyait proposer le plein
   * tarif et pouvait verser plus que son dû.</p>
   */
  quote: PaymentQuote | null = null;
  loadingQuote = false;
  quoteError = '';

  /**
   * Devis de chaque série du groupe choisi, indexés par identifiant de série.
   *
   * <p>Chargés en une requête dès la sélection du groupe : ils servent à griser dans le
   * sélecteur les séries qui n'ont plus rien à encaisser, avant tout choix de l'utilisateur.</p>
   */
  readonly seriesQuotes = new Map<number, PaymentQuote>();

  /**
   * Répartition prévisionnelle du montant saisi, recalculée à chaque frappe.
   *
   * <p>Stockée dans un champ plutôt que calculée par un accesseur appelé depuis le template :
   * l'accesseur reconstruirait la liste des reports à chaque cycle de détection, ce qui ferait
   * clignoter le tableau des séries destinataires.</p>
   */
  allocationPreview: AllocationPreview | null = null;

  constructor(
    private fb: FormBuilder,
    private dialogRef: MatDialogRef<PaymentDialogComponent>,
    private sessionSeriesService: SeriesService,
    private paymentService: PaymentService,
    private dialog: MatDialog,
    private snackBar: MatSnackBar,
    private translate: TranslateService,
    private receiptPdfService: PaymentReceiptPdfService,
    private authService: AuthService,
    @Inject(MAT_DIALOG_DATA) public data: { studentId: number, groups: Group[], studentName?: string }
  ) {
    this.groups = data.groups;
    this.studentId = data.studentId;
    this.studentName = data.studentName?.trim() || '';

    this.paymentForm = this.fb.group({
      groupId: [null, Validators.required],
      sessionSeriesId: [null, Validators.required],
      // Strictement positif : encaisser 0 ne remet rien à l'étudiant et ne crée qu'une
      // ligne de paiement vide. Le plafond haut reste porté par le validateur « max ».
      amountPaid: [null, [Validators.required, positiveAmountValidator]],
      // Espèces par défaut : c'est le mode de règlement utilisé dans la très grande
      // majorité des cas à l'accueil de l'école.
      paymentMethod: ['cash', Validators.required],
      // Paiement intégral de la série : ne sert qu'à préremplir le montant.
      // Désactivé jusqu'à ce qu'une série et un tarif soient connus.
      fullSeriesPayment: [{ value: false, disabled: true }],
      paymentDescription: ['']
    });
  }

  ngOnInit(): void {
    this.paymentForm.get('groupId')!.valueChanges.subscribe(groupId => {
      this.loadSessionSeries(groupId);
    });

    this.paymentForm.get('sessionSeriesId')!.valueChanges.subscribe(seriesId => {
      this.loadQuote(seriesId);
    });

    // Le basculement de la case force le montant : c'est une action explicite de
    // l'utilisateur, elle doit écraser une saisie manuelle éventuelle.
    this.paymentForm.get('fullSeriesPayment')!.valueChanges.subscribe(() => {
      this.applyAmountPreset(true);
    });

    // L'aperçu de répartition suit la saisie : le report doit être annoncé avant validation,
    // et non découvert au retour du serveur.
    this.paymentForm.get('amountPaid')!.valueChanges.subscribe(() => {
      this.refreshAllocationPreview();
    });
  }

  /** Indique si la case « paiement intégral » est cochée. */
  get isFullSeriesPayment(): boolean {
    return !!this.paymentForm.get('fullSeriesPayment')!.value;
  }

  /**
   * Clé du message expliquant le refus d'un montant nul ou négatif.
   *
   * <p>« Le montant doit être positif » est exact mais peu utile : le plus souvent, 0 a été
   * saisi parce qu'il n'y avait rien à encaisser. On nomme donc la cause réelle — exemption,
   * série soldée — comme le fait le serveur, pour que les deux disent la même chose.</p>
   */
  get nonPositiveAmountKey(): string {
    if (this.quote?.exempted) {
      return 'payment.dialog.errors.exemptedNothingDue';
    }
    if (this.quote && this.quote.maxPayable <= 0) {
      return 'payment.dialog.errors.alreadySettled';
    }
    return 'payment.dialog.errors.mustBePositive';
  }

  /** Prix net d'une séance, réduction appliquée. */
  get netSessionPrice(): number | null {
    return this.quote ? this.quote.netPricePerSession : null;
  }

  /** Vrai lorsque l'étudiant bénéficie d'une réduction sur cette série. */
  get hasDiscount(): boolean {
    return !!this.quote && this.quote.discountRate > 0;
  }

  /** Taux de réduction en pourcentage, pour l'affichage (0.65 → 65). */
  get discountPercent(): number {
    return this.quote ? this.round(this.quote.discountRate * 100) : 0;
  }

  /**
   * Montant à verser pour solder la série : le plafond encaissable calculé par le serveur,
   * acompte déjà déduit.
   */
  get fullSeriesAmount(): number | null {
    return this.quote ? this.quote.maxPayable : null;
  }

  /**
   * La case n'est activable que s'il reste quelque chose à encaisser : une série soldée, sans
   * séance, ou un étudiant exempté n'ont rien à régler.
   */
  get canPayFullSeries(): boolean {
    return !!this.quote && this.quote.maxPayable > 0;
  }

  /** Reste à payer sur la série. */
  get seriesRemaining(): number | null {
    return this.quote ? this.quote.remainingToPay : null;
  }

  /** Coût de la série complète, réduction appliquée. */
  get seriesTotalCost(): number | null {
    return this.quote ? this.quote.monthTotalCost : null;
  }

  /** Montant déjà versé sur la série. */
  get seriesAlreadyPaid(): number {
    return this.quote ? this.quote.amountPaid : 0;
  }

  /**
   * Nombre de séances facturables à l'étudiant sur la série choisie.
   *
   * <p>Ce n'est pas le nombre de séances de la série : une séance tenue avant l'arrivée de
   * l'étudiant dans le groupe, et à laquelle il n'a pas assisté, ne lui est pas facturée.</p>
   */
  get billableSessions(): number {
    return this.quote ? this.quote.billableSessions : 0;
  }

  /** Nombre de séances écartées de la facturation pour cet étudiant. */
  get excludedSessions(): number {
    return this.quote ? this.quote.excludedSessions : 0;
  }

  /**
   * Vrai lorsque au moins une séance de la série n'est pas facturée à l'étudiant.
   *
   * <p>Sert à afficher le motif d'exclusion : sans lui, un coût inférieur au coût nominal de la
   * série paraîtrait arbitraire et l'administrateur ne pourrait pas l'expliquer à la famille
   * (exigence 9.2).</p>
   */
  get hasExcludedSessions(): boolean {
    return this.excludedSessions > 0;
  }

  /**
   * Excédent déjà encaissé sur la série au-delà du coût au prorata, jamais négatif.
   *
   * <p>Strictement positif, la série a été sur-encaissée avant l'entrée en vigueur du prorata :
   * aucune reprise de données n'est prévue, l'écart reste affiché tel quel.</p>
   */
  get existingExcess(): number {
    return this.quote ? this.quote.existingExcess : 0;
  }

  /**
   * Maximum réellement encaissable en une saisie, à partir de la série choisie.
   *
   * <p>Le plafond de la saisie n'est plus celui de la série visée : le surplus est reporté sur
   * les séries suivantes, donc refuser au plafond de la série interdirait un report parfaitement
   * légitime. Le plafond est la somme des plafonds de la série visée et de toutes les séries
   * suivantes, calculée à partir des devis déjà chargés pour le groupe.</p>
   *
   * <p>Les séries <strong>antérieures</strong> sont exclues : le report va par identifiant
   * croissant et ne remonte jamais la chaîne. Une série sans séance a un plafond nul et
   * n'apporte donc rien à ce total, comme côté serveur.</p>
   */
  get chainMaxPayable(): number | null {
    if (!this.quote) {
      return null;
    }
    const startId = this.quote.seriesId;
    let total = 0;
    for (const series of this.sessionSeries) {
      if (series.id === undefined || series.id < startId) {
        continue;
      }
      const quote = series.id === startId ? this.quote : this.seriesQuotes.get(series.id);
      if (quote) {
        total += Math.max(0, quote.maxPayable);
      }
    }
    // Devis du groupe indisponibles : la série visée porte seule le plafond, ce qui reste plus
    // permissif que l'ancien comportement et laisse le serveur trancher.
    return this.round(Math.max(total, Math.max(0, this.quote.maxPayable)));
  }

  /** Nom de la série choisie, pour nommer la destination de la part imputée. */
  get selectedSeriesName(): string {
    const seriesId = this.paymentForm.get('sessionSeriesId')!.value;
    const series = this.sessionSeries.find(candidate => candidate.id === seriesId);
    return series?.name ?? '';
  }

  /** Vrai lorsque la chaîne peut recevoir plus que la seule série visée : un report est possible. */
  get carryOverPossible(): boolean {
    const chainMax = this.chainMaxPayable;
    return !!this.quote && chainMax !== null && chainMax > this.quote.maxPayable;
  }

  loadSessionSeries(groupId: number | null): void {
    if (!groupId) {
      return;
    }
    this.sessionSeriesService.getSessionSeriesByGroupId(groupId).subscribe({
      next: (series) => {
        // Le serveur trie les séries par identifiant croissant, donc par ordre d'ajout.
        // On conserve cet ordre tel quel : le retrier ici risquerait de le contredire.
        this.sessionSeries = series;
        this.paymentForm.get('sessionSeriesId')!.setValue(null);
        this.loadSeriesQuotes(groupId);
      },
      error: (err) => {
        console.error('Erreur lors du chargement des séries de sessions :', err);
      }
    });
  }

  /**
   * Charge le devis de chaque série du groupe, pour savoir lesquelles n'ont plus rien à
   * encaisser avant même que l'utilisateur en choisisse une.
   *
   * <p>Un échec est silencieux côté indicateurs : aucune série n'est grisée et le contrôle
   * habituel s'applique à la validation. Mieux vaut un sélecteur sans indication qu'un
   * sélecteur bloqué.</p>
   */
  private loadSeriesQuotes(groupId: number): void {
    this.seriesQuotes.clear();
    this.paymentService.getPaymentQuotesForGroup(this.studentId, groupId).subscribe({
      next: (quotes) => {
        quotes.forEach(quote => this.seriesQuotes.set(quote.seriesId, quote));
        // Ces devis portent le plafond de chaque série suivante : ils déterminent le maximum
        // encaissable sur la chaîne, donc le plafond de la saisie et l'aperçu du report.
        this.refreshAmountConstraints();
      },
      error: (err) => console.error('Erreur lors du chargement des devis des séries :', err)
    });
  }

  /** Vrai si la série n'a plus rien à encaisser : soldée, ou étudiant exempté. */
  isSeriesSettled(seriesId: number | undefined): boolean {
    if (seriesId === undefined) {
      return false;
    }
    const quote = this.seriesQuotes.get(seriesId);
    return !!quote && quote.maxPayable <= 0;
  }

  /**
   * Infobulle expliquant pourquoi une série est grisée.
   *
   * <p>Un simple grisage sans motif laisse l'administrateur supposer un bug ; on nomme donc la
   * cause, en distinguant l'exemption de la série déjà soldée.</p>
   */
  settledSeriesTooltip(seriesId: number | undefined): string {
    if (seriesId === undefined) {
      return '';
    }
    const quote = this.seriesQuotes.get(seriesId);
    if (!quote) {
      return '';
    }
    if (quote.exempted) {
      return this.translate.instant('payment.dialog.errors.exemptedNothingDue');
    }
    return this.translate.instant('payment.dialog.seriesSettledTooltip', {
      paid: quote.amountPaid,
      total: quote.monthTotalCost
    });
  }

  /**
   * Charge le devis serveur pour la série choisie : tarifs, réduction, coût, déjà versé et
   * plafond encaissable.
   */
  private loadQuote(seriesId: number | null): void {
    this.quote = null;
    this.quoteError = '';

    if (!seriesId) {
      this.refreshAmountConstraints();
      return;
    }

    this.loadingQuote = true;
    this.paymentService.getPaymentQuote(this.studentId, seriesId).subscribe({
      next: quote => {
        this.quote = quote;
        this.loadingQuote = false;
        this.refreshAmountConstraints();
      },
      error: (err: Error) => {
        this.quoteError = err.message || this.translate.instant('payment.dialog.messages.quoteError');
        this.loadingQuote = false;
        this.refreshAmountConstraints();
      }
    });
  }

  /**
   * Réaligne le plafond de saisie et le montant prérempli sur le devis.
   *
   * <p>Le plafond appliqué est celui de la <strong>chaîne</strong> des séries, et non celui de
   * la série visée : le surplus est désormais reporté sur les séries suivantes, si bien que
   * refuser au plafond de la série interdirait un report légitime — et empêcherait du même coup
   * le serveur d'expliquer, le cas échéant, qu'il faut d'abord créer les séances de la série
   * suivante pour l'ouvrir.</p>
   *
   * <p>Le refus du montant nul ou négatif est conservé : {@link Validators.min} étant inclusif,
   * il laisserait passer un versement nul.</p>
   */
  private refreshAmountConstraints(): void {
    const amountControl = this.paymentForm.get('amountPaid')!;
    const max = this.chainMaxPayable;

    amountControl.setValidators(max !== null
      ? [Validators.required, positiveAmountValidator, Validators.max(max)]
      : [Validators.required, positiveAmountValidator]);
    amountControl.updateValueAndValidity({ emitEvent: false });

    // On pilote l'état activé/désactivé par le FormControl : l'attribut [disabled] dans
    // le template déclencherait l'avertissement Angular des formulaires réactifs.
    const fullSeriesControl = this.paymentForm.get('fullSeriesPayment')!;
    if (this.canPayFullSeries) {
      if (fullSeriesControl.disabled) {
        fullSeriesControl.enable({ emitEvent: false });
      }
    } else {
      if (fullSeriesControl.value) {
        fullSeriesControl.setValue(false, { emitEvent: false });
      }
      if (fullSeriesControl.enabled) {
        fullSeriesControl.disable({ emitEvent: false });
      }
    }

    this.applyAmountPreset(this.isFullSeriesPayment);
    this.refreshAllocationPreview();
  }

  /**
   * Recalcule la répartition prévisionnelle du montant saisi.
   *
   * <p>Appelé à chaque frappe et à chaque arrivée de devis : le report annoncé doit refléter la
   * saisie courante, sinon l'aperçu contredirait le montant affiché.</p>
   */
  private refreshAllocationPreview(): void {
    const amount = Number(this.paymentForm.get('amountPaid')!.value);
    this.allocationPreview = this.buildAllocationPreview(amount);
  }

  /**
   * Reproduit la règle de répartition du serveur sur les devis déjà chargés.
   *
   * <p>Parcourt les séries du groupe par identifiant croissant à partir de la série visée. Une
   * série sans séance facturable est écartée : elle existe mais n'est pas ouverte, et ne peut
   * rien recevoir tant que ses séances n'ont pas été créées. Une série soldée ou exemptée donne
   * un plafond nul : elle est sautée en silence et le report continue.</p>
   *
   * <p>Les deux motifs sont distingués : les confondre produirait un message trompeur, « série
   * soldée » là où il faut dire « créez les séances pour ouvrir la série ».</p>
   *
   * @param amount le montant saisi
   * @return la répartition prévue, ou {@code null} faute de devis ou de montant exploitable
   */
  private buildAllocationPreview(amount: number): AllocationPreview | null {
    if (!this.quote || !Number.isFinite(amount) || amount <= 0) {
      return null;
    }

    const startId = this.quote.seriesId;
    let remaining = this.round(amount);
    let allocated = 0;
    const carryOvers: AllocationPreviewLine[] = [];
    let blockingSeriesName: string | null = null;

    for (const series of this.sessionSeries) {
      if (series.id === undefined || series.id < startId) {
        continue;
      }
      if (remaining <= 0) {
        break;
      }
      const quote = series.id === startId ? this.quote : this.seriesQuotes.get(series.id);
      if (!quote) {
        // Devis absent : on ne devine pas un plafond, le serveur tranchera.
        continue;
      }
      if (quote.billableSessions === 0) {
        if (blockingSeriesName === null) {
          blockingSeriesName = series.name;
        }
        continue;
      }
      const take = this.round(Math.min(remaining, Math.max(0, quote.maxPayable)));
      if (take <= 0) {
        continue;
      }
      if (series.id === startId) {
        allocated = take;
      } else {
        carryOvers.push({ seriesId: series.id, seriesName: series.name, amount: take });
      }
      remaining = this.round(remaining - take);
    }

    return {
      amountReceived: this.round(amount),
      allocated,
      carryOvers,
      unplaceable: Math.max(0, remaining),
      blockingSeriesName
    };
  }

  /**
   * Message du dépassement du maximum encaissable sur la chaîne.
   *
   * <p>Quand une série de la chaîne existe sans aucune séance, la cause du refus n'est pas le
   * montant mais la série fermée : le message nomme alors l'action corrective — créer ses
   * séances — comme le fait le serveur.</p>
   */
  get exceedsChainKey(): string {
    return this.allocationPreview?.blockingSeriesName
      ? 'payment.dialog.errors.exceedsChainUnopened'
      : 'payment.dialog.errors.exceedsChain';
  }

  /** Paramètres du message de dépassement : maximum encaissable et série à ouvrir. */
  get exceedsChainParams(): { max: number | null; series: string } {
    return {
      max: this.chainMaxPayable,
      series: this.allocationPreview?.blockingSeriesName ?? ''
    };
  }

  /**
   * Prérempli le montant : prix net d'une séance par défaut, reste à payer si
   * « paiement intégral » est coché. Le montant proposé ne dépasse jamais le plafond.
   *
   * @param force écrase le montant même s'il a déjà été saisi manuellement
   */
  private applyAmountPreset(force: boolean): void {
    const amountControl = this.paymentForm.get('amountPaid')!;
    if (!this.quote) {
      return;
    }

    const target = this.isFullSeriesPayment
      ? this.quote.maxPayable
      : Math.min(this.quote.netPricePerSession, this.quote.maxPayable);

    if (force || amountControl.pristine || amountControl.value === null) {
      amountControl.setValue(this.round(target));
    }
  }

  /** Arrondi monétaire à deux décimales. */
  private round(value: number): number {
    return Math.round(value * 100) / 100;
  }

  openConfirmationDialog(paymentData: Payment): void {
    if (!paymentData.sessionSeriesId) {
      this.snackBar.open(
        this.translate.instant('payment.dialog.messages.selectSeries'),
        this.translate.instant('common.cancel'),
        { duration: 4000 }
      );
      return;
    }

    const sessionSeriesId = paymentData.sessionSeriesId;
    const sessionSeries = this.sessionSeries.find(series => series.id === sessionSeriesId);
    const seriesName = sessionSeries?.name || this.translate.instant('payment.dialog.messages.unknownSeries');

    // Le récapitulatif reprend le devis serveur. Il recalculait auparavant le coût à partir
    // du tarif catalogue et du nombre de séances, sans la réduction : il pouvait donc
    // afficher — et accepter — un montant supérieur au dû réel.
    if (!this.quote) {
      this.snackBar.open(
        this.translate.instant('payment.dialog.messages.quoteMissing'),
        this.translate.instant('common.cancel'),
        { duration: 4000 }
      );
      return;
    }

    const quote = this.quote;
    const ceiling = quote.catchUpOnly ? quote.amountDueSoFar : quote.monthTotalCost;

    // Répartition prévue : le dépassement du plafond de la série n'est plus un refus, il devient
    // un report sur les séries suivantes. Seul un reliquat que personne ne peut recevoir bloque,
    // et le serveur refuse alors le versement en totalité.
    const preview = this.buildAllocationPreview(paymentData.amountPaid);
    if (preview && preview.unplaceable > 0) {
      this.showRefusal(this.translate.instant(this.exceedsChainKey, this.exceedsChainParams));
      return;
    }

    // Seule la part imputée sur la série visée entre dans le cumul de cette série ; les parts
    // reportées créditent d'autres séries et ne doivent pas y être ajoutées.
    const allocatedHere = preview ? preview.allocated : paymentData.amountPaid;
    const newTotalPaid = this.round(quote.amountPaid + allocatedHere);

    this.paymentService.getPaymentDetailsForSeries(this.studentId, sessionSeriesId).subscribe({
      next: paymentDetails => {
        const dialogRef = this.dialog.open(PaymentConfirmationDialogComponent, {
          width: '520px',
          maxWidth: '95vw',
          data: {
            seriesName,
            numberOfSessions: quote.catchUpOnly ? quote.attendedSessions : quote.billableSessions,
            excludedSessions: quote.excludedSessions,
            pricePerSession: quote.netPricePerSession,
            grossPricePerSession: quote.grossPricePerSession,
            discountRate: quote.discountRate,
            totalCost: ceiling,
            paymentDetails: paymentDetails || [],
            paymentHistory: [] as Payment[],
            totalPaid: newTotalPaid,
            remainingAmount: this.round(Math.max(0, ceiling - newTotalPaid)),
            isCatchUp: quote.catchUpOnly,
            calculationNote: quote.catchUpOnly
              ? this.translate.instant('payment.dialog.notes.catchUp')
              : '',
            // Récapitulatif de la répartition : montant reçu, part imputée sur la série visée et
            // parts reportées avec leur série destinataire (exigence 9.3).
            amountReceived: this.round(paymentData.amountPaid),
            amountAllocated: this.round(allocatedHere),
            carryOvers: preview ? preview.carryOvers : []
          }
        });

        dialogRef.afterClosed().subscribe(result => {
          if (result) {
            this.submitPayment(paymentData);
          }
        });
      },
      error: (err: Error) => {
        this.snackBar.open(
          err.message || this.translate.instant('payment.dialog.messages.quoteError'),
          this.translate.instant('common.cancel'),
          { duration: 5000 }
        );
      }
    });
  }

  
  onSubmit(): void {
    if (!this.paymentForm.valid) {
      // Le bouton de validation reste actif : sans ce retour, un formulaire invalide ne
      // produisait aucune réaction et laissait croire à un bouton mort.
      this.paymentForm.markAllAsTouched();
      const amountControl = this.paymentForm.get('amountPaid')!;
      // Le dépassement du maximum encaissable a sa propre explication : le message générique
      // « complétez les champs obligatoires » cacherait l'action corrective à effectuer.
      if (amountControl.hasError('max')) {
        this.showRefusal(this.translate.instant(this.exceedsChainKey, this.exceedsChainParams));
        return;
      }
      this.snackBar.open(
        amountControl.hasError('nonPositiveAmount')
          ? this.translate.instant(this.nonPositiveAmountKey)
          : this.translate.instant('payment.dialog.messages.invalidForm'),
        this.translate.instant('common.close'),
        { duration: 5000 }
      );
      return;
    }

    // fullSeriesPayment est un simple assistant de saisie : il ne fait pas partie du
    // contrat d'API et ne doit pas partir vers le backend. On le retire d'une copie plutôt
    // que par déstructuration : la variable écartée serait signalée comme inutilisée.
    const formValue = { ...this.paymentForm.value };
    delete formValue.fullSeriesPayment;
    const paymentData: Payment = {
      ...formValue,
      studentId: this.studentId
    };

    this.openConfirmationDialog(paymentData);
  }

  submitPayment(paymentData: Payment): void {
    if (this.nextCatchUpSessionId) {
      paymentData.sessionId = this.nextCatchUpSessionId;
    }

    const paymentRequest: Observable<PaymentAllocationResult | Payment> =
      paymentData.sessionId && this.nextCatchUpSessionId
        ? this.paymentService.processCatchUpPayment(paymentData)
        : this.paymentService.addPayment(paymentData);

    // Contexte capturé avant l'appel : la génération du reçu s'appuie sur la saisie et le
    // devis courants, que la fermeture du dialogue rendrait indisponibles.
    const receiptContext = this.captureReceiptContext(paymentData);

    paymentRequest.subscribe({
      next: (response) => {
        // Le chemin rattrapage renvoie encore une ligne de paiement, le chemin série renvoie la
        // répartition complète. On ramène les deux à la ligne de paiement créditée, seule
        // source de la date et de l'identifiant imprimés sur le reçu.
        const allocation = this.asAllocationResult(response);
        const payment = allocation ? allocation.payment : (response as Payment);

        // Un versement nul n'encaisse rien : il n'y a aucun justificatif à remettre à
        // l'étudiant, et imprimer un reçu à 0 DA laisserait croire à un paiement.
        const hasReceipt = receiptContext.amountPaid > 0;
        this.snackBar.open(
          this.successMessage(hasReceipt, allocation),
          this.translate.instant('common.close'),
          { duration: allocation && allocation.carryOvers.length > 0 ? 8000 : 3000 }
        );
        if (hasReceipt) {
          this.printReceipt(receiptContext, payment, allocation);
        }
        this.dialogRef.close(response);
      },
      // PaymentService.handleError convertit l'erreur HTTP en Error dont le message porte
      // déjà le motif renvoyé par le serveur. Le code précédent typait l'erreur
      // HttpErrorResponse et lisait err.error.message : toujours undefined sur un Error, si
      // bien que le motif précis du refus était systématiquement remplacé par un message
      // générique — « Une erreur est survenue » au lieu de « montant strictement positif ».
      error: (err: Error) => {
        console.error('Erreur lors du traitement du paiement:', err);
        this.showRefusal(err.message || this.translate.instant('payment.dialog.messages.genericError'));
      }
    });
  }

  /**
   * Distingue la répartition d'un versement de série de la ligne de paiement d'un rattrapage.
   *
   * <p>Les deux chemins de l'API ne renvoient pas le même contrat : {@code /process} renvoie la
   * répartition, {@code /process/catch-up} une ligne de paiement.</p>
   */
  private asAllocationResult(response: PaymentAllocationResult | Payment): PaymentAllocationResult | null {
    const candidate = response as PaymentAllocationResult;
    return candidate && Array.isArray(candidate.carryOvers) ? candidate : null;
  }

  /**
   * Message de succès, enrichi du report lorsqu'une part du versement a changé de série.
   *
   * <p>Un report silencieux serait incompréhensible : l'administrateur verrait la série visée
   * créditée d'un montant inférieur à celui qu'il a encaissé.</p>
   */
  private successMessage(hasReceipt: boolean, allocation: PaymentAllocationResult | null): string {
    if (allocation && allocation.carryOvers.length > 0) {
      const destinations = allocation.carryOvers
        .map(carryOver => `${carryOver.seriesName} (${carryOver.amount.toFixed(2)} DA)`)
        .join(', ');
      return this.translate.instant('payment.dialog.messages.successWithCarryOver', {
        allocated: allocation.amountAllocated.toFixed(2),
        carried: allocation.amountCarriedOver.toFixed(2),
        destinations
      });
    }
    return this.translate.instant(hasReceipt
      ? 'payment.dialog.messages.success'
      : 'payment.dialog.messages.successNoReceipt');
  }

  /**
   * Affiche un motif de refus intégralement, quelle qu'en soit la longueur.
   *
   * <p>Le refus d'un versement non plaçable nomme le maximum encaissable <strong>et</strong>
   * l'action corrective — créer les séances de la série suivante pour l'ouvrir. Tronquer ce
   * message ou l'effacer trop vite ferait disparaître précisément la partie utile : d'où une
   * durée d'affichage allongée et un rendu multiligne.</p>
   */
  private showRefusal(message: string): void {
    this.snackBar.open(message, this.translate.instant('common.close'), {
      duration: 15000,
      panelClass: ['payment-refusal-snackbar']
    });
  }
  

  /**
   * Fige les éléments du reçu à partir de la saisie et du devis, avant l'appel serveur.
   *
   * <p>Le montant retenu est celui saisi, et non celui de la réponse : le backend regroupe
   * tous les versements d'une série sur une même ligne de paiement, dont {@code amountPaid}
   * est le <strong>cumul</strong>. L'imprimer ferait apparaître sur le reçu du jour la somme
   * de tous les versements antérieurs.</p>
   */
  private captureReceiptContext(paymentData: Payment): ReceiptContext {
    const group = this.groups.find(g => g.id === paymentData.groupId);
    const series = this.sessionSeries.find(s => s.id === paymentData.sessionSeriesId);
    const method = this.paymentMethods.find(m => m.value === paymentData.paymentMethod);
    const quote = this.quote;

    return {
      amountPaid: paymentData.amountPaid,
      groupName: group?.name ?? '',
      seriesName: series?.name ?? this.translate.instant('payment.dialog.messages.unknownSeries'),
      paymentMethodLabel: method
        ? this.translate.instant(method.labelKey)
        : (paymentData.paymentMethod ?? ''),
      description: paymentData.paymentDescription,
      isCatchUp: !!quote?.catchUpOnly,
      seriesTotalCost: quote ? (quote.catchUpOnly ? quote.amountDueSoFar : quote.monthTotalCost) : undefined,
      // En rattrapage, la facturation porte sur les séances suivies ; hors rattrapage, sur les
      // séances facturables. Les deux décomptes doivent accompagner le coût qui leur correspond.
      billableSessions: quote ? (quote.catchUpOnly ? quote.attendedSessions : quote.billableSessions) : undefined,
      seriesAlreadyPaid: quote ? quote.amountPaid : undefined
    };
  }

  /**
   * Génère et envoie à l'impression le reçu du versement qui vient d'être encaissé.
   *
   * <p>Un échec de génération ne doit pas faire croire à un échec du paiement : celui-ci est
   * déjà enregistré côté serveur. L'erreur est donc signalée séparément, sans masquer le
   * message de succès.</p>
   */
  private printReceipt(
    context: ReceiptContext,
    response: Payment,
    allocation: PaymentAllocationResult | null
  ): void {
    const issuedAt = response.paymentDate ? new Date(response.paymentDate) : new Date();

    // Montant reçu : celui de la répartition serveur, jamais `response.amountPaid` qui porte le
    // CUMUL de la série — l'imprimer ferait apparaître sur le reçu du jour la somme de tous les
    // versements antérieurs. À défaut de répartition (rattrapage), la saisie fait foi.
    const amountReceived = allocation ? allocation.amountReceived : context.amountPaid;
    // Seule la part imputée réduit la dette de cette série ; les parts reportées en créditent
    // d'autres et ne doivent pas entrer dans son cumul ni dans son reste à payer (exigence 7.3).
    const allocatedHere = allocation ? allocation.amountAllocated : context.amountPaid;
    const seriesPaidAfter = context.seriesAlreadyPaid !== undefined
      ? this.round(context.seriesAlreadyPaid + allocatedHere)
      : undefined;
    const remainingAfter = context.seriesTotalCost !== undefined && seriesPaidAfter !== undefined
      ? this.round(Math.max(0, context.seriesTotalCost - seriesPaidAfter))
      : undefined;

    // La génération est asynchrone depuis l'ajout du logo (chargement de l'image) : l'échec
    // doit être capté sur la promesse, un try/catch synchrone le laisserait passer.
    this.receiptPdfService.generateAndPrint({
        reference: this.receiptPdfService.buildReference(response.id, issuedAt),
        issuedAt,
        studentName: this.studentName,
        groupName: context.groupName,
        seriesName: context.seriesName,
        amountPaid: amountReceived,
        paymentMethodLabel: context.paymentMethodLabel,
        description: context.description,
        isCatchUp: context.isCatchUp,
        seriesTotalCost: context.seriesTotalCost,
        billableSessions: context.billableSessions,
        totalPaidAfter: seriesPaidAfter,
        remainingAfter,
        // Répartition telle que décidée par le serveur : la part imputée et les séries
        // destinataires des reports sont imprimées sur le reçu (exigences 7.2, 7.4, 7.5).
        amountAllocated: allocation ? allocation.amountAllocated : undefined,
        carryOvers: allocation ? allocation.carryOvers : undefined,
        // L'admin connecté est celui qui encaisse ce versement. Le champ d'audit de la ligne
        // de paiement désigne l'admin du premier versement de la série, pas celui-ci.
        adminUsername: this.authService.currentUser?.username
          ?? this.translate.instant('payment.receipt.unknownAdmin')
    }).catch((err: unknown) => {
      console.error('Erreur lors de la génération du reçu:', err);
      this.snackBar.open(
        this.translate.instant('payment.receipt.error'),
        this.translate.instant('common.close'),
        { duration: 5000 }
      );
    });
  }

  onCancel(): void {
    this.dialogRef.close();
  }
}
