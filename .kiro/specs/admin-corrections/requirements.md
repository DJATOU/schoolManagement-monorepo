# Requirements Document

## Introduction

Le propriétaire produit demande « un système pour l'administrateur qui permet de corriger les
soucis de paiement, de présence, etc. ». Le Système sera installé sur site, sur le poste de
l'école, et utilisé par une administratrice seule, sans compétence technique. Chaque exigence
est écrite depuis sa place : elle doit pouvoir corriger une erreur **sans comprendre le modèle de
données**, voir **avant de confirmer** ce que la correction change pour le parent, et **répondre à
un parent** qui conteste un montant, reçu en main.

### Les erreurs réelles à corriger, par fréquence

1. Un encaissement erroné : montant mal tapé (20 000 au lieu de 2 000), mauvais Étudiant,
   mauvaise Série.
2. Une présence mal saisie : absent coché présent, ou l'inverse ; Séance validée par erreur.
3. Une date d'arrivée fausse : Étudiant enregistré une semaine après son arrivée réelle.
4. Un départ : Étudiant qui quitte un Groupe.

### Défauts avérés dans le code actuel

1. **Un versement n'a pas d'existence propre.** `payments` ne porte qu'un cumul par Étudiant et
   Série ; sa date est réécrite à chaque écriture. Une Séance n'a qu'une ligne de ventilation,
   complétée par les versements successifs : l'argent de plusieurs versements s'y mélange. Aucun
   versement ne peut donc être annulé sans toucher aux autres.
2. **Corriger un détail de paiement peut effacer de l'argent reçu.** `recalculatePayment`
   remplace le cumul de la Série par la somme des lignes de ventilation actives. Toute part non
   ventilée (avance sur des séances non encore planifiées) disparaît du registre à la première
   correction de n'importe quelle ligne de la Série. Rien n'empêche ensuite le cumul de passer
   sous le total déjà remboursé.
3. **Un reçu de paiement n'est pas vérifiable.** Sa référence est construite par l'écran à partir
   de l'identifiant du cumul et de sa date, réécrite ensuite ; rien n'est conservé à l'impression.
   Un reçu présenté par un parent ne peut être rapproché d'aucun versement.
4. **La ventilation ignore la réduction.** Le plafond par Séance est le tarif catalogue : pour un
   Étudiant réduit, des Séances apparaissent « payées » à tort.
5. **La date d'inscription ne peut être ni choisie ni corrigée** : `StudentGroupEntity.onCreate`
   l'écrase par l'heure de saisie.
6. **Aucune présence ne peut être corrigée isolément** : il faut dévalider la Séance entière,
   sans Motif ni Trace.
7. **Le Système accepte des absences hors de toute inscription** ; le repli de la Feuille_Appel
   ramène des Étudiants non concernés et des inscriptions clôturées.
8. **Une présence de rattrapage saisie à tort ne peut pas être retirée.**
9. **Une inscription clôturée disparaît de toutes les Séances, passées comprises** : une Séance
   validée en retard, après le départ d'un Étudiant, ne peut plus porter sa vraie absence.

### Découpage

- **Étape 1** — tout ce qui touche à l'argent et à ce qui le détermine : exigences 1 à 12.
- **Étape 2** — traçabilité complète des autres écritures : remises motivées et tracées, fin de
  toute suppression définitive, signalement des incohérences existantes, journal filtrable
  multi-critères, transfert d'un Étudiant vers un autre Groupe avec ses présences et paiements,
  raccordement des outils existants (détail de paiement, remboursement) aux Motifs communs.
  Conçue après livraison de l'étape 1.

### Ce qui change dans le calcul, et ce qui ne change pas

Inchangés : prorata, report, rattrapage, neutralité de la justification, montant dû à ce jour.

**Deux changements assumés**, parce que le calcul actuel contredit une règle tranchée :

- **Séances facturables d'un Étudiant sorti.** Aujourd'hui, une inscription clôturée n'est plus
  trouvée par le résolveur : l'Étudiant est traité comme sans inscription, et seules les Séances
  portant une Présence restent facturables. Une Séance de sa période, pas encore validée au
  moment du départ, cesse donc d'être due. Avec la Date_Sortie, sont facturables les Séances de la
  Fenêtre_Inscription, plus les Séances hors fenêtre effectivement suivies (exigence 6).
