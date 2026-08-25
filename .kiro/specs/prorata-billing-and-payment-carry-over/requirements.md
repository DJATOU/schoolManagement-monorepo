# Requirements Document

## Introduction

Cette fonctionnalité formalise trois règles métier liées à l'arrivée d'un Étudiant dans un
Groupe dont des Séances sont déjà passées, et au traitement d'un versement qui dépasse le
montant dû de la Série en cours :

1. **Facturation au prorata** : seules les Séances postérieures à l'entrée de l'Étudiant dans
   le Groupe, ou celles auxquelles il a effectivement assisté, sont facturées.
2. **Plafonnement** : un versement ne peut être imputé sur la Série en cours qu'à hauteur du
   montant restant dû de cette Série.
3. **Report** : la part du versement qui dépasse ce plafond est imputée sur la Série suivante
   au lieu de devenir un trop-perçu.

Le code actuel est incohérent sur ce point : `StudentHistoryService.resolveBillableSessions`
applique déjà la règle du prorata (une Séance est facturable si elle est postérieure à
`student_groups.date_assigned` ou si l'Étudiant y a une présence active), alors que
`PaymentQuoteService` calcule le plafond encaissable à partir de `series.totalSessions` sans
tenir compte de la date d'inscription. Le plafond autorise donc l'encaissement de Séances que
la facturation ne reconnaît pas, ce qui produit un trop-perçu intégral. Aucun mécanisme de
report inter-Séries n'existe aujourd'hui.

Le périmètre couvre : le calcul des Séances facturables, le calcul du coût de Série au
prorata, le plafond exposé par le devis, le plafonnement de l'encaissement, le report du
surplus sur la Série suivante, la traçabilité du report, le reçu PDF et la cohérence des
revenus de Groupe.

## Glossary

- **Système** : l'application de gestion scolaire dans son ensemble (backend Spring Boot +
  frontend Angular), sauf lorsqu'un composant plus précis est nommé.
- **Étudiant** : un `StudentEntity` inscrit dans un ou plusieurs Groupes.
- **Groupe** : un `GroupEntity`, porteur du Prix_Séance.
- **Série** : un `SessionSeriesEntity` ; unité de facturation d'un Groupe, portant un nombre de
  Séances planifiées (`total_sessions`).
- **Séance** : un `SessionEntity` rattaché à une Série et daté.
- **Date_Inscription** : la date d'entrée de l'Étudiant dans le Groupe
  (`student_groups.date_assigned`).
- **Présence_Active** : un enregistrement d'assiduité non supprimé rattachant l'Étudiant à une
  Séance.
- **Présence_Rattrapage** : une Présence_Active dont l'indicateur `isCatchUp` vaut vrai.
- **Rattrapage_Seul** : état d'un Étudiant pour une Série lorsque toutes ses Présences_Actives
  de cette Série sont des Présences_Rattrapage (`isCatchUpOnly`).
- **Inscrit_Régulier** : état d'un Étudiant pour une Série lorsqu'il n'est pas Rattrapage_Seul.
- **Séance_Facturable** : une Séance de la Série retenue dans le calcul du coût pour un
  Étudiant donné, selon les règles de l'exigence 1.
- **Séance_Exclue** : une Séance de la Série écartée du calcul du coût pour un Étudiant donné.
- **Prix_Séance** : le prix unitaire d'une Séance, résolu par le Résolveur_Coût.
- **Taux_Exemption** : taux de réduction appliqué au montant dû, compris entre 0.00 et 1.00
  inclus ; la réduction s'applique en multipliant par (1 − Taux_Exemption).
- **Coût_Série_Prorata** : nombre de Séances_Facturables × Prix_Séance × (1 − Taux_Exemption).
  Remplace le `monthTotalCost` calculé sur `total_sessions`.
- **Montant_Dû_À_Ce_Jour** : nombre de Séances_Facturables assisties × Prix_Séance ×
  (1 − Taux_Exemption) (`amountDueSoFar`), seuil de retard.
