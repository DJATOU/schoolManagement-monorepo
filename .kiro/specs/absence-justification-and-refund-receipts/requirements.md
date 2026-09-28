# Requirements Document

## Introduction

Cette fonctionnalité traite quatre questions liées, posées à partir d'un cas concret : « un
Étudiant est ajouté à une séance de rattrapage dans un autre Groupe ; que lui marque le
Système ? la justification est-elle modifiable ? le remboursement et son reçu existent-ils ? »

**Ce que le Système fait déjà, et qu'il ne faut pas re-spécifier.** La complétion d'une demande
de rattrapage crée une nouvelle Présence marquée présente, marquée rattrapage, rattachée au
Groupe et à la Séance de rattrapage, et référençant la Séance manquée. La Présence d'origine
n'est pas modifiée : elle reste une absence. La traçabilité « rattrapé » existe donc déjà en
base, mais elle n'est pas exploitée.

**Trois défauts avérés motivent cette fonctionnalité.**

1. **La Présence de rattrapage n'est rattachée à aucune Série.** La complétion ne renseigne
   jamais `attendance.session_series_id`. Or le résolveur de Séances facturables et le devis
   lisent les Présences par Série. Une Présence de rattrapage échappe donc à ces deux lectures :
   elle n'entre ni dans le décompte des Séances suivies, ni dans la détection du mode
   « rattrapage seul ». Ce n'est pas un choix documenté, c'est une incohérence.
2. **La justification n'est pas modifiable après saisie.** `PUT /api/attendances/{id}` n'accepte
   aucun corps de requête et `AttendanceService.updateAttendance` recharge puis ré-enregistre la
   Présence sans rien changer : l'appel réussit et ne modifie rien. Le seul chemin d'écriture de
   l'Indicateur_Justification est la saisie initiale en masse. Une erreur de saisie est donc
   définitive, et aucune trace n'existerait de qui l'aurait corrigée.
3. **Le remboursement laisse sortir de l'argent sans motif, sans pièce et sans plafond
   cumulé.** L'entité, le service, le contrôleur, la déduction dans les recettes et l'affichage
   dans l'historique existent. Manquent : le motif du remboursement, le numéro de pièce, le reçu
   remettable à un parent, et toute interface d'appel — l'API existe, rien ne l'appelle. Enfin,
   le contrôle de montant compare la demande au seul montant versé du Paiement, sans déduire les
   remboursements déjà accordés sur ce Paiement : deux remboursements du montant total d'un même
   versement sont tous deux acceptés. Un montant nul est également accepté, ce qui produirait une
   pièce sans objet.

**Une croyance à corriger explicitement.** La demande initiale supposait que « si l'absence est
justifiée, alors la séance n'est pas facturée ». Ce n'est pas le comportement du Système, et ce
n'est pas non plus le comportement retenu. L'Indicateur_Justification n'apparaît nulle part dans
le calcul des montants : il n'est lu que par l'affichage et les statistiques du tableau de bord.
Le Système distingue deux quantités qui répondent à deux questions différentes :

- le **Montant_Dû_À_Ce_Jour** (seuil de retard) ne compte que les Séances effectivement suivies.
  Une absence, justifiée ou non, ne l'augmente donc **jamais** : une absence ne met pas un
  Étudiant en retard ;
- le **Coût_Série_Prorata** (ce que la Série coûte) compte toutes les Séances postérieures à
  l'inscription. Une absence y **reste comptée** : la place était réservée.

L'arbitrage du propriétaire produit confirme cet état de fait et le formalise : la justification
reste **documentaire**, sans effet financier (voir « Décisions tranchées »). Le périmètre inclut
donc l'obligation de le **dire à l'écran**, pour qu'un administrateur ne promette pas à une
famille une exonération que le Système n'applique pas.

**Périmètre.** Rattachement de la Présence de rattrapage à sa Série ; mention « Rattrapée » sur
la Séance manquée ; neutralité financière du rattrapage dans le Groupe d'accueil (une Séance
consommée est facturée une fois) ; neutralité financière et lisibilité de la justification ;
modification de la justification par un point d'entrée dédié réservé à l'Administrateur, avec
piste d'audit ; motif et numéro de pièce du remboursement ; plafond de remboursement cumulé par
Paiement ; reçu de remboursement remettable ; interface d'enregistrement du remboursement.

## Glossary

- **Système** : l'application de gestion scolaire dans son ensemble (backend Spring Boot +
  frontend Angular), sauf lorsqu'un composant plus précis est nommé.
- **Étudiant** : un `StudentEntity`.
- **Administrateur** : un utilisateur porteur du rôle `ADMIN`, seul autorisé aux écritures.
- **Consultant** : un utilisateur porteur du rôle `VIEWER`, autorisé aux seules lectures.
- **Groupe** : un `GroupEntity`, porteur du Prix_Séance.
- **Série** : un `SessionSeriesEntity` ; unité de facturation d'un Groupe.
- **Séance** : un `SessionEntity` rattaché à une Série et daté.
- **Présence** : un `AttendanceEntity` rattachant un Étudiant à une Séance.
- **Présence_Active** : une Présence dont l'indicateur `active` vaut vrai.
- **Absence** : une Présence_Active dont l'indicateur `status` (`isPresent`) vaut faux.
- **Présence_Rattrapage** : une Présence_Active dont l'indicateur `is_catch_up` vaut vrai.
- **Séance_Manquée** : la Séance référencée par `attendance.missed_session_id` d'une
  Présence_Rattrapage.
- **Série_Accueil** : la Série de la Séance sur laquelle porte une Présence_Rattrapage.
- **Série_Origine** : la Série de la Séance_Manquée.
- **Date_Inscription** : la date d'entrée de l'Étudiant dans un Groupe
  (`student_groups.date_assigned`).
- **Rattrapage_Compensatoire** : une Présence_Rattrapage dont la Séance_Manquée est renseignée et
  dont la Séance_Manquée est postérieure ou égale à la Date_Inscription de l'Étudiant dans le
  Groupe de la Séance_Manquée. La Séance_Manquée est alors facturée dans sa Série_Origine.
- **Rattrapage_Consommé** : une Présence_Rattrapage qui n'est pas un Rattrapage_Compensatoire.
  Aucune autre Série ne facture la Séance à ce titre.
- **Indicateur_Justification** : le champ `attendance.is_justified` d'une Absence.
- **Commentaire_Justification** : texte libre accompagnant une modification de
  l'Indicateur_Justification, d'au plus 500 caractères.
- **Entrée_Audit** : enregistrement immuable d'une modification de l'Indicateur_Justification.
- **Rang_Séquence_Audit** : entier croissant porté par une Entrée_Audit, ordonnant deux
  Entrées_Audit de même horodatage.
- **Séance_Facturable** : une Séance retenue dans le coût d'une Série pour un Étudiant donné,
  selon les règles de la fonctionnalité `prorata-billing-and-payment-carry-over`.
- **Séance_Exclue** : une Séance écartée du coût d'une Série pour un Étudiant donné.
- **Prix_Séance** : le prix unitaire d'une Séance du Groupe.
- **Coût_Série_Prorata** : nombre de Séances_Facturables × Prix_Séance × (1 − réduction).
- **Montant_Dû_À_Ce_Jour** : nombre de Séances_Facturables suivies × Prix_Séance ×
  (1 − réduction) ; seuil de retard.
