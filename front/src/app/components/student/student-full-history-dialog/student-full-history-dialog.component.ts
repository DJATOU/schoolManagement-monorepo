import { CommonModule } from '@angular/common';
import { Component, Inject, OnInit } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { finalize } from 'rxjs';
import { StudentService } from '../services/student.service';
import { PdfGeneratorService } from '../services/pdf-generator.service';
import { RefundService } from '../../../services/refund.service';
import { RefundReceiptPdfService } from '../../../services/refund-receipt-pdf.service';
import { AuthService } from '../../../services/auth.service';
import { StudentRefund } from '../../../models/refund/refund';
import { AmountPipe } from '../../../pipes/amount.pipe';
import { StudentFullHistoryDTO } from '../domain/StudentFullHistoryDTO';
import { SeriesHistoryDTO } from '../../../models/sessionSerie/SeriesHistoryDTO';
import { SessionHistoryDTO } from '../../../models/session/SessionHistoryDTO';
import {
  countBillableSessions,
  countExcludedSessions,
  isCatchUpBilled,
  isExcludedSession,
  isPendingCatchUp
} from '../../../shared/session-billing';

/**
 * Dialogue d'historique complet de l'étudiant (tâche 18.1).
 *
 * Affiche à l'écran l'historique des paiements, les rattrapages effectués (avec un
 * indicateur de rattrapage), les réductions/exemptions et les remboursements.
 * La récupération des données passe par StudentService (un service par entité) dont
 * la gestion des erreurs HTTP est centralisée (voir StudentService.handleError,
 * même schéma que payment.service.ts).
 */
@Component({
  selector: 'app-student-full-history-dialog',
  standalone: true,
  templateUrl: './student-full-history-dialog.component.html',
  styleUrls: ['./student-full-history-dialog.component.scss'],
  imports: [
    CommonModule,
    MatDialogModule,
    MatButtonModule,
    MatIconModule,
    MatExpansionModule,
    MatProgressSpinnerModule,
    TranslateModule,
    AmountPipe
  ]
})
export class StudentFullHistoryDialogComponent implements OnInit {
  fullHistory: StudentFullHistoryDTO | null = null;
  loading = true;
  errorMessage = '';

  /**
   * Remboursements de l'étudiant (pièce, date, montant, motif), chargés pour un ADMIN seulement :
   * ce sont des pièces de caisse, réservées à ce rôle comme les reçus de versement. Le total par
   * série reste affiché pour tous, il vient de l'historique.
   */
  refunds: StudentRefund[] = [];
  /** La liste des remboursements n'a pas pu être chargée : le dire, plutôt que d'afficher rien. */
  refundsUnavailable = false;
  /** Remboursement dont le reçu est en cours de réimpression : un second clic ne relance rien. */
  reprintingRefundId: number | null = null;

  constructor(
    private studentService: StudentService,
    private pdfGeneratorService: PdfGeneratorService,
    private refundService: RefundService,
    private refundReceiptPdf: RefundReceiptPdfService,
    private authService: AuthService,
    private snackBar: MatSnackBar,
    public dialogRef: MatDialogRef<StudentFullHistoryDialogComponent>,
    private translate: TranslateService,
    @Inject(MAT_DIALOG_DATA) public data: { studentId: number }
  ) {}

  ngOnInit(): void {
    this.loadFullHistory();
    this.loadRefunds();
  }

  /** Vrai pour un ADMIN : lui seul voit le détail des remboursements et peut réimprimer un reçu. */
  get canSeeRefunds(): boolean {
    return this.authService.hasRole('ADMIN');
  }

  private loadRefunds(): void {
    if (!this.canSeeRefunds) {
      return;
    }
    this.refundService.getStudentRefunds(this.data.studentId).subscribe({
      next: refunds => {
        this.refunds = refunds;
        this.refundsUnavailable = false;
      },
      // L'historique reste lisible sans le détail : son total par série suffit à l'essentiel.
      error: () => this.refundsUnavailable = true
    });
  }

  /** Remboursements rattachés à une série, dans l'ordre où le serveur les a datés. */
  refundsOf(series: SeriesHistoryDTO): StudentRefund[] {
    return this.refunds.filter(refund => refund.seriesId === series.seriesId);
  }

  /** Vrai lorsqu'un montant a été rendu sur la série : le versé affiché en est diminué. */
  hasRefund(series: SeriesHistoryDTO): boolean {
    return (series.totalRefunded ?? 0) > 0;
  }