- **Montant_Versé** : cumul enregistré dans `payments.amount_paid` pour le couple
  (Étudiant, Série).
- **Plafond_Encaissable** : montant maximum imputable sur une Série pour un Étudiant à un
  instant donné, exposé par le Service_Devis.
- **Surplus** : part d'un versement excédant le Plafond_Encaissable de la Série visée.
- **Série_Suivante** : pour une Série d'un Groupe, la Série du même Groupe dont l'identifiant
  est immédiatement supérieur.
- **Report** : imputation d'un Surplus sur la Série_Suivante.
- **Calculateur_Coût** : le composant pur `PaymentCostCalculator`.
- **Résolveur_Coût** : le composant `PaymentCostResolver`, qui résout Séances planifiées,
  Séances assisties, Prix_Séance et Taux_Exemption.
- **Service_Devis** : le composant `PaymentQuoteService`, source unique du Plafond_Encaissable.
- **Service_Encaissement** : le composant `PaymentProcessingService`.
- **Service_Ventilation** : le composant `PaymentDistributionService`, qui crée les
  `payment_detail`.
- **Service_Historique** : le composant `StudentHistoryService`.
- **Service_Report** : le composant chargé d'imputer un Surplus sur la Série_Suivante.
- **Service_Revenus_Groupe** : le composant `GroupRevenueService`.
- **Service_Reçu** : le composant `PaymentReceiptPdfService`.
- **Administrateur** : l'utilisateur qui enregistre les versements.
- **ÉCHELLE_MONÉTAIRE** : échelle de 2 décimales.
- **ARRONDI_MONÉTAIRE** : mode d'arrondi `HALF_UP`.

## Requirements

### Requirement 1: Détermination des Séances facturables

**User Story:** En tant qu'administrateur, je veux que seules les Séances concernant
réellement l'Étudiant soient retenues dans le calcul du coût d'une Série, afin de ne pas
facturer les Séances tenues avant son arrivée dans le Groupe.

#### Acceptance Criteria

1. QUAND le Système détermine les Séances_Facturables d'un Étudiant pour une Série, LE Système
   DOIT retenir chaque Séance de la Série dont la date est postérieure ou égale à la
   Date_Inscription de l'Étudiant dans le Groupe de la Série.
2. QUAND le Système détermine les Séances_Facturables d'un Étudiant pour une Série, LE Système
   DOIT retenir chaque Séance de la Série pour laquelle l'Étudiant possède une
   Présence_Active.
3. LE Système DOIT classer en Séance_Exclue chaque Séance dont la date est antérieure à la
   Date_Inscription et pour laquelle l'Étudiant ne possède aucune Présence_Active.
4. SI aucune Date_Inscription n'est enregistrée pour le couple (Étudiant, Groupe), ALORS LE
   Système DOIT retenir comme Séances_Facturables uniquement les Séances pour lesquelles
   l'Étudiant possède une Présence_Active.
5. LE Système DOIT retenir la même définition de Séance_Facturable dans le Service_Historique,
   le Résolveur_Coût, le Service_Devis et le Service_Ventilation.
6. QUAND une Séance est ajoutée à une Série avec une date postérieure ou égale à la
   Date_Inscription, LE Système DOIT l'inclure dans les Séances_Facturables de l'Étudiant.

### Requirement 2: Coût de Série au prorata

**User Story:** En tant qu'administrateur, je veux que le coût d'une Série soit calculé sur les
Séances facturables de l'Étudiant, afin que le montant annoncé corresponde au service rendu.

#### Acceptance Criteria

1. LE Calculateur_Coût DOIT calculer le Coût_Série_Prorata comme le nombre de
   Séances_Facturables multiplié par le Prix_Séance, puis multiplié par (1 − Taux_Exemption).
