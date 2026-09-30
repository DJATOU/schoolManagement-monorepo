# Requirements Document

## Introduction

Cette fonctionnalité répond à une demande du propriétaire produit : « il faut un système pour
l'administrateur qui permet de corriger les soucis de paiement, de présence, etc. », et à une
règle qu'il a tranchée au même moment : **avant son inscription, l'Étudiant n'est pas concerné**
(`.kiro/steering/business-rules.md`, section du même nom).

**Ce qui existe déjà, et sert de modèle.** La correction d'un Détail_Paiement est le seul
chemin complet : réservée à l'Administrateur, Motif obligatoire, Trace immuable, refus sur Année
close (`PaymentDetailAdminService`). La justification d'absence et la décision de rattrapage ont
chacune une Trace, sans exiger de Motif. Cette fonctionnalité étend ce modèle aux domaines qui
en sont privés ; elle ne le réinvente pas.

**Quatre défauts avérés motivent cette fonctionnalité.**

1. **La date d'inscription ne peut être ni choisie ni corrigée.** `StudentGroupEntity.onCreate`
   écrase `date_assigned` par l'heure de l'enregistrement, même quand une date est transmise ;
   aucun point d'entrée ne la modifie ensuite. Or cette date décide des Séances facturables et de
   la Feuille_Appel. Un Étudiant enregistré une semaine après son arrivée réelle est traité comme
   arrivé le jour de sa saisie, sans recours.
2. **Une présence ne peut pas être corrigée isolément.** Passer un Étudiant d'absent à présent
   après validation impose de dévalider la Séance entière, ce qui désactive toutes ses Présences,
   puis de tout ressaisir. Aucun Motif, aucune Trace, aucun refus sur Année close.
3. **Le Système accepte des absences hors de toute inscription.** Aucun contrôle serveur ne
   compare la date de la Séance à la Date_Inscription, ni même ne vérifie une inscription. Le
   filtrage n'existe que dans l'écran, et son repli (« si personne n'est inscrit avant la
   séance, afficher tout le groupe ») ramène sur la Feuille_Appel des Étudiants non concernés et
   des inscriptions clôturées. Tout Étudiant décoché devient une absence.
4. **Plusieurs écritures qui modifient un montant dû sont muettes.** Remise créée, modifiée ou
   supprimée sans Motif ni Trace, suppression définitive ; Présence supprimée définitivement ;
   Séance dévalidée ; inscription clôturée. Aucune ne laisse savoir qui a changé quoi, alors que
   chacune change ce que l'Étudiant doit.

**Découpage en deux étapes.** Étape 1, ce qui touche directement aux montants : exigences 1, 2,
3, 7 et le journal par Étudiant (8.2, 8.3, 8.5). Étape 2 : exigences 4, 5, 6, 9 et le journal
complet filtrable (8.1, 8.4). L'étape 2 n'est conçue qu'une fois l'étape 1 livrée.

**Hors périmètre.** Le calcul des montants n'est pas modifié : Séances facturables, prorata,
report, rattrapage et neutralité de la justification restent tels que spécifiés et éprouvés. Seuls
changent les données qui y entrent, et la manière de les corriger. La réconciliation de fin
d'année reste une décision différée.

## Glossary

- **Administrateur** : utilisateur au rôle ADMIN. Seul rôle autorisé à corriger.
- **Motif** : texte libre obligatoire, non vide après suppression des espaces, de 500 caractères
  au plus, saisi par l'Administrateur pour justifier une Correction.
- **Correction** : écriture qui modifie une donnée déjà enregistrée ayant un effet sur un montant
  dû, un montant versé ou une Présence.
- **Trace** : enregistrement immuable d'une Correction — domaine, identifiant de la donnée, valeur
  avant, valeur après, auteur, horodatage, rang de séquence, Motif. Elle survit à la suppression
  de la donnée corrigée.
- **Journal_Corrections** : l'ensemble des Traces, consultable par l'Administrateur.
- **Date_Inscription** : `student_groups.date_assigned`, date d'arrivée réelle de l'Étudiant dans
  le Groupe.
- **Séance_Non_Concernée** : pour un Étudiant et un Groupe, Séance du Groupe dont la date est
  antérieure à sa Date_Inscription.
- **Feuille_Appel** : la liste des Étudiants proposée pour la prise de présence d'une Séance.
- **Année_Close** : Année scolaire qui n'est pas l'année courante (school-year, exigence 9).

## Requirements

### Requirement 1: Date d'inscription réelle et corrigeable

**User Story:** En tant qu'Administrateur, je veux saisir la date réelle d'arrivée d'un Étudiant
et pouvoir la corriger, afin que ce qu'il doit et les séances où il est attendu correspondent à la
réalité et non au jour de sa saisie.

#### Acceptance Criteria

1. QUAND une inscription est créée avec une Date_Inscription fournie, LE Système DOIT conserver
   cette date telle quelle.
2. QUAND une inscription est créée sans Date_Inscription, LE Système DOIT retenir la date du jour.
3. LE Système DOIT accepter une Date_Inscription postérieure à la date du jour : un Étudiant peut
   être inscrit à l'avance. Il n'est alors concerné par aucune Séance antérieure à cette date
   (exigence 2).
