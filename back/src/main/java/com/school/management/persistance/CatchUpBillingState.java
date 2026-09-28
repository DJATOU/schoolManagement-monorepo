package com.school.management.persistance;

/**
 * État de facturation d'une présence de rattrapage.
 *
 * <p>Cet état existe parce que l'ancien modèle n'avait que {@code isCatchUp}, qui confondait deux
 * situations sans rapport : un rattrapage <strong>dont la facturation est décidée</strong> et un
 * rattrapage <strong>dont personne n'a rien décidé</strong>. La séance était alors facturée au
 * groupe d'accueil par défaut, silencieusement, y compris lorsque l'étudiant l'avait déjà payée
 * dans son propre groupe.</p>
 *
 * <p>Une valeur nulle désigne une présence ordinaire : cet état ne concerne que les rattrapages.</p>
 */
public enum CatchUpBillingState {

    /**
     * À préciser : l'étudiant appartient à un groupe de même niveau et même matière, donc une place
     * lui est réservée ailleurs, mais la séance manquée et la décision « déjà payée » ne sont pas
     * encore renseignées.
     *
     * <p><strong>Ne facture rien</strong>, et n'écarte rien non plus : la séance n'entre ni dans les
     * séances facturables, ni dans les séances écartées. Une séance écartée est une décision prise
     * (« déjà payée ailleurs ») que l'interface annonce comme telle ; une séance en attente n'est
     * pas décidée. Les confondre ferait réapparaître le défaut d'étiquetage qui présentait une
     * séance à venir comme non facturée.</p>
     */
    PENDING,

    /**
     * Résolu : la séance manquée est désignée et la décision « déjà payée » a été prise
     * explicitement. La facturation reste ancrée à la séance manquée dans son groupe d'origine.
     */
    RESOLVED,

    /**
     * Facturée sur place : l'étudiant n'appartient à aucun groupe de même niveau et même matière,
     * donc aucune place ne lui est réservée nulle part et la séance lui est facturée au groupe
     * d'accueil, comme à un membre.
     *
     * <p>L'absence de séance manquée est ici <strong>voulue</strong>, et non un lien oublié : il n'y
     * a aucune séance à rattraper. C'est la distinction que l'ancien modèle ne pouvait pas
     * exprimer.</p>
     */
    HOST_BILLED
}