2. LE Calculateur_Coût DOIT calculer le Montant_Dû_À_Ce_Jour comme le nombre de
   Séances_Facturables auxquelles l'Étudiant a assisté, multiplié par le Prix_Séance, puis
   multiplié par (1 − Taux_Exemption).
3. LE Calculateur_Coût DOIT exprimer chaque montant en `BigDecimal` avec l'ÉCHELLE_MONÉTAIRE
   et l'ARRONDI_MONÉTAIRE.
4. LE Calculateur_Coût DOIT produire un Montant_Dû_À_Ce_Jour inférieur ou égal au
   Coût_Série_Prorata.
5. LE Calculateur_Coût DOIT produire un Coût_Série_Prorata inférieur ou égal au produit du
   nombre de Séances planifiées de la Série par le Prix_Séance multiplié par
   (1 − Taux_Exemption).
6. LÀ OÙ le Taux_Exemption vaut 1.00, LE Calculateur_Coût DOIT produire un Coût_Série_Prorata
   et un Montant_Dû_À_Ce_Jour égaux à zéro.
7. QUAND le nombre de Séances_Facturables vaut zéro, LE Calculateur_Coût DOIT produire un
   Coût_Série_Prorata et un Montant_Dû_À_Ce_Jour égaux à zéro.
8. SI le Prix_Séance est négatif, ou si le nombre de Séances_Facturables est négatif, ou si le
   Taux_Exemption est hors de l'intervalle 0.00 à 1.00, ALORS LE Calculateur_Coût DOIT rejeter
   l'entrée avec une erreur de validation.

### Requirement 3: Plafond encaissable cohérent avec le prorata

**User Story:** En tant qu'administrateur, je veux que le devis n'autorise jamais plus que ce
que la facturation reconnaît, afin d'éliminer les trop-perçus dus à une inscription tardive.

#### Acceptance Criteria

1. LÀ OÙ l'Étudiant est Inscrit_Régulier pour la Série, LE Service_Devis DOIT calculer le
   Plafond_Encaissable comme le Coût_Série_Prorata diminué du Montant_Versé.
2. LÀ OÙ l'Étudiant est Rattrapage_Seul pour la Série, LE Service_Devis DOIT calculer le
   Plafond_Encaissable comme le Montant_Dû_À_Ce_Jour diminué du Montant_Versé.
3. QUAND le calcul du Plafond_Encaissable produit une valeur négative, LE Service_Devis DOIT
   retourner un Plafond_Encaissable égal à zéro.
4. LE Service_Devis DOIT exposer dans le devis le Coût_Série_Prorata, le nombre de
   Séances_Facturables, le nombre de Séances_Exclues et le Montant_Versé.
5. QUAND le Montant_Versé d'une Série dépasse le Coût_Série_Prorata, LE Service_Devis DOIT
   retourner un Plafond_Encaissable égal à zéro et exposer l'écart entre le Montant_Versé et
   le Coût_Série_Prorata comme excédent existant.
6. QUAND le devis est calculé deux fois de suite sans modification des données de la Série, de
   l'assiduité, des versements et de l'inscription, LE Service_Devis DOIT produire des
   montants identiques.
7. SI le Service_Devis ne parvient pas à déterminer le Coût_Série_Prorata, le nombre de
   Séances_Facturables, le nombre de Séances_Exclues ou le Montant_Versé, ALORS LE
   Service_Devis DOIT retourner une erreur sans produire de devis.

### Requirement 4: Plafonnement du versement sur la Série en cours

**User Story:** En tant qu'administrateur, je veux qu'un versement s'arrête au montant dû de la
Série en cours, afin qu'aucune Série ne soit créditée au-delà de son coût.

#### Acceptance Criteria

1. QUAND un versement d'un montant inférieur ou égal au Plafond_Encaissable de la Série est
   enregistré, LE Service_Encaissement DOIT imputer la totalité du versement sur cette Série
   et fixer le Surplus à zéro.