  /**
   * Réimprime le reçu d'un remboursement. Chaque émission est enregistrée par le serveur, qui
   * renvoie un rang : à partir du deuxième, le reçu porte la mention « DUPLICATA ».
   */
  reprintRefund(refund: StudentRefund): void {
    if (this.reprintingRefundId !== null) {
      return;
    }
    this.reprintingRefundId = refund.refundId;
    this.refundService.issueReceipt(refund.refundId)
      .pipe(finalize(() => this.reprintingRefundId = null))
      .subscribe({
        next: receipt => this.refundReceiptPdf.generateAndPrint(receipt).catch((err: unknown) => {
          console.error('Erreur lors de l\'impression du reçu de remboursement :', err);
          this.notify(this.translate.instant('studentHistory.refunds.reprintError'));
        }),
        error: (err: Error) =>
          this.notify(err.message || this.translate.instant('studentHistory.refunds.reprintError'))
      });
  }

  private notify(message: string): void {
    this.snackBar.open(message, this.translate.instant('common.close'), { duration: 6000 });
  }

  private loadFullHistory(): void {
    this.loading = true;
    this.errorMessage = '';
    this.studentService.getStudentFullHistory(this.data.studentId).subscribe({
      next: (history) => {
        this.fullHistory = history;
        this.loading = false;
      },
      error: (error: Error) => {
        this.errorMessage = error.message || this.translate.instant('studentHistory.dialog.error');
        this.loading = false;
      }
    });
  }

  /** Séances actives (les paiements CANCELLED sont exclus de l'affichage). */
  getActiveSessions(series: SeriesHistoryDTO): SessionHistoryDTO[] {
    return (series.sessions || []).filter(s => s.paymentStatus !== 'CANCELLED');
  }

  /** Vrai lorsque toutes les séances de la série sont des rattrapages. */
  isCatchUpSeries(series: SeriesHistoryDTO): boolean {
    const sessions = this.getActiveSessions(series);
    return sessions.length > 0 && sessions.every(s => s.catchUpSession);
  }

  /**
   * Séance écartée du coût au prorata (exigences 11.3, 11.4).
   *
   * <p>L'étudiant n'y était pas présent et elle n'est pas facturée : elle reste affichée,
   * mais ne constitue pas une dette. Le verdict vient du module partagé pour que l'écran,
   * la fenêtre de paiement et le PDF ne divergent pas.</p>
   */
  isExcluded(session: SessionHistoryDTO): boolean {
    return isExcludedSession(session);
  }

  /**
   * Séance facturée parce que suivie en rattrapage (exigence 11.5). Sans cette étiquette,
   * la facturation d'une séance antérieure à l'inscription paraît arbitraire.
   */
  isCatchUpBilled(session: SessionHistoryDTO): boolean {
    return isCatchUpBilled(session);
  }

  /** Nombre de séances facturables retenues dans le coût au prorata (exigence 11.6). */
  billableSessionsCount(series: SeriesHistoryDTO): number {
    return countBillableSessions(series);
  }

  /** Nombre de séances écartées, pour la note « ce n'est pas une dette ». */
  excludedSessionsCount(series: SeriesHistoryDTO): number {
    return countExcludedSessions(series);
  }

  /**
   * Classe CSS d'une ligne, dérivée du seul état de la séance.
   *
   * <p>La coloration croisait auparavant présence et paiement, soit six teintes pour trois
   * informations. La présence a maintenant sa propre colonne, explicite : la couleur ne porte
   * plus que la question monétaire — réglée, à régler, non facturée, exemptée.</p>
   */
  getSessionRowClass(session: SessionHistoryDTO): string {
    switch (this.sessionState(session)) {
      case 'PENDING_DECISION':
        return 'row-pending-decision';
      case 'NOT_BILLED':
        return 'row-not-billed';
      case 'EXEMPTED':
        return 'row-exempted';
      case 'DUE':
        return 'row-due';
      default:
        return 'row-paid';
    }
  }

  /** Texte de justification affiché (uniquement pour les absences). */
  getJustificationText(session: SessionHistoryDTO): string {
    if (session.attendanceStatus === 'ABSENT') {
      return this.translate.instant(session.isJustified ? 'common.yes' : 'common.no');
    }
    return '';
  }

  /** Libellé traduit d'un code de présence renvoyé par le backend. */
  attendanceLabel(session: SessionHistoryDTO): string {
    return this.codeLabel('attendance', session.attendanceStatus);
  }

