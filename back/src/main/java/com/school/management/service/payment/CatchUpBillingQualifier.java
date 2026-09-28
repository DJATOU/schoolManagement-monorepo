package com.school.management.service.payment;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Qualifie les présences de rattrapage d'un étudiant, pour que la règle « une séance consommée est
 * facturée une fois et une seule » (exigence 2.6) soit appliquée en un seul endroit.
 *
 * <h2>Le problème résolu</h2>
 * Un étudiant qui manque une séance dans son groupe et la rattrape dans un autre risquait de la
 * payer deux fois : la séance manquée reste facturée dans sa série d'origine, où sa place était
 * réservée, et la séance de rattrapage devenait facturable dans la série d'accueil du fait de la
 * présence qui la couvre. Deux séances distinctes, mais une seule unité d'enseignement consommée.
 *
 * <h2>Pourquoi il n'y a pas de récursion</h2>
 * La qualification ne demande <strong>jamais</strong> « la série d'origine facture-t-elle cette
 * séance ? », ce qui exigerait d'évaluer le coût d'une autre série et pourrait boucler. Elle compare
 * deux dates stockées : celle de la séance manquée et celle de l'inscription de l'étudiant dans le
 * groupe de cette séance. Test à un seul niveau, aucune évaluation en cascade.
 *
 * <p>C'est aussi ce qui rend le résultat <strong>indépendant de l'ordre d'évaluation des
 * séries</strong> (exigence 2.6) : la qualification ne dépend d'aucun état de calcul, seulement de
 * données en base. Évaluer la série A puis la série B, ou l'inverse, donne les mêmes
 * qualifications.</p>
 */
public interface CatchUpBillingQualifier {

    /** Nature d'une présence de rattrapage au regard de la facturation. */
    enum Qualification {

        /**
         * La séance manquée est facturée dans sa série d'origine : le rattrapage est donc gratuit
         * côté groupe d'accueil, sans quoi l'étudiant paierait deux fois.
         */
        COMPENSATOIRE,

        /**
         * Aucune autre série ne facture cette séance : le rattrapage est facturable côté accueil.
         * C'est le cas d'une séance consommée avant toute inscription, que
         * {@code business-rules.md} déclare facturable parce qu'elle a bien été suivie.
         */
        CONSOMME
    }

    /**
     * Vue des rattrapages d'un étudiant, indexée dans les deux sens dont le résolveur a besoin.
     *
     * <p>Les deux sens sont nécessaires parce qu'une même série peut être, selon l'étudiant
     * considéré, la série d'accueil d'un rattrapage et la série d'origine d'un autre.</p>
     *
     * @param qualificationsBySessionId   sens accueil : par séance de rattrapage, les qualifications
     *                                    des présences de rattrapage qui la couvrent
     * @param compensatedMissedSessionIds sens origine : séances manquées couvertes par un
     *                                    rattrapage compensatoire, à compter comme suivies dans
     *                                    leur série d'origine (exigence 2.12)
     */
    record CatchUpView(Map<Long, List<Qualification>> qualificationsBySessionId,
                       Set<Long> compensatedMissedSessionIds) {

        /** Vue vide, pour un étudiant sans aucun rattrapage. */
        public static CatchUpView empty() {
            return new CatchUpView(Map.of(), Set.of());
        }

        /**
         * Vrai si la séance n'est couverte que par des rattrapages compensatoires (exigence 2.3).
         *
         * <p>Faux dès qu'un rattrapage consommé la couvre : dans ce cas la séance reste facturable
         * côté accueil (exigence 2.5). Faux également en l'absence de tout rattrapage, la question
         * ne se posant pas.</p>
         */
        public boolean isFullyCompensated(Long sessionId) {
            List<Qualification> qualifications = qualificationsBySessionId.get(sessionId);
            // Formulé en « au moins un compensatoire et aucun consommé » plutôt qu'en allMatch :
            // allMatch sur une liste vide vaut vrai, ce qui aurait rendu gratuite une séance sans
            // aucun rattrapage et imposé une garde supplémentaire impossible à atteindre.
            return qualifications != null
                    && qualifications.contains(Qualification.COMPENSATOIRE)
                    && !qualifications.contains(Qualification.CONSOMME);
        }

        /**
         * Vrai si cette séance a été rattrapée ailleurs par un rattrapage compensatoire, et doit
         * donc compter comme suivie dans sa série d'origine (exigence 2.12).
         */
        public boolean isCompensatedAway(Long sessionId) {
            return compensatedMissedSessionIds.contains(sessionId);
        }
    }

    /**
     * Construit la vue des rattrapages de l'étudiant, en un nombre de requêtes constant.
     *
     * @param studentId identifiant de l'étudiant
     * @return la vue, jamais nulle ; vide si l'étudiant n'a aucun rattrapage actif
     */
    CatchUpView view(Long studentId);
}