- **Ventilation au prix net** : la répartition d'un versement entre Séances ne plafonne plus au
  tarif catalogue mais au prix réduit (exigence 1.6). Le montant versé, le montant dû et le
  statut ne changent pas ; seule change la liste des Séances affichées comme payées.

### Hors périmètre

Changent les données qui entrent dans le calcul et la manière de les corriger, pas le reste.

**Aucune reprise de données.** Le Système est encore en test : la base est réinitialisée avant
l'installation chez le client. Aucune migration ne transforme de données existantes ; les
migrations de l'étape 1 ne font qu'ajouter des tables et des colonnes. La réconciliation de fin d'année reste différée. La préinscription dans un Groupe de
l'année scolaire suivante est exclue : ce Groupe appartient à une année qui n'est pas encore
courante, donc en lecture seule.

## Glossary

- **Administrateur** : utilisateur au rôle ADMIN, seul autorisé à corriger.
- **Encaissement** : un versement reçu d'un parent, tel qu'il a eu lieu — montant, date, heure,
  Étudiant, Série visée, mode de paiement. Il est l'unité que l'Administrateur annule ou corrige.
- **Numéro_Reçu** : identifiant unique et définitif d'un Encaissement, imprimé sur son reçu.
- **Imputation** : part d'un Encaissement créditée sur une Série — la Série visée, ou une Série
  suivante par report.
- **Annulation** : neutralisation d'un Encaissement erroné. L'Encaissement reste enregistré,
  marqué annulé ; il ne compte plus dans aucun montant.
- **Remplacement** : Annulation d'un Encaissement suivie, dans la même opération, d'un nouvel
  Encaissement corrigé qui le référence.
- **Correction** : écriture qui modifie une donnée enregistrée ayant un effet sur un montant dû,
  un montant versé ou une Présence.
- **Motif** : raison d'une Correction : un Motif_Type choisi dans une liste, et un texte libre
  obligatoire si le type est « Autre ».
- **Aperçu** : présentation, avant confirmation, de l'effet d'une Correction sur les montants de
  chaque Série concernée.
- **Trace** : enregistrement immuable d'une Correction — objet, valeur avant, valeur après,
  effet sur les montants, auteur, horodatage, rang, Motif. Elle survit à la donnée corrigée.
- **Journal** : l'ensemble des Traces d'un Étudiant, lisible et imprimable.
- **Date_Inscription**, **Date_Sortie** : début et fin de la présence de l'Étudiant dans le Groupe.
- **Fenêtre_Inscription** : période de la Date_Inscription à la Date_Sortie incluses, ou sans fin
  si l'inscription n'est pas clôturée.
- **Séance_Non_Concernée** : Séance du Groupe hors de la Fenêtre_Inscription de l'Étudiant.
- **Feuille_Appel** : liste des Étudiants proposée pour la prise de présence d'une Séance.
- **Année_Close** : Année scolaire non courante, en lecture seule.

## Requirements

### Requirement 1: L'Encaissement, unité de paiement

**User Story:** En tant qu'Administratrice, je veux que chaque versement reçu reste identifiable
tel qu'il a eu lieu, afin de pouvoir retrouver, annuler ou corriger précisément celui-là.

#### Acceptance Criteria

1. QUAND un versement est encaissé, LE Système DOIT enregistrer un Encaissement portant le montant
   reçu, l'Étudiant, le Groupe, la Série visée, le mode de paiement, la note, l'auteur, la date et
   l'heure d'encaissement fixées par le serveur.
2. LE Système DOIT attribuer à chaque Encaissement un Numéro_Reçu unique, de la forme
   `RECU-AAAA-NNNN`, séquentiel par année civile, attribué par le serveur et jamais réutilisé.
3. LE Système DOIT rattacher chaque Imputation et chaque ligne de ventilation à l'Encaissement qui
   l'a produite ; aucune ligne ne DOIT mêler l'argent de deux Encaissements.
4. LE Système DOIT maintenir, pour chaque Étudiant et chaque Série, l'invariant : montant versé =
   somme des Imputations des Encaissements non annulés, moins les remboursements.
5. LE Système NE DOIT modifier ni le montant, ni la date, ni l'Étudiant, ni la Série d'un
   Encaissement enregistré ; toute rectification passe par une Annulation ou un Remplacement.
6. LE Système DOIT plafonner la ventilation d'une Séance au prix net de l'Étudiant, réduction
   comprise.
