package com.school.management.service.payment;

import com.school.management.persistance.SessionEntity;

import java.util.Date;
import java.util.List;

/**
 * Séances d'une série réellement facturables à un étudiant.
 *
 * <p>Source unique de la règle du prorata : une séance est facturable si sa date est
 * postérieure ou égale à la date d'inscription de l'étudiant dans le groupe de la série,
 * <strong>ou</strong> si l'étudiant y possède une présence active. Cette règle ne vivait que
 * dans {@code StudentHistoryService}, tandis que le devis plafonnait sur
 * {@code series.total_sessions} : les deux se contredisaient, et l'écart devenait un
 * trop-perçu intégral (exigence 1.5).</p>
 *
 * <p><strong>L'unité de facturation est la série</strong>, décision tranchée avant le
 * démarrage : la méthode prend un identifiant de série, jamais une plage de dates, et aucune
 * agrégation entre groupes sur un mois civil n'est effectuée. Le décompte des présences reste
 * donc borné à la série, comme le faisait déjà
 * {@code AttendanceRepository.countPresentForStudentAndSeries}.</p>
 */
public interface BillableSessionsResolver {

    /**
     * Décompte et détail des séances facturables d'un étudiant pour une série.
     *
     * @param studentId identifiant de l'étudiant
     * @param seriesId  identifiant de la série
     * @return le détail des séances, jamais nul
     */
    BillableSessions resolve(Long studentId, Long seriesId);

    /**
     * Détail des séances d'une série vis-à-vis d'un étudiant.
     *
     * @param billable       séances facturables, dans l'ordre chronologique
     * @param excluded       séances écartées : antérieures à l'inscription et non suivies
     * @param attendedCount  séances facturables où l'étudiant est marqué présent
     * @param enrolled       une inscription active existe dans {@code student_groups}
     * @param enrollmentDate date d'inscription au groupe, nulle si aucune inscription
     */
    record BillableSessions(
            List<SessionEntity> billable,
            List<SessionEntity> excluded,
            int attendedCount,
            boolean enrolled,
            Date enrollmentDate) {

        /** Nombre de séances facturables : c'est le décompte qui remplace {@code total_sessions}. */
        public int billableCount() {
            return billable.size();
        }

        /** Nombre de séances écartées, exposé par le devis et l'historique. */
        public int excludedCount() {
            return excluded.size();
        }
    }
}