  /**
   * Mention accompagnant le montant d'une séance.
   *
   * <p>Remplace l'ancienne colonne « Paiement », qui affichait un code de statut à côté d'un
   * montant : deux cellules pour une seule information. La mention qualifie désormais le
   * montant lui-même — « à régler », « non facturée », « exempté » — et disparaît quand la
   * séance est réglée, le vert et le montant suffisant alors à le dire.</p>
   *
   * <p>Une séance écartée n'a pas de statut de paiement : le serveur la renvoie « UNPAID »
   * faute de mieux, ce qui la faisait lire comme un impayé (exigences 11.3, 11.4).</p>
   */
  sessionAmountLabel(session: SessionHistoryDTO): string {
    switch (this.sessionState(session)) {
      // « À préciser » et non « non facturée » : la seconde formule annoncerait une gratuité
      // décidée, alors que la décision reste à prendre.
      case 'PENDING_DECISION':
        return this.translate.instant('studentHistory.pendingDecisionTag');
      case 'NOT_BILLED':
        return this.translate.instant('studentHistory.excludedTag');
      case 'EXEMPTED':
        return this.translate.instant('studentHistory.exemptedTag');
      case 'DUE':
        return this.translate.instant('studentHistory.toSettle');
      default:
        return '';
    }
  }

  /**
   * Montant affiché sur la ligne : ce qu'il reste à régler si la séance est due, sinon ce qui
   * a été versé. Les deux viennent du serveur, aucune soustraction n'est faite ici.
   */
  sessionAmount(session: SessionHistoryDTO): number {
    return this.sessionState(session) === 'DUE'
      ? (session.amountRemaining ?? 0)
      : (session.amountPaid ?? 0);
  }

  /** Libellé traduit du statut de paiement d'une série. */
  seriesStatusLabel(series: SeriesHistoryDTO): string {
    return this.codeLabel('seriesStatus', series.paymentStatus);
  }

  // ------------------------------------------------------------------
  // Carte de série : badge, montants, colonnes utiles
  // ------------------------------------------------------------------

  /**
   * Badge de la carte, déduit des données existantes et d'aucun nouvel indicateur stocké.
   *
   * <p>L'exemption est prioritaire sur le statut de paiement : une série exemptée à 100 % a un
   * coût nul, donc un montant versé nul, ce que le statut du serveur rapporte comme « soldée ».
   * Annoncer « Soldé » laisserait croire à un règlement qui n'a jamais eu lieu.</p>
   *
   * <p>« Exempté » et non « À jour » : « à jour » signifie « pas en retard » partout ailleurs
   * dans l'application, et un statut `EXEMPT` distinct existe déjà sur les cartes étudiant.</p>
   */
  seriesBadge(series: SeriesHistoryDTO): 'EXEMPTED' | 'FULL' | 'UNPAID' | 'PARTIAL' {
    if (series.isExempted === true) {
      return 'EXEMPTED';
    }
    switch (series.paymentStatus) {
      case 'FULL':
        return 'FULL';
      // « Non payé » et non « Partiel » quand rien n'est versé : « partiel » se lisait comme un
      // règlement entamé. Le verdict vient du serveur, qui le compare au versé net.
      case 'UNPAID':
        return 'UNPAID';
      default:
        return 'PARTIAL';
    }
  }

  /** Icône du badge : elle double le libellé, la couleur ne porte pas seule l'information. */
  seriesBadgeIcon(series: SeriesHistoryDTO): string {
    switch (this.seriesBadge(series)) {
      case 'FULL':
        return 'check_circle';
      case 'EXEMPTED':
        return 'volunteer_activism';
      case 'UNPAID':
        return 'error_outline';
      default:
        return 'schedule';
    }
  }

  /** Coût de la série pour cet étudiant : séances facturables × prix net. */
  seriesDue(series: SeriesHistoryDTO): number {
    return series.totalCost ?? 0;
  }

  /**
   * Montant versé sur la série, <strong>net des remboursements</strong> : le serveur déduit ce qui a
   * été rendu (décision du propriétaire produit), et le reste à payer suit.
   */
  seriesPaid(series: SeriesHistoryDTO): number {
    return series.totalAmountPaid ?? 0;
  }

  /**
   * Reste à payer, borné à zéro.
   *
   * <p>Le dépassement n'est pas perdu pour autant : il est déjà porté séparément par
   * `totalOverpaid`, que la carte affiche à part. Un « reste » négatif se lirait comme une
   * dette inversée.</p>
   */
  seriesRemaining(series: SeriesHistoryDTO): number {
    return Math.max(0, this.seriesDue(series) - this.seriesPaid(series));
  }