2. QUAND un versement d'un montant supérieur au Plafond_Encaissable de la Série est enregistré,
   LE Service_Encaissement DOIT imputer sur cette Série un montant égal au
   Plafond_Encaissable et qualifier la différence comme Surplus.
3. LE Service_Encaissement DOIT vérifier que la somme du montant imputé sur la Série et du
   Surplus est égale au montant du versement enregistré.
4. LE Service_Encaissement DOIT incrémenter le `payments.amount_paid` de la Série du seul
   montant imputé sur cette Série.
5. LE Service_Ventilation DOIT ventiler le montant imputé sur les seules Séances_Facturables
   de l'Étudiant pour cette Série.
6. SI le montant du versement enregistré est inférieur ou égal à zéro, ALORS LE
   Service_Encaissement DOIT rejeter le versement avec une erreur de validation.
7. LE Service_Encaissement DOIT exprimer le montant imputé et le Surplus en `BigDecimal` avec
   l'ÉCHELLE_MONÉTAIRE et l'ARRONDI_MONÉTAIRE.
8. LE Service_Encaissement DOIT considérer un versement comme traité seulement après
   l'achèvement de la ventilation du montant imputé par le Service_Ventilation.
9. SI la ventilation du montant imputé échoue, ALORS LE Service_Encaissement DOIT annuler
   l'enregistrement du versement et retourner une erreur.

### Requirement 5: Report du Surplus sur la Série suivante

**User Story:** En tant qu'administrateur, je veux que la part excédentaire d'un versement
serve à payer la Série suivante, afin que l'argent reçu ne devienne pas un trop-perçu.

#### Acceptance Criteria

1. QUAND un versement produit un Surplus supérieur à zéro et qu'une Série_Suivante existe pour
   le Groupe, LE Service_Report DOIT imputer sur la Série_Suivante un montant égal au plus
   petit du Surplus et du Plafond_Encaissable de la Série_Suivante.
2. LE Service_Report DOIT identifier la Série_Suivante comme la Série du même Groupe dont
   l'identifiant est immédiatement supérieur à celui de la Série en cours.
3. QUAND le Service_Report impute un montant sur la Série_Suivante, LE Système DOIT incrémenter
   le `payments.amount_paid` de la Série_Suivante de ce montant et ventiler ce montant sur les
   Séances_Facturables de l'Étudiant pour la Série_Suivante.
4. LE Service_Report DOIT calculer le Plafond_Encaissable de la Série_Suivante en appliquant
   les règles de l'exigence 3.
5. SI l'imputation sur la Série_Suivante échoue pour une raison quelconque, ALORS LE Système
   DOIT annuler l'enregistrement du versement dans son ensemble, y compris le montant déjà
   imputé sur la Série en cours, et retourner une erreur.
6. LE Système DOIT traiter l'enregistrement d'un versement, sa ventilation et le Report associé
   dans une seule transaction.
7. LE Système DOIT laisser le Montant_Versé de la Série en cours et celui de la Série_Suivante
   inchangés lorsque l'annulation décrite en 5.5 s'applique.

8. LE Service_Report DOIT considérer une Série_Suivante comme apte à recevoir un Report
   uniquement si elle comporte au moins une Séance_Facturable pour l'Étudiant. Une Série
   existante mais dépourvue de Séances ne peut rien accueillir.
9. QUAND le Surplus dépasse le Plafond_Encaissable de la Série_Suivante, LE Service_Report DOIT
   poursuivre le Report sur les Séries suivantes par identifiant croissant jusqu'à épuisement du
   Surplus.
10. LE Service_Report DOIT appliquer le Report sans étape de confirmation distincte de la
    validation du versement.
11. QUAND une part du Surplus ne peut être imputée sur aucune Série, LE Système DOIT refuser le
    versement **dans sa totalité**, y compris la part qui aurait été imputable.
12. QUAND un versement est refusé faute de Série apte à recevoir le Surplus, LE Système DOIT
    indiquer le montant maximal encaissable et l'action corrective à effectuer, à savoir créer
    les Séances de la Série suivante pour l'ouvrir.

