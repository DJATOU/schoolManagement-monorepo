import { CommonModule } from '@angular/common';
import { Component, Input, OnChanges, SimpleChanges } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { GroupChange } from '../../../models/group/group-change';
import { StudentService } from '../../student/services/student.service';
import { resolveLocale } from '../../../shared/locale';

/**
 * Bandeau signalant les changements de groupe d'un étudiant (exigences 10.2, 10.5).
 *
 * <p>Affiché sur la fiche de l'étudiant et sur son formulaire de versement. Un même composant
 * sert les deux emplacements : le libellé du signalement doit dire la même chose des deux
 * côtés, sous peine que l'administrateur lise deux versions du même fait.</p>
 *
 * <h2>Rien à signaler ⇒ rien à l'écran</h2>
 * Le tableau vide est le cas de très loin le plus fréquent : le signalement porte sur un
 * <strong>changement</strong> de groupe, pas sur l'appartenance à plusieurs groupes, qui est la
 * situation normale d'un étudiant suivant plusieurs matières. Aucun bloc vide ni mention
 * « aucun changement » n'est rendu : une alerte qui s'affiche toujours cesse d'être lue.
 *
 * <h2>Purement informatif</h2>
 * Le composant ne porte aucun montant et n'expose aucun état consommé par une validation de
 * formulaire : il ne peut donc ni altérer une facturation ni bloquer l'enregistrement d'un
 * versement (exigences 10.6, 10.7). Un échec de chargement est journalisé en console et laisse
 * la zone vide — une information secondaire indisponible ne doit pas parasiter la consultation
 * d'une fiche ni la saisie d'un versement.
 */
@Component({
  selector: 'app-group-change-notice',
  standalone: true,
  imports: [CommonModule, MatIconModule, TranslateModule],
  templateUrl: './group-change-notice.component.html',
  styleUrls: ['./group-change-notice.component.scss']
})
export class GroupChangeNoticeComponent implements OnChanges {

  /** Étudiant dont les changements de groupe sont signalés. */
  @Input() studentId: number | null | undefined;

  /** Signalements reçus, du plus ancien au plus récent (ordre du serveur, conservé tel quel). */
  changes: GroupChange[] = [];

  constructor(
    private studentService: StudentService,
    private translate: TranslateService
  ) {}

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['studentId']) {
      this.load();
    }
  }

  /**
   * Libellé localisé du mois du signalement (« août 2026 »).
   *
   * <p>Le serveur ne renvoie que l'année et le mois en nombres : il n'a aucune raison de
   * connaître la langue de l'interface. Le libellé est donc composé ici, dans la langue
   * active.</p>
   */
  monthLabel(change: GroupChange): string {
    const date = new Date(change.year, change.month - 1, 1);
    return date.toLocaleDateString(resolveLocale(this.translate.currentLang),
      { month: 'long', year: 'numeric' });
  }

  /**
   * Charge les signalements de l'étudiant.
   *
   * <p>Appel indépendant des autres chargements de l'écran : il n'est attendu par personne et
   * ne retarde donc ni l'affichage de la fiche ni celui du formulaire de versement.</p>
   */
  private load(): void {
    this.changes = [];
    if (this.studentId === null || this.studentId === undefined) {
      return;
    }
    this.studentService.getGroupChanges(this.studentId).subscribe({
      next: changes => {
        this.changes = changes ?? [];
      },
      // Échec silencieux à l'écran : un signalement informatif absent vaut mieux qu'une fiche
      // ou un formulaire de versement rendus inutilisables.
      error: (err: unknown) => {
        console.error('Erreur lors du chargement des signalements de changement de groupe :', err);
        this.changes = [];
      }
    });
  }
}