7. LE Système DOIT appliquer la même règle au chemin d'encaissement d'un rattrapage, y compris la
   clé d'idempotence.

### Requirement 2: Annuler un Encaissement

**User Story:** En tant qu'Administratrice, je veux annuler un encaissement saisi par erreur, afin
qu'il ne compte plus, sans effacer la trace qu'il a eu lieu.

#### Acceptance Criteria

1. LE Système DOIT permettre d'annuler un Encaissement, avec un Motif et après Aperçu.
2. QUAND un Encaissement est annulé, LE Système DOIT neutraliser toutes ses Imputations, reports
   compris, et toutes ses lignes de ventilation, puis recalculer les montants des Séries concernées.
3. LE Système DOIT conserver l'Encaissement annulé, visible dans l'historique de l'Étudiant et
   marqué « annulé » avec la date, l'auteur et le Motif.
4. SI l'Annulation ferait passer le montant versé d'une Série sous le total déjà remboursé sur
   cette Série, ALORS LE Système DOIT la refuser et nommer le remboursement en cause.
5. SI l'Encaissement est déjà annulé, ALORS LE Système DOIT refuser une seconde Annulation.
6. QUAND le reçu d'un Encaissement annulé est réimprimé, LE Système DOIT le marquer « ANNULÉ »,
   avec la date d'annulation et, s'il existe, le Numéro_Reçu de l'Encaissement de remplacement.
7. SI l'Encaissement porte sur une Année_Close, ALORS LE Système DOIT refuser l'Annulation.

### Requirement 3: Corriger un Encaissement

**User Story:** En tant qu'Administratrice, je veux corriger un encaissement mal saisi — montant,
Étudiant, Série — en une seule opération, afin que le registre corresponde à l'argent reçu.

#### Acceptance Criteria

1. LE Système DOIT permettre de corriger le montant, l'Étudiant, le Groupe, la Série visée, le mode
   de paiement ou la note d'un Encaissement, avec un Motif et après Aperçu.
2. QUAND un Encaissement est corrigé, LE Système DOIT procéder par Remplacement : annuler l'original
   et créer un nouvel Encaissement avec un nouveau Numéro_Reçu, en une seule opération indivisible.
3. LE Système DOIT relier l'Encaissement de remplacement à l'original, dans les deux sens, et
   l'afficher dans l'historique : « remplace RECU-… », « remplacé par RECU-… ».
4. LE Système DOIT soumettre l'Encaissement de remplacement aux mêmes règles qu'un encaissement
   ordinaire : plafond, report, refus en totalité, année close.
5. SI le Remplacement est refusé par l'une de ces règles, ALORS LE Système NE DOIT annuler
   l'original ni rien écrire, et DOIT présenter le motif du refus.
6. QUAND seul le mode de paiement ou la note change, LE Système DOIT les corriger sans Remplacement,
   sans nouveau Numéro_Reçu, avec une Trace.
7. QUAND le Remplacement est confirmé, LE Système DOIT proposer l'impression du nouveau reçu.

### Requirement 4: Aperçu avant toute Correction

**User Story:** En tant qu'Administratrice, je veux voir ce qu'une correction change pour le parent
avant de la confirmer, afin de ne jamais découvrir après coup un montant inattendu.

#### Acceptance Criteria

1. AVANT toute Correction des exigences 2, 3, 5, 6, 8, 9 et 10, LE Système DOIT présenter un Aperçu
   indiquant, pour chaque Série dont un montant change : coût, montant dû à ce jour, montant versé,
   reste à payer et statut « à jour / en retard », avant et après.
2. L'Aperçu DOIT lister les autres effets : Imputations neutralisées, absences retirées, Séances
   devenues facturables ou non facturables, ventilation déplacée.
3. LE Système DOIT garantir que la Correction confirmée produit exactement les montants annoncés
   par l'Aperçu ; SI les données ont changé entre l'Aperçu et la confirmation, ALORS LE Système DOIT
   refuser la confirmation et présenter un nouvel Aperçu.
4. QUAND une Correction ne change aucun montant, L'Aperçu DOIT l'indiquer explicitement.

### Requirement 5: Date d'inscription réelle et corrigeable

**User Story:** En tant qu'Administratrice, je veux saisir la date réelle d'arrivée d'un Étudiant et
pouvoir la corriger, afin que ce qu'il doit corresponde à sa présence réelle.

#### Acceptance Criteria