> **Justification du refus total (critère 5.11)** : un encaissement partiel ferait diverger
> l'argent physiquement reçu du montant enregistré. L'administrateur conserverait la différence
> en main sans aucune trace dans le Système. Le refus total l'oblige à décider avant que tout
> enregistrement ait lieu. L'Exigence 9.3 rend ce refus évitable en annonçant le maximum
> encaissable avant la saisie.

### Requirement 6: Traçabilité du Report

**User Story:** En tant qu'administrateur, je veux voir d'où vient et où va chaque montant
reporté, afin de pouvoir justifier un encaissement lors d'un contrôle.

#### Acceptance Criteria

1. QUAND un Report est effectué, LE Système DOIT enregistrer le montant reporté, la Série
   source, la Série destination, la date du versement d'origine et l'identifiant de l'Étudiant.
2. QUAND l'historique de paiement d'un Étudiant est consulté, LE Système DOIT restituer chaque
   Report avec son montant, sa Série source et sa Série destination.
3. QUAND un versement produisant un Report est enregistré, LE Système DOIT restituer à
   l'appelant le montant imputé sur la Série en cours et le montant reporté.
4. LE Système DOIT distinguer dans l'historique un montant imputé directement sur une Série
   d'un montant reçu par Report.

### Requirement 7: Reçu de versement

**User Story:** En tant qu'administrateur, je veux que le reçu remis à la famille indique le
coût réellement facturé et la part reportée, afin d'éviter toute contestation.

#### Acceptance Criteria

1. QUAND un reçu est généré pour un versement, LE Service_Reçu DOIT afficher le
   Coût_Série_Prorata de la Série en cours et le nombre de Séances_Facturables retenues.
2. QUAND un versement produit un Report supérieur à zéro, LE Service_Reçu DOIT afficher le
   montant imputé sur la Série en cours, le montant reporté et le nom de la Série destination.
3. QUAND un reçu est généré, LE Service_Reçu DOIT afficher un reste à payer égal au
   Coût_Série_Prorata diminué du Montant_Versé de la Série en cours.
4. QUAND un versement ne produit aucun Report, LE Service_Reçu DOIT afficher un montant reporté
   égal à zéro.
5. LE Service_Reçu DOIT afficher le montant reçu égal à la somme du montant imputé sur la
   Série en cours et du montant reporté.

### Requirement 8: Cohérence des revenus de Groupe

**User Story:** En tant qu'administrateur, je veux que les revenus par Série restent justes
après un Report, afin que le suivi financier du Groupe reste fiable.

#### Acceptance Criteria

1. LE Service_Revenus_Groupe DOIT comptabiliser un montant reporté dans les encaissements de
   la Série destination.
2. LE Service_Revenus_Groupe DOIT exclure un montant reporté des encaissements de la Série
   source.
3. LE Service_Revenus_Groupe DOIT classer un montant reporté hors du trop-perçu.
4. LE Service_Revenus_Groupe DOIT produire une somme des montants imputés par Série égale au
   total des versements enregistrés pour le Groupe.
5. LE Service_Revenus_Groupe DOIT continuer à présenter séparément le reste à payer et le
   trop-perçu.

### Requirement 9: Restitution à l'interface d'administration

**User Story:** En tant qu'administrateur, je veux comprendre à l'écran pourquoi le montant dû
est inférieur au coût complet de la Série, afin d'expliquer le calcul à la famille.

#### Acceptance Criteria

1. QUAND le formulaire de versement d'un Étudiant est affiché, LE Système DOIT afficher le
   Coût_Série_Prorata, le nombre de Séances_Facturables et le nombre de Séances_Exclues de la
   Série en cours.
2. QUAND une Série comporte au moins une Séance_Exclue pour l'Étudiant, LE Système DOIT
   afficher le motif d'exclusion « Séance antérieure à l'inscription ».