4. SI la Date_Inscription fournie est hors de l'Année scolaire du Groupe, ALORS LE Système DOIT
   refuser l'inscription avec un message nommant les bornes de l'année.
5. LE Système DOIT traiter la Date_Inscription comme une date calendaire : une Séance tenue le
    jour même de l'inscription concerne l'Étudiant, quelle que soit l'heure de la saisie.
6. LE Système DOIT permettre à l'Administrateur de corriger la Date_Inscription d'une inscription
   existante, avec un Motif.
7. QUAND la Date_Inscription est corrigée, LE Système DOIT écrire une Trace portant l'ancienne et
   la nouvelle date.
8. SI la correction de Date_Inscription rendait Séance_Non_Concernée une Séance où l'Étudiant a
   une absence active, ALORS LE Système DOIT refuser la correction et lister ces absences, sauf
   demande explicite de l'Administrateur de les retirer dans la même opération.
9. QUAND l'Administrateur demande de retirer ces absences avec la correction, LE Système DOIT
    appliquer la correction de date et le retrait de chaque absence listée en une seule
    opération indivisible, sous un seul Motif, avec une Trace par absence retirée et une Trace
    pour la date. Si l'une échoue, aucune n'est appliquée.
10. SI les absences à retirer diffèrent de celles présentées à l'Administrateur au moment de sa
    demande, ALORS LE Système DOIT refuser l'opération et présenter la liste à jour : rien n'est
    retiré sans avoir été vu.
11. QUAND la Date_Inscription est corrigée, LE Système DOIT refléter la nouvelle date dans le coût
   au prorata, le montant dû à ce jour et le plafond encaissable, sans autre intervention.
12. SI le Groupe appartient à une Année_Close, ALORS LE Système DOIT refuser la correction.

### Requirement 2: L'Étudiant non inscrit n'est pas concerné

**User Story:** En tant qu'Administrateur, je veux qu'un Étudiant ne soit jamais noté absent à une
séance tenue avant son arrivée, afin qu'il en soit dispensé d'office sans démarche de ma part.

#### Acceptance Criteria

1. QUAND la Feuille_Appel d'une Séance est construite, LE Système DOIT n'y inclure que les
   Étudiants dont l'inscription au Groupe est active et dont la Date_Inscription est antérieure
   ou égale à la date de la Séance.
2. LE Système NE DOIT PAS compléter une Feuille_Appel vide par l'ensemble des Étudiants du Groupe.
3. SI une absence est soumise pour un Étudiant sur une Séance_Non_Concernée, ALORS LE Système DOIT
   la refuser côté serveur, quel que soit l'écran d'origine, avec un message nommant l'Étudiant et
   sa Date_Inscription.
4. SI une absence est soumise pour un Étudiant sans inscription active au Groupe, ALORS LE Système
   DOIT la refuser.
5. LE Système DOIT continuer d'accepter une présence sur une Séance_Non_Concernée : c'est un
   rattrapage consommé, facturable (business-rules, prorata).
6. QUAND une validation en masse contient au moins une absence refusée, LE Système DOIT refuser la
   validation entière et nommer chaque ligne refusée, afin qu'aucune Séance ne soit validée à
   moitié.

### Requirement 3: Correction d'une présence

**User Story:** En tant qu'Administrateur, je veux corriger la présence d'un seul Étudiant sur une
séance validée, afin de réparer une erreur de saisie sans dévalider la séance pour tous.

#### Acceptance Criteria

1. LE Système DOIT permettre à l'Administrateur de passer une Présence active de présent à absent,
   et d'absent à présent, avec un Motif.
2. LE Système DOIT permettre à l'Administrateur d'ajouter une Présence manquante pour un Étudiant
   de la Feuille_Appel d'une Séance validée, avec un Motif.
3. LE Système DOIT permettre à l'Administrateur de retirer une Présence, avec un Motif ; le
   retrait DOIT être une désactivation et non une suppression définitive.
4. SI le passage à absent porte sur une Séance_Non_Concernée, ALORS LE Système DOIT le refuser
   (exigence 2.3).
5. QUAND une Présence passe à présent, LE Système DOIT effacer son indicateur de justification, et
   l'inscrire dans la même Trace.
6. SI la Présence est un rattrapage, ALORS LE Système DOIT refuser la correction par ce point
   d'entrée et désigner la correction de rattrapage existante, qui porte la décision « déjà
   payée ».
7. QUAND une Présence est corrigée, LE Système DOIT écrire une Trace portant la valeur avant et
   la valeur après.
8. SI la Séance appartient à une Année_Close, ALORS LE Système DOIT refuser la correction.

### Requirement 4: Dévalidation et suppression tracées

**User Story:** En tant qu'Administrateur, je veux que dévalider une séance ou en retirer les
présences laisse une trace motivée, afin de pouvoir expliquer après coup un montant qui a changé.

#### Acceptance Criteria

1. QUAND une Séance est dévalidée, LE Système DOIT exiger un Motif et écrire une Trace listant les
   Présences désactivées.