  /** Nombre de séances déjà réglées, pour le sous-libellé du montant versé. */
  paidSessionsCount(series: SeriesHistoryDTO): number {
    return this.getActiveSessions(series)
      .filter(session => !isExcludedSession(session) && session.paymentStatus === 'PAID')
      .length;
  }

  /** Nombre de séances facturables restant à régler, partiellement ou totalement. */
  unpaidSessionsCount(series: SeriesHistoryDTO): number {
    return this.getActiveSessions(series)
      .filter(session => (session.amountRemaining ?? 0) > 0)
      .length;
  }

  /**
   * Vrai lorsqu'une réduction s'applique : le tarif catalogue est alors affiché barré à côté
   * du prix net, sans quoi le prix réduit paraîtrait arbitraire.
   */
  hasDiscount(series: SeriesHistoryDTO): boolean {
    const gross = series.unitPriceGross;
    const net = series.unitPriceNet;
    return gross != null && net != null && gross > net;
  }

  /**
   * Vrai lorsque le serveur a fourni le prix unitaire.
   *
   * <p>Le test vit ici et non dans le gabarit : la valeur est facultative, donc absente aussi
   * bien en `null` qu'en `undefined`, et le gabarit ne peut pas couvrir les deux d'une seule
   * comparaison stricte.</p>
   */
  hasUnitPrice(series: SeriesHistoryDTO): boolean {
    return series.unitPriceNet != null;
  }

  /**
   * Colonne « Justifiée » : masquée tant que la série ne comporte aucune absence.
   *
   * <p>La justification ne concerne que les absences ; sur une série sans absence, la colonne
   * est vide sur toutes les lignes et n'occupe que de la largeur.</p>
   */
  showJustifiedColumn(series: SeriesHistoryDTO): boolean {
    return this.getActiveSessions(series).some(s => s.attendanceStatus === 'ABSENT');
  }


  // ------------------------------------------------------------------
  // Ligne de séance : un seul état visuel par ligne
  // ------------------------------------------------------------------

  /**
   * État d'une ligne de séance, en trois valeurs exclusives.
   *
   * <p>`EXEMPTED` précède `PAID` : une séance exemptée n'a pas été payée, elle n'était pas
   * due. Les confondre attribuerait à la famille un règlement qu'elle n'a pas fait.</p>
   */
  sessionState(session: SessionHistoryDTO): 'PENDING_DECISION' | 'NOT_BILLED' | 'EXEMPTED' | 'PAID' | 'DUE' {
    // Rattrapage à préciser : ni dette ni gratuité acquise. Il précède tous les autres tests,
    // car aucun d'eux ne s'applique à une séance dont la facturation n'est pas décidée — la
    // classer « non facturée » annoncerait une gratuité, « due » une dette inexistante.
    if (isPendingCatchUp(session)) {
      return 'PENDING_DECISION';
    }
    if (isExcludedSession(session)) {
      return 'NOT_BILLED';
    }
    if (session.isExempted === true) {
      return 'EXEMPTED';
    }
    // `amountRemaining` est le critère : il distingue une séance partiellement couverte d'une
    // séance soldée, ce que le statut seul ne fait pas. Absent — réponse d'une version
    // antérieure du serveur — on retombe sur le statut, sans quoi toute séance impayée
    // passerait pour réglée, exactement l'erreur que cet écran doit éviter.
    if (session.amountRemaining == null) {
      return session.paymentStatus === 'PAID' ? 'PAID' : 'DUE';
    }
    return session.amountRemaining > 0 ? 'DUE' : 'PAID';
  }

  /** Traduit un code ; une valeur inconnue est rendue telle quelle. */
  private codeLabel(group: string, code: string | undefined | null): string {
    if (!code) {
      return '—';
    }
    const key = `studentHistory.${group}.${code}`;
    const translated = this.translate.instant(key);
    return translated === key ? code : translated;
  }

  /** Génère le PDF de l'historique complet (réutilise le service PDF existant). */
  generatePdf(): void {
    if (this.fullHistory) {
      // Les remboursements déjà chargés passent au PDF : un administrateur imprime le même détail
      // que celui qu'il lit à l'écran.
      this.pdfGeneratorService.generateFullHistoryPdf(
        this.fullHistory, 'assets/succes_assistance.png', this.refunds);
    }
  }
}