3. QUAND le montant saisi par l'Administrateur dépasse le Plafond_Encaissable de la Série en
   cours, LE Système DOIT afficher le montant qui sera imputé sur la Série en cours et le
   montant qui sera reporté avant validation.
4. LE Système DOIT afficher les montants monétaires avec l'ÉCHELLE_MONÉTAIRE.

### Requirement 10: Signalement d'un changement de Groupe

**User Story:** En tant qu'administrateur, je veux être averti lorsqu'un Étudiant change de
Groupe, afin de pouvoir ajuster manuellement sa facturation sur le mois concerné.

Cette exigence est la contrepartie de la décision d'unité de facturation (voir « Décisions
tranchées ») : l'agrégation automatique entre Groupes est abandonnée, donc le changement de Groupe
ne doit pas devenir invisible.

**Ce qui est détecté est un changement de Groupe, et non la simple appartenance à plusieurs
Groupes.** Un Étudiant suivant plusieurs matières est inscrit simultanément dans plusieurs
Groupes : c'est la situation normale, et la signaler produirait une alerte permanente que
l'Administrateur cesserait de lire.

#### Acceptance Criteria

1. LE Système DOIT définir un Changement_Groupe comme la clôture d'une inscription de l'Étudiant
   dans un Groupe et l'ouverture d'une inscription dans un autre Groupe, les deux événements
   tombant dans le même mois civil.
2. QUAND un Changement_Groupe est détecté, LE Système DOIT le signaler à l'Administrateur.
3. LE Système DOIT présenter dans le signalement le mois civil concerné, le Groupe quitté, le
   Groupe rejoint et le nombre de Séances suivies dans chacun sur ce mois.
4. LE Système NE DOIT PAS émettre de signalement lorsque l'Étudiant possède plusieurs
   inscriptions simultanément actives sans clôture d'inscription.
5. LE Système DOIT afficher le signalement sur la fiche de l'Étudiant et sur le formulaire de
   versement de cet Étudiant.
6. LE Système DOIT laisser la facturation inchangée lorsqu'un signalement est émis : le
   Coût_Série_Prorata de chaque Série reste calculé indépendamment.
7. LE Système NE DOIT PAS bloquer l'enregistrement d'un versement en raison d'un signalement.

### Requirement 11: Lisibilité du prorata dans l'historique

**User Story:** En tant qu'administrateur, je veux comprendre à la lecture de l'historique
pourquoi un Étudiant est à jour avec un montant inférieur au coût nominal de la Série, afin de ne
pas le croire en retard à tort.

#### Acceptance Criteria

1. LE Système DOIT calculer le statut de paiement d'une Série contre le Coût_Série_Prorata, et
   jamais contre le produit du nombre de Séances planifiées par le Prix_Séance.
2. QUAND le Montant_Versé atteint le Coût_Série_Prorata, LE Système DOIT présenter la Série comme
   soldée, même si ce montant est inférieur au coût nominal de la Série.
3. QUAND l'historique de paiement ou l'historique complet d'un Étudiant est consulté, LE Système
   DOIT afficher chaque Séance_Exclue en indiquant que l'Étudiant n'y était pas présent et
   qu'elle n'est pas facturée.
4. LE Système DOIT distinguer visuellement une Séance_Exclue d'une Séance_Facturable impayée : la
   première n'est pas une dette.
5. QUAND une Séance antérieure à la Date_Inscription est facturée parce que l'Étudiant y a
   assisté en Présence_Rattrapage, LE Système DOIT l'identifier comme rattrapage dans
   l'historique.
6. LE Système DOIT afficher le Coût_Série_Prorata et le nombre de Séances_Facturables dans le
   récapitulatif de Série de l'historique.

## Décisions tranchées

### Unité de facturation : la Série

**Question** : le décompte des Séances facturables et le seuil de retard sont-ils bornés à une
Série, ou à une plage de dates couvrant un mois civil et plusieurs Groupes ?