2. LE Système NE DOIT PAS supprimer définitivement une Présence par un point d'entrée accessible
   à l'Administrateur ; les suppressions définitives existantes DOIVENT devenir des
   désactivations tracées.
3. SI la Séance appartient à une Année_Close, ALORS LE Système DOIT refuser la validation, la
   dévalidation et le retrait de Présences.

### Requirement 5: Remises tracées

**User Story:** En tant qu'Administrateur, je veux que toute remise accordée, modifiée ou retirée
soit motivée et tracée, afin que la réduction d'un montant dû soit toujours explicable.

#### Acceptance Criteria

1. QUAND une remise est créée, modifiée ou retirée, LE Système DOIT exiger un Motif.
2. LE Système DOIT écrire une Trace pour chacune de ces opérations, portant le taux avant et après.
3. LE Système DOIT retirer une remise par désactivation et non par suppression définitive.
4. SI la remise porte sur une Série ou un Groupe d'une Année_Close, ALORS LE Système DOIT refuser
   l'opération.

### Requirement 6: Sortie de groupe tracée, symétrique de l'entrée

**User Story:** En tant qu'Administrateur, je veux que la clôture d'une inscription soit motivée
et tracée, et qu'après sa sortie l'Étudiant ne soit plus concerné, afin de retrouver pourquoi et
quand il a quitté un Groupe.

#### Acceptance Criteria

1. QUAND une inscription est clôturée, LE Système DOIT exiger un Motif et une date de sortie,
   proposée par défaut à la date du jour, et écrire une Trace.
2. LE Système DOIT considérer comme Séance_Non_Concernée toute Séance postérieure à la date de
   sortie : l'Étudiant est concerné entre sa Date_Inscription et sa date de sortie incluses.
3. SI une absence active ou une présence ordinaire existe sur une Séance postérieure à la date de
   sortie, ALORS LE Système DOIT refuser la clôture et lister ces Présences.
4. LE Système DOIT accepter une présence de rattrapage postérieure à la date de sortie : c'est un
   autre cas, régi par les règles de rattrapage.
5. LE Système DOIT conserver les inscriptions clôturées dans l'historique, avec leurs dates
   d'entrée et de sortie.

### Requirement 7: Motif et Trace communs

**User Story:** En tant qu'Administrateur, je veux une seule manière de motiver et de consulter
les corrections, quel que soit le domaine, afin de ne pas apprendre une règle par écran.

#### Acceptance Criteria

1. LE Système DOIT appliquer une seule règle de validation du Motif à toute Correction : non vide
   après suppression des espaces, 500 caractères au plus, espaces de tête et de fin retirés.
2. LE Système DOIT refuser une Correction sans changement effectif, plutôt que d'écrire une Trace
   sans information.
3. LE Système DOIT attribuer chaque Trace à l'Administrateur authentifié, et jamais à une valeur
   fournie par le client.
4. LE Système DOIT attribuer à chaque Trace un rang de séquence strictement croissant, afin que
   « quelle est la dernière correction » ait une réponse même pour deux corrections dans la même
   milliseconde.
5. LE Système DOIT conserver une Trace après la désactivation ou la suppression de la donnée
   corrigée.
6. SI la Correction échoue, ALORS LE Système NE DOIT écrire aucune Trace.
7. LE Système DOIT réserver toute Correction au rôle ADMIN ; le rôle VIEWER DOIT recevoir 403.

### Requirement 8: Journal des corrections

**User Story:** En tant qu'Administrateur, je veux consulter l'historique des corrections d'un
Étudiant, d'un Groupe ou d'une Séance, afin de répondre à un parent qui conteste un montant.

#### Acceptance Criteria

1. LE Système DOIT exposer le Journal_Corrections filtrable par Étudiant, par Groupe, par Séance,
   par domaine et par période.
2. LE Système DOIT présenter les Traces de la plus récente à la plus ancienne.
3. LE Système DOIT inclure dans le Journal_Corrections les Traces déjà existantes : Détails de
   paiement, justifications d'absence, décisions de rattrapage.
4. LE Système DOIT rendre le Journal_Corrections consultable pour une Année_Close.
5. LE Système DOIT permettre, depuis la fiche d'un Étudiant, d'ouvrir son Journal_Corrections.

### Requirement 9: Signalement des incohérences existantes

**User Story:** En tant qu'Administrateur, je veux voir les données déjà en contradiction avec la
règle « non concerné avant l'inscription », afin de les corriger une fois pour toutes.

#### Acceptance Criteria

1. LE Système DOIT lister, pour l'Année courante, les absences actives portant sur une
   Séance_Non_Concernée, et les absences d'Étudiants sans inscription active au Groupe.
2. QUAND une telle absence est listée, LE Système DOIT proposer les deux corrections possibles :
   retirer l'absence (exigence 3.3) ou corriger la Date_Inscription (exigence 1.6).
3. LE Système NE DOIT PAS corriger ces données automatiquement : choisir entre une absence erronée
   et une date d'inscription erronée appartient à l'Administrateur.