- **Plafond_Encaissable** : montant maximum imputable sur une Série, exposé par le Service_Devis.
- **Paiement** : un `PaymentEntity`, rattaché à un Étudiant et généralement à une Série.
- **Montant_Versé** : le champ `payments.amount_paid` d'un Paiement.
- **Remboursement** : un `RefundEntity` rattaché à un Paiement.
- **Remboursement_Actif** : un Remboursement dont l'indicateur `active` vaut vrai.
- **Motif_Remboursement** : texte non vide, d'au plus 500 caractères, justifiant un
  Remboursement.
- **Numéro_Pièce** : identifiant lisible, unique et immuable d'un Remboursement, de la forme
  `REMB-AAAA-NNNN`.
- **Plafond_Remboursable** : Montant_Versé du Paiement diminué de la somme des
  Remboursements_Actifs déjà enregistrés sur ce Paiement.
- **Reçu_Remboursement** : document imprimable attestant la remise d'un Remboursement.
- **Résolveur_Facturable** : le composant `BillableSessionsResolver`.
- **Calculateur_Coût** : le composant `PaymentCostCalculator`.
- **Service_Devis** : le composant `PaymentQuoteService`.
- **Service_Rattrapage** : le composant `CatchUpService`.
- **Service_Justification** : le composant chargé de modifier l'Indicateur_Justification.
- **Service_Remboursement** : le composant `RefundService`.
- **Service_Reçu_Remboursement** : le composant chargé de produire le Reçu_Remboursement.
- **Service_Historique** : le composant `StudentHistoryService`.
- **ÉCHELLE_MONÉTAIRE** : échelle de 2 décimales.
- **ARRONDI_MONÉTAIRE** : mode d'arrondi `HALF_UP`.

## Requirements

### Requirement 1: Rattachement et traçabilité de la Présence de rattrapage

**User Story:** En tant qu'administrateur, je veux qu'une séance rattrapée dans un autre groupe
soit lisible depuis la séance manquée comme depuis la séance de rattrapage, afin d'expliquer à
une famille ce que le Système a enregistré.

#### Acceptance Criteria

1. QUAND le Service_Rattrapage complète une demande de rattrapage, LE Service_Rattrapage DOIT
   renseigner sur la Présence_Rattrapage créée une Série égale à la Série de la Séance de
   rattrapage ainsi que la référence de la Séance_Manquée de la demande, dans la même transaction
   que le passage de la demande à l'état complété.
2. LE Système DOIT maintenir, pour toute Présence_Active, une Série égale à la Série de sa Séance,
   à la création de la Présence comme après toute modification ultérieure de celle-ci.
3. QUAND le Service_Rattrapage complète une demande de rattrapage, LE Service_Rattrapage DOIT
   conserver la Présence d'origine de la Séance_Manquée inchangée : marquée absente, active, et
   son Indicateur_Justification à sa valeur antérieure.
4. QUAND l'historique d'un Étudiant est consulté, LE Service_Historique DOIT présenter chaque
   Séance_Manquée couverte par une Présence_Rattrapage de cet Étudiant avec la mention
   « Rattrapée », la date de la Séance de rattrapage et le nom du Groupe de cette Séance de
   rattrapage, tout en continuant à présenter la Présence d'origine comme Absence.
5. QUAND l'historique d'un Étudiant est consulté, LE Service_Historique DOIT présenter chaque
   Présence_Rattrapage de cet Étudiant comme rattrapage et nommer la Séance_Manquée
   correspondante par sa date et le nom de son Groupe.
6. *(Retiré.)* Ce critère décrivait une reprise de données rattachant les Présences_Rattrapage
   existantes à leur Série. Il n'y a aucune donnée à reprendre. Le critère 1.1 garantit le
   rattachement des Présences à venir, qui est le seul enjeu. Voir « Non-objectifs ».
7. SI la Séance de rattrapage d'une demande à compléter n'est rattachée à aucune Série, ALORS LE
   Service_Rattrapage DOIT rejeter la complétion avec une erreur de validation nommant la Séance
   concernée, laisser la demande dans son état antérieur et créer aucune Présence.
8. SI la Série de la Séance d'une Présence à créer ne peut être déterminée, ALORS LE Système DOIT
   rejeter la création de cette Présence avec une erreur de validation nommant la Séance
   concernée et n'enregistrer aucune Présence, aucune Présence ne pouvant exister sans Série en
   attente d'un rattachement ultérieur.
9. SI la Séance_Manquée d'une demande à compléter est déjà couverte par une Présence_Rattrapage
   du même Étudiant, ALORS LE Service_Rattrapage DOIT rejeter la complétion avec une erreur de
   validation indiquant qu'un rattrapage est déjà enregistré pour cette Séance_Manquée, laisser
   la demande dans son état antérieur et créer aucune Présence, LE Système maintenant au plus une
   Présence_Rattrapage par couple (Étudiant, Séance_Manquée).
10. SI la Séance_Manquée d'une Présence_Rattrapage n'est pas déterminable, ALORS LE
    Service_Historique DOIT présenter cette Présence_Rattrapage comme rattrapage, indiquer que sa
    Séance_Manquée n'est pas déterminée, et restituer l'historique complet de l'Étudiant sans
    erreur.
11. *(Retiré.)* Ce critère décrivait le compte rendu d'une reprise partielle. Il n'y a aucune
    donnée à reprendre. Voir « Non-objectifs ».

### Requirement 2: Une Séance consommée est facturée une fois

**User Story:** En tant qu'administrateur, je veux qu'un étudiant qui rattrape une séance dans un
autre groupe ne la paie pas deux fois, afin que le montant annoncé à la famille corresponde au
service rendu.