**Décision** : **la Série est l'unité de facturation** (option A). Le décompte des Séances
suivies reste borné à la Série. Aucune agrégation automatique entre Groupes sur un mois civil
n'est effectuée.

**Éléments qui ont motivé la décision** :

- « Une Série = un mois » est **faux dans les données** : un même Groupe compte couramment deux à
  trois Séries dans un même mois civil (par exemple trois Séries en novembre 2026 pour le groupe
  Math 1ère B). L'unité « mois » ne correspond donc à aucune entité.
- Paiements, reçus, relevés de Groupe et devis sont **tous indexés par Série**. Introduire une
  granularité « mois civil » aurait créé une seconde échelle sans support en base.
- Le code bornait déjà le décompte à la Série
  (`AttendanceRepository.countPresentForStudentAndSeries`), en contradiction avec la version
  antérieure de `business-rules.md`. La décision tranche en faveur du code et
  `.kiro/steering/business-rules.md` a été corrigé en conséquence.

**Contrepartie obligatoire** : le changement de Groupe en cours de mois est traité
administrativement, mais ne doit pas devenir invisible. D'où l'Exigence 10.

**Conséquences sur les autres exigences** :

- Exigence 2.2 : le Montant_Dû_À_Ce_Jour est calculé sur les Séances_Facturables de la Série
  seule.
- Exigence 5.2 : la Série_Suivante est la Série d'identifiant immédiatement supérieur, **même si
  elle appartient au même mois civil**. Un surplus peut donc être reporté à l'intérieur d'un même
  mois.

### Décisions de détail

Toutes tranchées à partir de cas concrets. Aucune ne reste ouverte.

| Sujet | Décision | Exigence portant la règle |
|---|---|---|
| **Absence de Série apte** | Refus du versement **en totalité**, avec le montant maximal encaissable et l'action corrective : créer les Séances de la Série suivante pour l'ouvrir. Ni création anticipée de Série, ni crédit flottant | 5.11, 5.12 |
| **Aptitude d'une Série à recevoir** | Une Série doit comporter au moins une Séance_Facturable. Son existence seule ne suffit pas | 5.8 |
| **Séance passée assistée en rattrapage** | Facturée sur **sa** Série d'origine, et identifiée comme rattrapage dans l'historique | 1.2, 11.5 |
| **Cascade du Report** | Report sur les Séries suivantes par identifiant croissant jusqu'à épuisement du Surplus, sans limite de profondeur | 5.9 |
| **Automatisme du Report** | Automatique, sans étape de confirmation distincte. L'aperçu de répartition avant validation suffit | 5.10, 9.3 |
| **Exemption partielle** | Une Série exemptée donne un Plafond_Encaissable nul : elle est sautée et le Report continue. L'Étudiant reste redevable des Séries non exemptées | 3.1, 5.9 |
| **Sort du crédit reporté** | Sans objet : le Report est immédiat et imputé sur une Série réelle, aucun crédit flottant n'existe | — |
| **Mention sur le reçu** | Le reçu nomme explicitement la ou les Séries destinataires | 7.2 |
| **Données existantes** | Aucune reprise. L'excédent des Séries historiquement sur-encaissées reste affiché comme excédent existant | 3.5 |
| **Périmètre du signalement** | Un **changement de Groupe** (inscription clôturée puis autre ouverte le même mois civil), et non la simple appartenance à plusieurs Groupes | 10.1, 10.4 |

### Note sur le statut de paiement

La décision du prorata a une conséquence qui déborde le calcul : **le statut de paiement doit être
évalué contre le Coût_Série_Prorata**. Un Étudiant arrivé en cours de Série et ayant réglé ses
Séances facturables est **à jour et soldé**, même si le montant versé est très inférieur au coût
nominal de la Série. Sans cette règle, il apparaîtrait indéfiniment en retard. Elle est portée par
l'Exigence 11.1 et 11.2, et par la section « Prorata » de
`.kiro/steering/business-rules.md`.