1. QUAND une inscription est créée avec une Date_Inscription, LE Système DOIT la conserver ;
   sans date, LE Système DOIT retenir le jour même.
2. LE Système DOIT accepter une Date_Inscription future à l'intérieur de l'année scolaire courante.
3. SI la Date_Inscription est hors de l'année scolaire du Groupe, ALORS LE Système DOIT refuser
   l'inscription en nommant les bornes de l'année.
4. LE Système DOIT traiter la Date_Inscription comme une date calendaire : une Séance tenue le jour
   même de l'inscription concerne l'Étudiant, quelle que soit l'heure de la saisie.
5. LE Système DOIT permettre de corriger la Date_Inscription, avec un Motif et après Aperçu.
6. QUAND la correction **recule** la Date_Inscription, LE Système DOIT lister les absences devenues
   hors Fenêtre_Inscription et proposer de les retirer dans la même opération ; il DOIT aussi
   lister les présences ordinaires devenues hors fenêtre, qui restent facturables comme séances
   consommées, et demander confirmation explicite de ce maintien.
7. QUAND la correction **avance** la Date_Inscription, LE Système DOIT lister les Séances déjà
   validées devenues concernées et sans Présence pour l'Étudiant, et permettre de le noter présent
   ou absent à chacune dans la même opération ; une Séance laissée sans Présence DOIT être annoncée
   dans l'Aperçu comme facturable.
8. SI la liste confirmée par l'Administratrice diffère de la liste recalculée à la confirmation,
   ALORS LE Système DOIT refuser et présenter la liste à jour : rien n'est retiré sans avoir été vu.
9. QUAND une correction rend non facturable une Séance qui porte une ventilation, LE Système DOIT
   ventiler de nouveau ce montant sur les Séances facturables de la même Série, et l'annoncer dans
   l'Aperçu ; SI le montant versé dépasse alors le coût de la Série, ALORS l'Aperçu DOIT l'annoncer
   comme trop-perçu, et la Correction NE DOIT PAS le reporter ni le rembourser d'elle-même.

### Requirement 6: Date de sortie

**User Story:** En tant qu'Administratrice, je veux enregistrer la date de départ d'un Étudiant, afin
qu'il ne soit plus attendu après, sans perdre ses présences passées.

#### Acceptance Criteria

1. QUAND une inscription est clôturée, LE Système DOIT exiger une Date_Sortie, proposée au jour
   même, et un Motif, après Aperçu.
2. LE Système DOIT considérer l'Étudiant concerné par toutes les Séances de sa Fenêtre_Inscription,
   y compris après la clôture : une Séance antérieure à la Date_Sortie, validée plus tard, DOIT le
   faire figurer sur la Feuille_Appel.
3. SI une absence ou une présence ordinaire existe sur une Séance postérieure à la Date_Sortie,
   ALORS LE Système DOIT la lister et proposer de la retirer dans la même opération.
4. LE Système DOIT accepter une présence de rattrapage hors Fenêtre_Inscription, selon les règles
   de rattrapage.
5. LE Système DOIT permettre de corriger une Date_Sortie ou de rouvrir une inscription clôturée,
   avec un Motif et après Aperçu.

### Requirement 7: L'Étudiant hors fenêtre n'est pas concerné

**User Story:** En tant qu'Administratrice, je veux qu'un Étudiant ne soit jamais noté absent hors de
sa période d'inscription, afin qu'il en soit dispensé d'office.

#### Acceptance Criteria

1. LE Système DOIT construire la Feuille_Appel d'une Séance à partir des seuls Étudiants dont la
   Fenêtre_Inscription contient la date de la Séance.
2. LE Système NE DOIT PAS compléter une Feuille_Appel vide par les autres Étudiants du Groupe ;
   une Feuille_Appel vide DOIT l'expliquer.
3. SI une absence est soumise sur une Séance_Non_Concernée ou pour un Étudiant sans inscription au
   Groupe, ALORS LE Système DOIT la refuser côté serveur, en nommant l'Étudiant et sa
   Fenêtre_Inscription.
4. LE Système DOIT accepter une présence sur une Séance_Non_Concernée, comme rattrapage consommé.
5. QUAND une validation contient une absence refusée, LE Système DOIT refuser la validation entière
   et nommer chaque ligne ; l'écran DOIT permettre de retirer ces lignes en une action et de revalider.

### Requirement 8: Corriger une présence