Sans cette exigence, le rattachement demandé par l'exigence 1.1 produirait une double
facturation : la Séance_Manquée reste facturée dans sa Série_Origine (elle est postérieure à
l'inscription) et la Séance de rattrapage deviendrait facturable dans la Série_Accueil du fait de
la Présence_Active qui la couvre.

#### Acceptance Criteria

1. LE Système DOIT qualifier chaque Présence_Rattrapage de Rattrapage_Compensatoire ou de
   Rattrapage_Consommé selon les définitions du Glossaire, en déterminant cette qualification à
   chaque évaluation d'un coût, d'un devis ou de l'historique à partir de l'état courant de la
   Séance_Manquée et de l'inscription de l'Étudiant, et indépendamment de la réduction applicable
   à la Série_Origine, du Montant_Versé sur la Série_Origine et du statut de paiement de cette
   Série_Origine.
2. LE Système DOIT retenir la Séance_Manquée d'un Rattrapage_Compensatoire dans les
   Séances_Facturables de sa Série_Origine, y compris lorsque la réduction applicable rend le
   Coût_Série_Prorata de cette Série_Origine nul et lorsque le Montant_Versé de cette
   Série_Origine couvre déjà son Coût_Série_Prorata.
3. LE Résolveur_Facturable DOIT classer en Séance_Exclue la Séance de la Série_Accueil dont
   toutes les Présences_Actives de l'Étudiant sont des Rattrapages_Compensatoires.
4. LE Résolveur_Facturable DOIT écarter du décompte des Séances suivies de la Série_Accueil
   chaque Séance dont toutes les Présences_Actives de l'Étudiant sont des
   Rattrapages_Compensatoires.
5. LE Résolveur_Facturable DOIT retenir dans les Séances_Facturables de la Série_Accueil la
   Séance dont au moins une Présence_Active de l'Étudiant est un Rattrapage_Consommé.
6. LE Système DOIT retenir chaque Séance suivie par un Étudiant comme Séance_Facturable dans une
   Série au plus, et désigner cette Série indépendamment de l'ordre dans lequel les Séries de
   l'Étudiant sont évaluées.
7. SI la Séance_Manquée d'une Présence_Rattrapage n'est pas renseignée, si la Séance référencée
   ou sa Série n'existe plus, ou si l'Étudiant n'a aucune inscription active dans le Groupe de
   cette Séance_Manquée, ALORS LE Système DOIT traiter la Présence_Rattrapage comme un
   Rattrapage_Consommé.
8. LÀ OÙ un Étudiant a au moins une Présence_Active sur une Série et où toutes ses
   Présences_Actives sur cette Série sont des Rattrapages_Compensatoires, LE Service_Devis DOIT
   retourner un Coût_Série_Prorata, un Montant_Dû_À_Ce_Jour et un Plafond_Encaissable égaux à
   zéro.
9. QUAND l'historique présente une Séance de la Série_Accueil classée Séance_Exclue au titre du
   critère 2.3, LE Service_Historique DOIT présenter cette Séance comme non facturée, indiquer
   qu'elle est déjà facturée dans la Série_Origine, nommer cette Série_Origine et son Groupe, et
   donner la date de la Séance_Manquée.
10. LE Système DOIT exprimer les montants issus de cette exigence en `BigDecimal` avec
    l'ÉCHELLE_MONÉTAIRE et l'ARRONDI_MONÉTAIRE.
11. LÀ OÙ une Séance de la Série_Accueil porte pour un Étudiant au moins une Présence_Active qui
    n'est pas un Rattrapage_Compensatoire, LE Résolveur_Facturable DOIT retenir cette Séance dans
    les Séances_Facturables de la Série_Accueil, la présence simultanée d'un
    Rattrapage_Compensatoire sur cette même Séance ne l'en écartant pas.
12. LE Résolveur_Facturable DOIT compter la Séance_Manquée d'un Rattrapage_Compensatoire dans le
    décompte des Séances suivies de sa Série_Origine, sans modifier la Présence d'origine, qui
    reste marquée absente.
13. LÀ OÙ le Plafond_Encaissable d'une Série est nul par application du critère 2.8 et où le
    Montant_Versé de cette Série est strictement positif, LE Service_Devis DOIT exposer ce
    Montant_Versé comme excédent existant, laisser le Montant_Versé de la Série inchangé et
    n'imputer aucun montant sur une autre Série en dehors de l'enregistrement d'un nouveau
    versement.

### Requirement 3: La justification est documentaire et sans effet financier

**User Story:** En tant qu'administrateur, je veux savoir sans ambiguïté ce que change une
justification, afin de ne pas promettre à une famille une exonération que le Système n'applique
pas.

#### Acceptance Criteria

1. LE Calculateur_Coût DOIT produire, pour deux jeux de données d'une même Série ne différant que
   par la valeur de l'Indicateur_Justification de ses Absences, un Montant_Dû_À_Ce_Jour identique
   à l'ÉCHELLE_MONÉTAIRE.
2. LE Calculateur_Coût DOIT produire, pour deux jeux de données d'une même Série ne différant que
   par la valeur de l'Indicateur_Justification de ses Absences, un Coût_Série_Prorata identique à
   l'ÉCHELLE_MONÉTAIRE.
3. QUAND l'Indicateur_Justification d'une Absence est modifié, LE Système DOIT produire pour la
   Série concernée et pour toute autre Série de l'Étudiant un Coût_Série_Prorata, un
   Montant_Dû_À_Ce_Jour et un Plafond_Encaissable égaux, à l'ÉCHELLE_MONÉTAIRE, à ceux calculés
   avant la modification, et un statut de paiement identique.
4. QUAND l'historique d'assiduité présente une Absence, LE Service_Historique DOIT porter sur la
   ligne de cette Absence, sans exiger d'action de l'utilisateur et non en seule légende de
   l'écran, une mention indiquant que la Séance reste retenue dans le Coût_Série_Prorata et que
   la valeur de l'Indicateur_Justification ne modifie pas ce montant.
5. QUAND l'historique d'assiduité présente une Absence, LE Service_Historique DOIT porter sur la
   ligne de cette Absence, sans exiger d'action de l'utilisateur et non en seule légende de
   l'écran, une mention indiquant que la Séance n'entre pas dans le Montant_Dû_À_Ce_Jour.
6. QUAND un écran présente au moins une Absence, LE Système DOIT y afficher au moins une fois une
   mention indiquant que l'Indicateur_Justification sert au suivi disciplinaire et au droit au
   rattrapage et qu'il n'entre dans aucun calcul de montant.
7. QUAND les statistiques d'assiduité du tableau de bord sont produites, LE Système DOIT exposer
   le nombre d'Absences justifiées et le nombre d'Absences injustifiées comme deux valeurs
   distinctes, dont la somme égale le nombre d'Absences de la période présentée.
8. QUAND l'historique d'assiduité présente une Absence, LE Système DOIT distinguer une Absence
   justifiée d'une Absence injustifiée par un libellé textuel propre à chaque valeur, la
   distinction ne reposant pas uniquement sur une différence de couleur.
9. SI l'Indicateur_Justification d'une Absence n'est pas renseigné, ALORS LE Système DOIT
   présenter cette Absence avec un libellé indiquant que la justification n'est pas renseignée et
   la compter parmi les Absences injustifiées des statistiques du tableau de bord.
10. QUAND l'action de modification de l'Indicateur_Justification d'une Absence est ouverte, LE
    Système DOIT afficher une mention indiquant que la modification ne change ni le
    Coût_Série_Prorata, ni le Montant_Dû_À_Ce_Jour, ni le statut de paiement de la Série
    concernée.

### Requirement 4: Modification de la justification d'une absence

**User Story:** En tant qu'administrateur, je veux corriger la justification d'une absence après
la saisie, afin qu'une erreur de feuille de présence ou un justificatif remis en retard ne reste
pas définitif.

#### Acceptance Criteria

1. LE Système DOIT exposer un point d'entrée dédié à la modification de
   l'Indicateur_Justification, acceptant exactement l'identifiant d'une Présence, la valeur
   booléenne demandée de l'Indicateur_Justification et un Commentaire_Justification facultatif, à
   l'exclusion de toute autre donnée modifiable de la Présence.
2. QUAND ce point d'entrée est appelé avec une valeur différente de la valeur courante, LE
   Service_Justification DOIT appliquer la nouvelle valeur à l'Indicateur_Justification de la
   Présence, laisser inchangés tous les autres champs de la Présence — l'Étudiant, la Séance, la
   Série, le statut de présence, l'indicateur de rattrapage, la Séance_Manquée, le droit au
   rattrapage et l'indicateur `active` — et restituer à l'appelant l'identifiant de la Présence
   et la valeur appliquée.
3. QUAND ce point d'entrée est appelé avec une valeur identique à la valeur courante, LE
   Service_Justification DOIT laisser la Présence inchangée, créer aucune Entrée_Audit et
   retourner une réponse de succès portant cette valeur courante.
4. SI l'appelant de ce point d'entrée n'est pas un Administrateur, ALORS LE Système DOIT refuser
   l'appel par une erreur d'autorisation dont le message indique que le rôle Administrateur est
   requis pour modifier la justification d'une Absence, sans modifier aucune donnée et sans créer
   d'Entrée_Audit.
5. SI la Présence visée est introuvable, ALORS LE Service_Justification DOIT retourner une erreur
   « ressource absente » sans modifier aucune donnée et sans créer d'Entrée_Audit.
6. SI la Présence visée est marquée présente, ALORS LE Service_Justification DOIT retourner une
   erreur de validation dont le message indique que l'Indicateur_Justification ne s'applique qu'à
   une Absence, sans modifier aucune donnée et sans créer d'Entrée_Audit.
7. SI la valeur demandée de l'Indicateur_Justification est absente du corps de la requête ou
   n'est pas une valeur booléenne, ALORS LE Service_Justification DOIT retourner une erreur de
   validation sans modifier aucune donnée et sans créer d'Entrée_Audit.
8. SI le Commentaire_Justification comporte plus de 500 caractères, ALORS LE
   Service_Justification DOIT retourner une erreur de validation indiquant la longueur maximale
   autorisée, sans modifier aucune donnée et sans créer d'Entrée_Audit.
9. SI le point d'entrée de mise à jour de Présence dépourvu de corps de requête est appelé, ALORS
   LE Système DOIT répondre que la ressource n'existe pas, ce point d'entrée étant retiré, et
   modifier aucune donnée.
10. QUAND l'écran d'historique d'assiduité d'un Étudiant est consulté par un Administrateur, LE
    Système DOIT proposer sur chaque Absence une action de modification de la justification
    affichant la valeur courante de l'Indicateur_Justification et un champ de
    Commentaire_Justification limité à 500 caractères.
11. LÀ OÙ l'utilisateur courant est un Consultant, LE Système DOIT masquer l'action de
    modification de la justification.
12. SI la Présence visée n'est pas une Présence_Active, ALORS LE Service_Justification DOIT
    retourner une erreur de validation dont le message indique que la Présence est désactivée,
    sans modifier aucune donnée et sans créer d'Entrée_Audit.
13. SI l'année scolaire de la Séance de la Présence visée n'est pas l'année scolaire courante,
    ALORS LE Service_Justification DOIT refuser la modification par une erreur indiquant que
    l'année scolaire est close, sans modifier aucune donnée et sans créer d'Entrée_Audit.
14. LÀ OÙ l'année scolaire de la Séance de la Présence visée est l'année scolaire courante, LE
    Système DOIT accepter la modification de l'Indicateur_Justification quelle que soit
    l'ancienneté de la Séance, aucune autre borne temporelle ne limitant la correction.

### Requirement 5: Piste d'audit de la justification

**User Story:** En tant qu'administrateur, je veux savoir qui a changé la justification d'une
absence, quand et pourquoi, afin de pouvoir répondre à une contestation d'un parent.

#### Acceptance Criteria

1. QUAND le Service_Justification modifie l'Indicateur_Justification d'une Présence, LE Système
   DOIT enregistrer une Entrée_Audit portant l'identifiant de la Présence, la valeur antérieure,
   la valeur appliquée, l'identifiant de l'utilisateur auteur, l'horodatage de la modification à
   la milliseconde, un Rang_Séquence_Audit permettant d'ordonner deux Entrées_Audit de même
   horodatage, et le Commentaire_Justification fourni ou, à défaut, une mention explicite
   d'absence de commentaire.
2. LE Système DOIT déterminer l'identifiant de l'utilisateur auteur depuis le contexte de
   sécurité, et retenir l'identifiant de repli `system` en l'absence d'utilisateur authentifié.
3. LE Système DOIT conserver toutes les Entrées_Audit d'une Présence sans limite de nombre, et
   n'exposer aucun point d'entrée de modification ni de suppression d'une Entrée_Audit
   enregistrée.
4. LE Système DOIT enregistrer la modification de la Présence et son Entrée_Audit dans une seule
   transaction.
5. SI l'enregistrement de l'Entrée_Audit échoue pour une cause transitoire, à savoir
   indisponibilité temporaire de la base, conflit de verrou ou expiration de transaction, ALORS
   LE Système DOIT annuler la modification de l'Indicateur_Justification, puis rejouer
   l'opération complète au plus 3 fois, chaque rejeu étant engagé au plus 1 seconde après l'échec
   précédent et la durée totale de l'opération n'excédant pas 5 secondes.
6. SI les 3 rejeux prévus au critère 5.5 échouent, ALORS LE Système DOIT laisser la Présence
   inchangée, ne conserver aucune Entrée_Audit issue des tentatives, et retourner à l'appelant
   une erreur nommant l'échec d'enregistrement de la piste d'audit.
7. QUAND la piste d'audit d'une Présence est consultée, LE Système DOIT restituer ses
   Entrées_Audit par horodatage décroissant puis, à horodatage égal, par Rang_Séquence_Audit
   décroissant, et restituer une collection vide lorsque la Présence ne porte aucune
   Entrée_Audit.
8. LE Système DOIT produire une valeur courante de l'Indicateur_Justification égale à la valeur
   appliquée par l'Entrée_Audit de la Présence d'horodatage le plus récent, et de
   Rang_Séquence_Audit le plus élevé à horodatage égal, lorsqu'au moins une Entrée_Audit existe.
9. QUAND l'historique d'assiduité présente une Absence portant au moins une Entrée_Audit, LE
   Système DOIT indiquer l'identifiant de l'utilisateur auteur, l'horodatage et le
   Commentaire_Justification de la modification la plus récente, la mention d'absence de
   commentaire étant affichée lorsque aucun commentaire n'a été fourni.
10. SI l'enregistrement de l'Entrée_Audit échoue pour une cause permanente, à savoir violation
    d'une contrainte ou donnée d'Entrée_Audit invalide, ALORS LE Système DOIT annuler la
    modification de l'Indicateur_Justification, n'engager aucun rejeu et retourner à l'appelant
    une erreur nommant l'échec d'enregistrement de la piste d'audit, un rejeu identique ne
    pouvant que reproduire le même échec.
11. QUAND une Présence est désactivée ou supprimée, LE Système DOIT conserver ses Entrées_Audit
    inchangées et les restituer sur consultation de la piste d'audit de l'identifiant de cette
    Présence.
12. QUAND deux modifications de l'Indicateur_Justification d'une même Présence sont demandées
    concurremment, LE Système DOIT les appliquer l'une après l'autre et enregistrer pour chacune
    une Entrée_Audit dont la valeur antérieure égale la valeur courante laissée par la
    modification précédente.

### Requirement 6: Motif et numéro de pièce du remboursement

**User Story:** En tant qu'administrateur, je veux qu'aucune sortie d'argent ne soit enregistrée
sans motif ni numéro, afin de pouvoir justifier ma caisse lors d'un contrôle.

#### Acceptance Criteria

1. LE Service_Remboursement DOIT exiger un Motif_Remboursement à la création d'un Remboursement.
2. SI le Motif_Remboursement est absent, vide ou composé uniquement d'espaces, ALORS LE
   Service_Remboursement DOIT retourner une erreur de validation sans créer de Remboursement.
3. SI le Motif_Remboursement dépasse 500 caractères, ALORS LE Service_Remboursement DOIT
   retourner une erreur de validation sans créer de Remboursement.
4. QUAND un Remboursement est créé, LE Système DOIT lui attribuer un Numéro_Pièce et enregistrer
   ce Numéro_Pièce dans la transaction même qui enregistre le Remboursement, aucun Remboursement
   ne pouvant être enregistré sans Numéro_Pièce.
5. LE Système DOIT composer le Numéro_Pièce de la forme `REMB-AAAA-NNNN`, où `AAAA` est l'année
   civile de la date du Remboursement écrite sur quatre chiffres et `NNNN` le rang du
   Remboursement au sein de cette année, complété à gauche par des zéros pour atteindre quatre
   chiffres jusqu'au rang 9999 et écrit sans troncature au-delà, la suite des rangs d'une année
   pouvant présenter des rangs manquants qu'aucun Remboursement ultérieur ne comble ni ne
   réutilise.
6. LE Système DOIT garantir par une contrainte d'unicité au stockage, et non par le seul calcul
   applicatif, que deux Remboursements distincts ne portent jamais le même Numéro_Pièce, quel que
   soit le nombre de créations concurrentes et sans dépendre du nombre d'instances de
   l'application en exécution.
7. LE Système DOIT conserver le Numéro_Pièce d'un Remboursement inchangé après sa création.
8. QUAND un Remboursement est créé, LE Système DOIT restituer à l'appelant le Numéro_Pièce, le
   Motif_Remboursement, le montant et la date du Remboursement.
9. QUAND l'historique d'un Étudiant présente un Remboursement, LE Système DOIT afficher son
   Numéro_Pièce et son Motif_Remboursement.
10. *(Retiré.)* Ce critère décrivait l'affichage d'un Remboursement enregistré avant cette
    fonctionnalité. Aucun n'existe : la table est vide, y compris sur le poste de développement, et
    les données actuelles sont des données de test destinées à être supprimées. Voir
    « Non-objectifs ».
11. LE Système DOIT réserver la création d'un Remboursement au rôle Administrateur et répondre au
    Consultant par un refus d'autorisation dont le message indique que le rôle Administrateur est
    requis pour enregistrer un Remboursement.
12. QUAND un Numéro_Pièce est attribué à un Remboursement, LE Système DOIT retenir un rang
    strictement supérieur au plus grand rang déjà attribué pour l'année civile de la date de ce
    Remboursement, et le rang 1 lorsqu'aucun Numéro_Pièce ne porte encore cette année civile.
13. *(Retiré.)* Ce critère décrivait l'ordre de numérotation d'une reprise de données. Il n'y a
    aucune donnée à reprendre. Voir « Non-objectifs ».
14. SI l'enregistrement d'un Remboursement est rejeté parce que son Numéro_Pièce est déjà
    attribué, ALORS LE Service_Remboursement DOIT recalculer le rang et rejouer l'enregistrement
    au plus 3 fois, puis retourner après la troisième tentative infructueuse une erreur nommant
    l'échec d'attribution du Numéro_Pièce sans créer de Remboursement.

### Requirement 7: Plafond de remboursement cumulé par Paiement

**User Story:** En tant qu'administrateur, je veux que le Système m'empêche de rembourser plus
qu'un versement n'a rapporté, y compris en plusieurs fois, afin qu'aucune caisse ne se retrouve
négative.

Le contrôle actuel compare la demande au seul Montant_Versé du Paiement. Deux Remboursements
égaux au Montant_Versé sont donc tous deux acceptés.

#### Acceptance Criteria

1. LE Service_Remboursement DOIT calculer le Plafond_Remboursable d'un Paiement comme le
   Montant_Versé de ce Paiement, traité comme zéro lorsqu'il est absent, diminué de la somme des
   montants des seuls Remboursements_Actifs déjà enregistrés sur ce Paiement, les Remboursements
   dont l'indicateur `active` vaut faux étant exclus de cette somme.
2. QUAND le montant demandé, arrondi à l'ÉCHELLE_MONÉTAIRE, est supérieur ou égal à 0,01 et
   inférieur ou égal au Plafond_Remboursable, LE Service_Remboursement DOIT créer le
   Remboursement portant ce montant arrondi et restituer à l'appelant le Plafond_Remboursable
   résiduel, égal au Plafond_Remboursable antérieur diminué du montant enregistré.
3. SI le montant demandé dépasse le Plafond_Remboursable, ALORS LE Service_Remboursement DOIT
   retourner une erreur de validation indiquant le Montant_Versé, la somme des
   Remboursements_Actifs déjà enregistrés et le Plafond_Remboursable, sans créer de
   Remboursement et sans modifier aucune donnée existante.
4. SI le montant demandé est absent, ou si son arrondi à l'ÉCHELLE_MONÉTAIRE est inférieur à 0,01
   ou supérieur à 999 999 999,99, ALORS LE Service_Remboursement DOIT retourner une erreur de
   validation sans créer de Remboursement.
5. LE Service_Remboursement DOIT arrondir le montant demandé à l'ÉCHELLE_MONÉTAIRE puis le
   valider selon le critère 7.4 avant d'évaluer le Plafond_Remboursable, afin qu'aucun montant
   absent, nul ou négatif ne puisse traverser le contrôle de plafond.
6. SI le Paiement visé par une demande de création de Remboursement ou par une lecture du
   Plafond_Remboursable est introuvable, ALORS LE Service_Remboursement DOIT retourner une erreur
   « ressource absente », sans créer de Remboursement et sans restituer aucune valeur de plafond.
7. LE Système DOIT maintenir en permanence, pour chaque Paiement, une somme des montants des
   Remboursements_Actifs inférieure ou égale au Montant_Versé de ce Paiement, y compris après
   toute réactivation d'un Remboursement.
8. LE Service_Remboursement DOIT évaluer le Plafond_Remboursable et enregistrer le Remboursement
   dans une seule transaction, et sérialiser ces transactions par Paiement de sorte que deux
   demandes concurrentes portant sur un même Paiement ne soient jamais évaluées contre le même
   Plafond_Remboursable, la seconde demande étant évaluée contre le Plafond_Remboursable
   résiduel.
9. LE Service_Remboursement DOIT exprimer le Plafond_Remboursable, la somme des
   Remboursements_Actifs et le montant enregistré en `BigDecimal` avec l'ÉCHELLE_MONÉTAIRE et
   l'ARRONDI_MONÉTAIRE.
10. QUAND le Plafond_Remboursable d'un Paiement est demandé en lecture, LE Système DOIT restituer
    le Montant_Versé de ce Paiement, la somme des montants des Remboursements_Actifs déjà
    enregistrés et le Plafond_Remboursable, à l'Administrateur comme au Consultant.
11. LE Service_Remboursement DOIT enregistrer sur le Remboursement l'Étudiant du Paiement
    rattaché, en ignorant tout identifiant d'Étudiant transmis dans la demande.
12. SI la réactivation d'un Remboursement désactivé porterait la somme des montants des
    Remboursements_Actifs de son Paiement au-delà du Montant_Versé de ce Paiement, ALORS LE
    Système DOIT refuser la réactivation avec une erreur de validation indiquant le
    Plafond_Remboursable courant, et laisser le Remboursement désactivé.
13. QUAND un Remboursement est créé, LE Système DOIT laisser inchangés le Montant_Versé et
    l'imputation du Paiement rattaché, ainsi que le Coût_Série_Prorata, le Montant_Dû_À_Ce_Jour,
    le Plafond_Encaissable, le statut de paiement et le report d'excédent de la Série de ce
    Paiement, la sortie de caisse étant portée par le seul Remboursement.
14. SI le Paiement rattaché ne référence aucun Étudiant, ALORS LE Service_Remboursement DOIT
    retourner une erreur de validation sans créer de Remboursement, aucun Remboursement ne
    pouvant exister sans bénéficiaire identifiable sur son Reçu_Remboursement.

### Requirement 8: Reçu de remboursement remettable

**User Story:** En tant qu'administrateur, je veux remettre au parent un reçu signé du montant
rendu, afin que la remise de l'argent soit attestée des deux côtés.

#### Acceptance Criteria

1. QUAND un Reçu_Remboursement est demandé pour un Remboursement, LE Service_Reçu_Remboursement
   DOIT produire, en au plus 3 secondes et dans la langue active de l'interface au moment de la
   demande, un document imprimable affichant le nom de l'école et son logo tels que configurés
   dans le Système, le Numéro_Pièce, la date du Remboursement, les nom et prénom de l'Étudiant,
   le montant remboursé et le Motif_Remboursement intégral jusqu'à 500 caractères, sans
   troncature ni abréviation.
2. QUAND un Reçu_Remboursement est produit, LE Service_Reçu_Remboursement DOIT y afficher la
   référence du Paiement rattaché, à savoir sa date exprimée en jour, mois et année, son
   Montant_Versé, ainsi que le nom du Groupe et le nom de la Série de ce Paiement.
3. LÀ OÙ le Paiement rattaché n'est rattaché à aucune Série, LE Service_Reçu_Remboursement DOIT
   afficher la mention « Hors série » à la place du nom de la Série.
4. QUAND un Reçu_Remboursement est produit, LE Service_Reçu_Remboursement DOIT y afficher le nom
   de l'Administrateur ayant enregistré le Remboursement, tel que porté par l'identifiant d'audit
   de création du Remboursement, une zone de signature identifiée comme celle de l'Administrateur
   et une zone de signature identifiée comme celle du bénéficiaire.
5. LE Service_Reçu_Remboursement DOIT afficher chaque montant du Reçu_Remboursement avec
   exactement deux décimales selon l'ÉCHELLE_MONÉTAIRE et l'ARRONDI_MONÉTAIRE, y compris lorsque
   la partie décimale est nulle.
6. QUAND le Reçu_Remboursement d'un même Remboursement est produit deux fois ou plus, LE
   Service_Reçu_Remboursement DOIT afficher à chaque production, à langue d'affichage égale, le
   même Numéro_Pièce, le même montant, le même Motif_Remboursement et la même date de
   Remboursement, caractère pour caractère.
7. SI le Remboursement visé est introuvable ou n'est pas un Remboursement_Actif, ALORS LE
   Service_Reçu_Remboursement DOIT retourner une erreur indiquant que le Remboursement demandé
   est introuvable ou inactif, sans produire de document ni restituer aucune donnée partielle de
   reçu.
8. LE Service_Reçu_Remboursement DOIT nommer le fichier produit du Numéro_Pièce suivi du nom de
   l'Étudiant, les espaces remplacés par un caractère de soulignement, les caractères autres que
   lettres, chiffres, tiret et soulignement retirés, pour une longueur totale d'au plus 150
   caractères, et DOIT produire ce même nom à chaque production du reçu d'un même Remboursement.
9. LE Système DOIT distinguer le Reçu_Remboursement du reçu de versement par un titre de document
   différent, par une mention qualifiant l'opération de sortie de caisse et par un libellé de
   montant nommant un montant remboursé et non un montant reçu, afin qu'une sortie d'argent ne
   puisse pas être confondue avec une entrée.
10. QUAND un Reçu_Remboursement est produit pour un Remboursement dont un Reçu_Remboursement a
    déjà été produit, LE Service_Reçu_Remboursement DOIT afficher sur le document la mention
    « Duplicata », le rang de la production courante et la date de cette production.
11. SI l'identifiant de l'utilisateur ayant enregistré le Remboursement est absent ou vaut
    l'identifiant de repli `system`, ALORS LE Service_Reçu_Remboursement DOIT afficher la mention
    « Administrateur non identifié » à la place du nom de l'Administrateur et conserver la zone de
    signature de l'Administrateur, sans faire échouer la production du document. Ce repli ne vise
    plus une reprise de données mais le cas d'un enregistrement sans utilisateur authentifié.
12. LÀ OÙ le Paiement rattaché n'est rattaché à aucun Groupe, LE Service_Reçu_Remboursement DOIT
    afficher la mention « Hors groupe » à la place du nom du Groupe.

### Requirement 9: Enregistrement d'un remboursement depuis l'interface

**User Story:** En tant qu'administrateur, je veux enregistrer un remboursement depuis la fiche
de l'étudiant, afin de ne pas dépendre d'un appel technique pour rendre de l'argent.

#### Acceptance Criteria

1. QUAND l'historique de paiement d'un Étudiant est consulté par un Administrateur, LE Système
   DOIT proposer sur chaque Paiement l'action d'enregistrement d'un Remboursement, cette action
   étant rendue indisponible et accompagnée d'une mention explicative lorsque le
   Plafond_Remboursable du Paiement est nul.
2. QUAND le formulaire d'enregistrement d'un Remboursement est ouvert, LE Système DOIT afficher à
   l'ÉCHELLE_MONÉTAIRE le Montant_Versé du Paiement, la somme des Remboursements_Actifs déjà
   accordés sur ce Paiement et le Plafond_Remboursable.
3. SI le montant saisi est absent, nul ou négatif, ou si le Motif_Remboursement saisi est vide ou
   composé uniquement d'espaces, ALORS LE Système DOIT empêcher la validation du formulaire et
   transmettre aucune demande d'enregistrement au serveur.
4. SI le montant saisi dépasse le Plafond_Remboursable affiché, ALORS LE Système DOIT afficher le
   Plafond_Remboursable, empêcher la validation du formulaire et transmettre aucune demande
   d'enregistrement au serveur.
5. QUAND un Remboursement est enregistré avec succès, LE Système DOIT proposer le téléchargement
   du Reçu_Remboursement correspondant.
6. QUAND un Remboursement est enregistré avec succès, LE Système DOIT actualiser l'historique de
   paiement de l'Étudiant, les montants remboursés affichés et le Plafond_Remboursable du
   Paiement concerné.
7. SI l'enregistrement du Remboursement échoue, ALORS LE Système DOIT afficher le message
   d'erreur retourné par le serveur et conserver les valeurs saisies, et DOIT afficher un message
   de repli indiquant que le résultat de l'opération est inconnu lorsque aucune réponse n'est
   parvenue dans un délai de 30 secondes.
8. LÀ OÙ l'utilisateur courant est un Consultant, LE Système DOIT masquer l'action
   d'enregistrement d'un Remboursement.
9. LE Système DOIT porter les appels réseau liés au Remboursement dans un service dédié aux
   Remboursements, appliquant la gestion d'erreur centralisée du projet.
10. QUAND l'Administrateur valide le formulaire d'enregistrement d'un Remboursement, LE Système
    DOIT demander une confirmation explicite rappelant le montant, le Motif_Remboursement et le
    caractère non annulable de l'opération, et DOIT transmettre aucune demande d'enregistrement
    au serveur ni perdre les valeurs saisies lorsque l'Administrateur renonce.
11. TANT QU'une demande d'enregistrement d'un Remboursement est en cours, LE Système DOIT rendre
    l'action de validation indisponible, de sorte qu'une confirmation ne puisse donner lieu qu'à
    une seule demande d'enregistrement.
12. SI le serveur rejette l'enregistrement parce que le montant demandé dépasse le
    Plafond_Remboursable, ALORS LE Système DOIT afficher le Plafond_Remboursable retourné par le
    serveur, remplacer les montants affichés par ceux qu'il retourne, conserver les valeurs
    saisies et présenter aucun Remboursement comme enregistré.

## Décisions tranchées

### La justification reste documentaire

**Question** : une Absence justifiée doit-elle changer le montant dû, et
`business-rules.md` marquait explicitement ce point comme différé.

**Décision** (propriétaire produit) : **aucun effet financier**. La justification sert au suivi
disciplinaire et au droit au rattrapage, pas au calcul. Aucune Absence n'augmente le
Montant_Dû_À_Ce_Jour, justifiée ou non ; toute Absence postérieure à l'inscription reste retenue
dans le Coût_Série_Prorata, justifiée ou non.

**Ce que la décision change** : aucun montant. Elle **lève l'ambiguïté** et impose de l'afficher
(exigence 3.4, 3.5), parce que l'ambiguïté avait déjà produit une attente erronée. La section
« DEFERRED policy decision » de `.kiro/steering/business-rules.md` doit être remplacée par cette
décision.

### Le rattrapage est gratuit dans le Groupe d'accueil

**Question** : lorsqu'un Étudiant rattrape une Séance dans un autre Groupe, la Séance_Manquée
reste facturée dans sa Série_Origine et la Séance de rattrapage devient facturable dans la
Série_Accueil. L'Étudiant paierait deux fois une Séance consommée une fois.

**Décision** (propriétaire produit) : l'Étudiant paie sa Séance dans son **Groupe d'origine**, où
sa place était réservée ; la Présence_Rattrapage est tracée mais **exclue du coût de la
Série_Accueil**.

**Conciliation avec `business-rules.md`** : la règle « une Séance suivie en rattrapage avant
l'inscription est facturable : elle a été consommée » reste vraie et n'est pas contredite. Elle
vise le cas où **aucune autre Série ne facture cette Séance**, c'est-à-dire le
Rattrapage_Consommé du Glossaire. La distinction opérationnelle porte donc sur un seul test, non
récursif : la Séance_Manquée est-elle facturée dans sa Série_Origine ? Le principe unique est
« une Séance consommée est facturée une fois et une seule » (critère 2.6).

### Décisions de détail

| Sujet | Décision | Exigence |
|---|---|---|
| **Absence d'origine après rattrapage** | Conservée telle quelle, marquée absente. La mention « Rattrapée » est un affichage dérivé, pas une réécriture de la présence | 1.3, 1.4 |
| **Un seul rattrapage par séance manquée** | Refus d'une seconde complétion sur la même Séance_Manquée : deux rattrapages d'une même séance rendraient le décompte des séances suivies indéterminé | 1.9 |
| **« Rattrapée » ≠ « non facturée »** | La Séance_Manquée reste facturée dans sa Série_Origine ; l'écran doit le dire | 2.2, 2.9 |
| **Rattrapage sans Séance_Manquée exploitable** | Traité comme Rattrapage_Consommé, donc facturé côté accueil. Règle de repli déterministe, pas d'exception | 2.7 |
| **Profondeur du test de compensation** | Un seul niveau : la Séance_Manquée est facturée côté origine si elle est postérieure ou égale à la Date_Inscription dans son Groupe. Aucune évaluation en cascade | Glossaire, 2.1 |
| **Présence ordinaire et rattrapage sur la même séance** | La Séance reste facturable côté accueil dès qu'une Présence_Active n'est pas compensatoire : la gratuité ne vaut que si le rattrapage est la seule raison d'être présent | 2.11 |
| **La séance consommée compte comme suivie côté origine** | Sans cela, une séance rattrapée n'augmentait le Montant_Dû_À_Ce_Jour d'aucune Série : ni de l'accueil (écartée) ni de l'origine (absence). La Présence d'origine n'est pas réécrite pour autant | 2.12 |
| **Plafond tombé à zéro sur une série déjà encaissée** | Le versement devient un excédent exposé par le devis, sans imputation automatique ailleurs. Aucun mécanisme de report nouveau n'est inventé ici | 2.13 |
| **Libellés d'écran** | Le contenu à énoncer est normatif, le texte exact reste à l'implémentation. Les mentions portent sur la ligne de l'absence, pas en seule légende, sinon elles ne sont pas testables | 3.4, 3.5, 3.6 |
| **Distinction justifiée / injustifiée** | Maintenue par un libellé textuel et non par la couleur seule : l'effet financier est nul mais le suivi disciplinaire demeure, et la couleur seule n'est pas accessible | 3.8 |
| **Justification non renseignée** | Comptée parmi les injustifiées et libellée comme non renseignée. Aucun `NULL` présenté comme un « non » | 3.9 |
| **Modification de justification identique** | Sans effet et sans Entrée_Audit : la piste d'audit ne consigne que les changements réels | 4.3 |
| **Justification d'une Présence** | Refusée : l'Indicateur_Justification ne s'applique qu'à une Absence | 4.6 |
| **Point d'entrée `PUT /{id}` inerte** | Retiré. Un appel qui réussit sans rien modifier induit l'appelant en erreur | 4.9 |
| **Corps de requête restreint** | Deux champs acceptés et énumération des champs laissés inchangés : le PATCH générique avait été retiré parce qu'il projetait une Map arbitraire sur l'entité, la faille ne doit pas se rouvrir | 4.1, 4.2 |
| **Année scolaire close** | Modification refusée, alignée sur le garde-fou de lecture seule existant. Aucune autre borne d'ancienneté | 4.13, 4.14 |
| **Forme de la piste d'audit** | Journal dédié en ajout seul. Les colonnes `updated_by` / `date_update` de `BaseEntity` ne disent pas *quel* champ a changé, ni ne conservent l'historique | 5.1, 5.3 |
| **Échec transitoire ou permanent** | Rejeu borné à 3 tentatives pour un échec transitoire seulement. Rejouer un échec déterministe ne fait que retarder la même erreur | 5.5, 5.10 |
| **Survie de l'audit** | Les Entrées_Audit survivent à la désactivation et à la suppression de la Présence : un audit qui disparaît avec son objet ne prouve rien | 5.11 |
| **Unicité du numéro de pièce** | Portée par une contrainte de stockage, pas par le calcul applicatif. Le déploiement mono-instance rend la contrainte facile à satisfaire, il ne la remplace pas | 6.6, 6.14 |
| **Rangs manquants tolérés** | Un rang réservé par une création échouée n'est ni comblé ni réutilisé : combler un trou réattribuerait un numéro déjà imprimé sur un reçu | 6.5 |
| **Plafond de remboursement** | Cumulé par Paiement, déduction des Remboursements_Actifs. Un montant nul est refusé | 7.1, 7.4 |
| **Remboursement désactivé** | Exclu du plafond consommé, comme il est exclu des recettes ; sa réactivation est refusée si elle ferait dépasser le Montant_Versé | 7.1, 7.12 |
| **Le remboursement ne touche pas le devis** | Ni le Montant_Versé, ni le statut de paiement, ni le report d'excédent de la Série ne changent : la sortie de caisse est portée par le seul Remboursement | 7.13 |
| **Rôle requis** | Administrateur pour modifier une justification et pour créer un Remboursement ; le masquage de l'interface est une commodité, le backend reste l'autorité | 4.4, 4.11, 6.11, 9.8 |
| **Lecture de la piste d'audit** | Ouverte aux deux rôles : un Consultant doit pouvoir constater qui a modifié quoi | 5.7 |
| **Lecture du plafond** | Ouverte aux deux rôles : constater ce qui reste remboursable n'est pas une écriture | 7.10 |
| **Réimpression du reçu** | Signalée comme duplicata, avec rang et date de production, sans altérer les données du reçu. Un reçu réimprimé sans mention pourrait servir deux fois | 8.6, 8.10 |
| **Confirmation avant sortie d'argent** | Exigée, et validation verrouillée pendant la requête : l'annulation d'un Remboursement est hors périmètre, donc le geste est irréversible | 9.10, 9.11 |
| **Plafond périmé côté client** | Le blocage client ne dispense pas du rejet serveur : un encaissement concurrent peut avoir changé le plafond depuis l'ouverture du formulaire | 9.4, 9.12 |

## Évolutions de schéma

Le schéma est versionné par Flyway et `ddl-auto=validate` s'applique en production. Les
évolutions suivantes exigent un script `V2__…` livré avec la fonctionnalité ; aucune ne peut
reposer sur la génération automatique Hibernate.

| Évolution | Portée | Exigence |
|---|---|---|
| Journal d'audit de la justification (table, en ajout seul, avec horodatage à la milliseconde et Rang_Séquence_Audit) | Nouvelle table | 5.1, 5.7 |
| `refund.reason` | Nouvelle colonne, nullable ; l'obligation de motif est applicative | 6.1 |
| `refund.refund_number` | Nouvelle colonne `NOT NULL`, contrainte d'unicité | 6.4, 6.6 |
| Contrainte d'unicité sur le couple (Étudiant, Séance_Manquée) des Présences_Rattrapage | Nouvelle contrainte | 1.9 |

## Non-objectifs

- **Toute reprise des données existantes.** Décision produit : l'existant ne compte pas. Les données
  actuelles sont des données de test destinées à être supprimées, et l'installation cible démarre sur
  une base vide. Aucune exigence ne porte donc sur la renumérotation de Remboursements passés, le
  rattachement de Présences orphelines, la détection de doublons antérieurs, ni l'affichage de
  Remboursements « antérieurs à la traçabilité ». Les critères 1.6, 1.11, 6.10 et 6.13 ont été
  retirés à ce titre. Les contraintes de schéma posées par la fonctionnalité protègent les données à
  venir, qui sont le seul enjeu.
- **Le schéma d'exemption** (`student_groups.exemption_rate`) : décision de conception distincte,
  traitée séparément. Aucune exigence de cette fonctionnalité ne le modifie.
- **Le droit au rattrapage** (`attendance.catch_up_right`), déjà indépendant de la justification :
  conservé tel quel, aucune règle nouvelle.
- **La facturation des Absences en réconciliation de fin d'année** : la décision ci-dessus ferme
  la question pour la justification. La réconciliation de fin d'année, dont
  `business-rules.md` signale que « les séances restantes ne sont pas exactement définies », reste
  hors périmètre.
- **Le reçu de versement** existant : inchangé. L'exigence 8.9 impose seulement que le
  Reçu_Remboursement s'en distingue.
- **L'annulation d'un Remboursement** : non couverte. Seule la création est spécifiée, et
  l'exigence 9.10 en tire la conséquence en imposant une confirmation.
- **Un mécanisme de report d'excédent nouveau** : le critère 2.13 se borne à exposer l'excédent
  existant, sans introduire d'imputation automatique.

## Propriétés de correction candidates

Ces propriétés sont numérotées comme dans le document de conception, où chacune est détaillée.
Elles sont rattachées aux critères
d'acceptation et implémentées en jqwik selon la convention du projet
(`// Feature: absence-justification-and-refund-receipts, Property N: …`, `@Property(tries = 100)`).
Elles portent sur des règles monétaires et des invariants d'état, où cent tirages trouvent des cas
que deux exemples manquent.

| N | Propriété | Type | Exigence |
|---|---|---|---|
| 1 | Pour tout ensemble de Séances, de Présences et de Présences_Rattrapage d'un Étudiant, chaque Séance suivie est retenue comme Séance_Facturable dans une Série au plus, et le résultat ne dépend pas de l'ordre d'évaluation des Séries | Invariant | 2.6 |
| 2 | Pour toute Série_Accueil, le Coût_Série_Prorata et le Montant_Dû_À_Ce_Jour calculés en présence de Rattrapages_Compensatoires égalent ceux calculés en leur absence | Métamorphique | 2.3, 2.4 |
| 3 | Pour toute Série_Origine, le Montant_Dû_À_Ce_Jour croît du Prix_Séance net pour chaque Séance_Manquée couverte par un Rattrapage_Compensatoire, sans que la Présence d'origine cesse d'être une Absence | Métamorphique | 2.12 |
| 4 | Pour tout ensemble d'Absences d'une Série, faire varier arbitrairement l'Indicateur_Justification laisse le Coût_Série_Prorata, le Montant_Dû_À_Ce_Jour, le Plafond_Encaissable et le statut de paiement identiques | Métamorphique | 3.1, 3.2, 3.3 |
| 5 | Pour toute Présence et toute valeur de justification, appliquer deux fois la même valeur produit le même état et une seule Entrée_Audit | Idempotence | 4.3, 5.1 |
| 6 | Pour toute suite de modifications de justification, la valeur courante égale la valeur appliquée par la dernière Entrée_Audit, et le nombre d'Entrées_Audit égale le nombre de changements effectifs de valeur | Invariant | 5.3, 5.8 |
| 7 | Pour toute suite de demandes de Remboursement sur un même Paiement, la somme des montants acceptés reste inférieure ou égale au Montant_Versé, et toute demande dépassant le Plafond_Remboursable est rejetée | Invariant monétaire | 7.1, 7.3, 7.7 |
| 8 | Pour tout montant demandé inférieur à 0,01 après arrondi, ou tout Motif_Remboursement vide, la création est rejetée ; tout montant accepté est exprimé à l'ÉCHELLE_MONÉTAIRE | Condition d'erreur | 6.2, 7.4, 7.9 |
| 9 | Pour tout ensemble de Remboursements créés, les Numéros_Pièce sont deux à deux distincts, inchangés après création, et leur rang est strictement croissant au sein d'une même année civile | Invariant | 6.6, 6.7, 6.12 |
| 10 | Pour tout Remboursement, deux productions successives du Reçu_Remboursement affichent les mêmes Numéro_Pièce, montant, motif et date, et la seconde porte la mention « Duplicata » | Idempotence | 8.6, 8.10 |
| 11 | Pour toute demande de rattrapage complétée, la Présence_Rattrapage créée porte la Série de sa Séance | Invariant | 1.1, 1.2 |
| 12 | Pour tout Remboursement créé sur un Paiement rattaché à une Série, le Coût_Série_Prorata, le Montant_Dû_À_Ce_Jour, le Plafond_Encaissable et le statut de paiement de cette Série sont inchangés | Invariant | 7.13 |
