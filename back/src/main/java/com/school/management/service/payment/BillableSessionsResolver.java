package com.school.management.service.payment;

import com.school.management.persistance.SessionEntity;

import java.util.List;
import java.util.Set;

/**
 * Séances d'une série réellement facturables à un étudiant.
 *
 * <p>Source unique de la règle du prorata : une séance est facturable si une Fenêtre_Inscription
 * de l'étudiant dans le groupe de la série contient son jour — de l'arrivée au départ inclus,
 * inscription close comprise (spec admin-corrections, D5) — <strong>ou</strong> si l'étudiant y
 * possède une présence active. Cette règle ne vivait que
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
     * @param billable                  séances facturables, dans l'ordre chronologique
     * @param excluded                  séances écartées : hors de toute fenêtre d'inscription et non
     *                                  suivies
     * @param attendedCount             séances facturables où l'étudiant est marqué présent
     * @param enrolled                  l'étudiant est membre du groupe pour cette série : inscription
     *                                  active, ou inscription close dont la fenêtre contient une
     *                                  séance de la série
     * @param withinEnrolmentSessionIds séances de la série que contient une fenêtre d'inscription
     *                                  de l'étudiant : facturables à ce seul titre ; vide si aucune,
     *                                  {@code null} accepté pour vide
     * @param compensatedAwaySessionIds séances écartées parce que déjà facturées dans la série
     *                                  d'origine d'un rattrapage compensatoire
     */
    record BillableSessions(
            List<SessionEntity> billable,
            List<SessionEntity> excluded,
            int attendedCount,
            boolean enrolled,
            Set<Long> withinEnrolmentSessionIds,
            Set<Long> compensatedAwaySessionIds) {

        public BillableSessions {
            withinEnrolmentSessionIds = withinEnrolmentSessionIds == null ? Set.of() : Set.copyOf(withinEnrolmentSessionIds);
            compensatedAwaySessionIds = compensatedAwaySessionIds == null ? Set.of() : Set.copyOf(compensatedAwaySessionIds);
        }

        /**
         * Détail sans séance écartée pour compensation.
         *
         * <p>Existe pour les appelants qui ne mettent pas en jeu le rattrapage — la majorité — afin
         * qu'ils n'aient pas à nommer un ensemble vide. « Aucune séance compensée ailleurs » est le
         * cas normal, pas un cas particulier.</p>
         */
        public BillableSessions(List<SessionEntity> billable,
                               List<SessionEntity> excluded,
                               int attendedCount,
                               boolean enrolled,
                               Set<Long> withinEnrolmentSessionIds) {
            this(billable, excluded, attendedCount, enrolled, withinEnrolmentSessionIds, Set.of());
        }

        /**
         * Vrai si une fenêtre d'inscription contient cette séance : elle est due à ce seul titre,
         * suivie ou non. Faux pour une séance facturée parce que suivie hors fenêtre — avant
         * l'arrivée, après le départ, ou sans inscription — qui demande une explication à l'écran.
         */
        public boolean isWithinEnrolment(Long sessionId) {
            return withinEnrolmentSessionIds.contains(sessionId);
        }

        /** Nombre de séances facturables : c'est le décompte qui remplace {@code total_sessions}. */
        public int billableCount() {
            return billable.size();
        }

        /** Nombre de séances écartées, exposé par le devis et l'historique. */
        public int excludedCount() {
            return excluded.size();
        }

        /**
         * Vrai si cette séance a été écartée parce qu'elle est déjà facturée dans la série d'origine
         * d'un rattrapage compensatoire (exigence 2.3).
         *
         * <p>Permet à l'historique de dire <em>pourquoi</em> une séance n'est pas facturée
         * (exigence 2.9) : « écartée » sans raison ressemble à une erreur de calcul.</p>
         */
        public boolean isCompensatedAway(Long sessionId) {
            return compensatedAwaySessionIds.contains(sessionId);
        }
    }
}