**User Story:** En tant qu'Administratrice, je veux corriger la présence d'un seul Étudiant sur une
Séance validée, sans toucher aux autres.

#### Acceptance Criteria

1. LE Système DOIT permettre, avec un Motif et après Aperçu : passer présent ↔ absent, ajouter une
   Présence manquante, retirer une Présence.
2. QUAND une Présence passe à absent, LE Système DOIT permettre de fixer la justification dans la
   même action.
3. QUAND une Présence passe à présent, LE Système DOIT effacer la justification et l'inscrire dans
   la même Trace.
4. LE Système DOIT retirer une Présence par désactivation, jamais par suppression définitive.
5. SI la Correction ferait une absence sur une Séance_Non_Concernée, ALORS LE Système DOIT la refuser.
6. SI la Séance appartient à une Année_Close, ALORS LE Système DOIT refuser la Correction.

### Requirement 9: Corriger une présence de rattrapage

**User Story:** En tant qu'Administratrice, je veux retirer une présence de rattrapage saisie à tort,
afin qu'un Étudiant ne paie pas une Séance où il n'est pas venu.

#### Acceptance Criteria

1. LE Système DOIT permettre de retirer une présence de rattrapage, avec un Motif et après Aperçu.
2. QUAND une présence de rattrapage est retirée, LE Système DOIT rouvrir le droit au rattrapage de
   la Séance manquée qu'elle compensait, de sorte qu'elle puisse être rattrapée de nouveau.
3. LE Système DOIT conserver dans la Trace la Séance manquée et la décision « déjà payée » qui
   étaient portées par la présence retirée.

### Requirement 10: Dévalider une Séance

**User Story:** En tant qu'Administratrice, je veux dévalider une Séance validée par erreur en
sachant ce que je défais, afin de pouvoir l'expliquer ensuite.

#### Acceptance Criteria

1. QUAND une Séance est dévalidée, LE Système DOIT exiger un Motif, présenter un Aperçu, et écrire
   une Trace listant les Présences désactivées.
2. SI la Séance appartient à une Année_Close, ALORS LE Système DOIT refuser la validation et la
   dévalidation.

### Requirement 11: Motif et Trace

**User Story:** En tant qu'Administratrice, je veux motiver une correction en un geste et retrouver
toujours qui a changé quoi.

#### Acceptance Criteria

1. LE Système DOIT proposer, pour chaque type de Correction, une liste de Motif_Type : « Erreur de
   saisie », « Justificatif reçu », « Date d'arrivée corrigée », « Départ de l'étudiant »,
   « Encaissement sur le mauvais élève », « Montant mal saisi », « Autre ».
2. SI le Motif_Type est « Autre », ALORS LE Système DOIT exiger un texte libre, non vide après
   suppression des espaces ; tout texte libre est limité à 500 caractères.
3. LE Système DOIT refuser une Correction sans changement effectif.
4. LE Système DOIT attribuer chaque Trace à l'Administrateur authentifié, jamais à une valeur
   fournie par le client, et lui donner un rang strictement croissant.
5. LE Système DOIT écrire la Trace dans la même opération que la Correction : une Correction
   refusée ou échouée n'en laisse aucune.
6. LE Système DOIT conserver chaque Trace après la désactivation de la donnée corrigée.
7. LE Système DOIT réserver toute Correction au rôle ADMIN ; le rôle VIEWER DOIT recevoir 403.

### Requirement 12: Journal lisible et imprimable

**User Story:** En tant qu'Administratrice, je veux montrer à un parent qui conteste l'historique
des corrections de son enfant, en clair.

#### Acceptance Criteria

1. LE Système DOIT présenter, depuis la fiche d'un Étudiant, son Journal : Encaissements annulés et
   remplacés, corrections de présence, de dates, de rattrapage, de justification et de détail de
   paiement, du plus récent au plus ancien.
2. LE Système DOIT rédiger chaque entrée en français, sans identifiant technique : « Séance du
   14/01/2027 (Maths 1ère A) : absent → présent », « Reçu RECU-2027-0042 de 20 000,00 DA annulé,
   remplacé par RECU-2027-0043 de 2 000,00 DA ».
3. LE Système DOIT indiquer pour chaque entrée son effet sur le montant dû, quand il y en a un.
4. LE Système DOIT permettre d'imprimer le Journal d'un Étudiant, sur une période choisie.
5. LE Système DOIT rendre le Journal consultable pour une Année_Close.
