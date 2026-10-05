# Requirements Document

## Introduction

Le propriétaire produit demande une page pour payer les enseignants : « après la fin de la série,
on paie l'enseignant en fonction de combien la série dans son groupe a encaissé, et l'admin prend
aussi un pourcentage, paramétrable ». La rémunération n'est pas un salaire : c'est une **part de ce
qui a réellement été encaissé**. Un élève qui ne paie pas fait donc baisser la part de l'enseignant.

### Décisions du propriétaire produit (tranchées)

1. **Base** : l'Encaissé_Net de la Série, versements moins remboursements. Un impayé la diminue.
2. **Deux parts seulement** : l'Enseignant et l'École. Pas de frais ni de troisième bénéficiaire.
3. **Le taux est un catalogue** que l'Administrateur crée et entretient, comme les tarifs. Il le
   choisit au moment de chaque Paie.
4. **Fin de série** : toutes les Séances de la Série sont validées. Le paiement est **déclenché par
   l'Administrateur**, jamais automatiquement.
5. **Une Paie versée n'est jamais modifiée.** L'argent arrivé ou rendu après coup donne lieu à une
   Régularisation. Les erreurs restent corrigeables, par une Correction tracée, « pour la souplesse ».
6. **Les remplaçants sont ignorés pour l'instant** : l'Enseignant du Groupe reçoit toute la Part.

### Hors périmètre

- Remplaçant payé au prorata des séances assurées ; partage entre plusieurs enseignants.
- Salaire fixe, prime, charges sociales, retenues autres que la Régularisation.
- Virement bancaire, paiement automatique, échéancier.
- Toute modification des règles de facturation des élèves : la Paie lit l'Encaissé_Net, elle ne
  le calcule pas autrement que le relevé de recettes du Groupe.

## Glossary

- **Administrateur** : utilisateur de rôle ADMIN. Seul rôle autorisé sur cette fonctionnalité,
  lecture comprise.
- **Enseignant** : l'enseignant rattaché au Groupe de la Série (`groups.teacher_id`) au moment de
  la Paie.
- **Série_Terminée** : Série comptant au moins une Séance active, dont toutes les Séances actives
  sont validées. Une Séance désactivée n'est ni comptée, ni attendue.
- **Encaissé_Net** : pour une Série, somme des versements non annulés de tous les élèves
  (rattrapages compris) moins les remboursements actifs. Même définition que le relevé de recettes
  du Groupe.
- **Taux_Rémunération** : entrée du catalogue : un libellé et un Pourcentage_Enseignant,
  strictement compris entre 0 et 100, à deux décimales.
- **Part_Enseignant** : Encaissé_Net × Pourcentage_Enseignant / 100, arrondi au centime
  (demi supérieur).
- **Part_École** : Encaissé_Net − Part_Enseignant. Les deux parts somment exactement la base.
- **Paie** : versement enregistré à un Enseignant pour une Série. Porte un Numéro_Paie, sa date, son
  auteur, et une copie figée de sa base, du taux et des deux parts.
- **Numéro_Paie** : `PAIE-AAAA-NNNN`, attribué par le serveur, jamais réutilisé.
- **Paie_Initiale** : première Paie d'une Série. Au plus une Paie_Initiale active par Série.
- **Régularisation** : Paie complémentaire d'une Série déjà payée, sur l'écart d'Encaissé_Net
  apparu depuis. Positive : « Complément ». Négative : « Retenue ».
- **Écart_À_Régulariser** : (Encaissé_Net actuel × Pourcentage de la Paie_Initiale / 100) − somme
  des Parts_Enseignant des Paies actives de la Série.
- **Bordereau** : document imprimé d'une Paie, signé par l'Administrateur et l'Enseignant.
- **Correction**, **Motif**, **Aperçu**, **Trace** : au sens de la spec `admin-corrections`.

## Requirements

### Requirement 1: Catalogue des taux

**User Story:** En tant qu'Administratrice, je veux définir les taux que j'applique aux
enseignants, afin de choisir le bon au moment de payer sans le retaper.

#### Acceptance Criteria

1. LE Système DOIT permettre de créer un Taux_Rémunération avec un libellé et un
   Pourcentage_Enseignant.
2. LE Système DOIT refuser un pourcentage hors de l'intervalle ouvert ]0 ; 100[, ou à plus de deux
   décimales, en nommant la borne.
3. LE Système DOIT refuser deux taux actifs de même libellé, casse et espaces de bord ignorés.
4. LE Système DOIT afficher, pour chaque taux, la Part_École correspondante (100 − pourcentage).
5. QUAND un taux est désactivé, LE Système NE DOIT PLUS le proposer pour une nouvelle Paie.
6. LE Système NE DOIT modifier aucune Paie existante quand un taux est modifié ou désactivé : la
   Paie porte une copie figée du libellé et du pourcentage.

### Requirement 2: Séries à payer

**User Story:** En tant qu'Administratrice, je veux voir quelles séries sont terminées et pas
encore payées, afin de ne pas oublier un enseignant ni le payer deux fois.

#### Acceptance Criteria

1. LE Système DOIT lister les Séries_Terminées sans Paie_Initiale active, avec pour chacune :
   Groupe, Série, Enseignant, nombre de Séances, Encaissé_Net, et l'encaissé brut et le remboursé
   qui le composent.
2. LE Système DOIT lister à part les Séries payées dont l'Écart_À_Régulariser n'est pas nul.
3. LE Système NE DOIT PAS proposer de payer une Série non terminée ; s'il l'affiche, il DOIT dire
   combien de Séances restent à valider.
4. QUAND le Groupe n'a pas d'Enseignant, LE Système DOIT le dire et refuser la Paie.
5. LE Système DOIT permettre de filtrer par Enseignant et par Groupe.

### Requirement 3: Calcul des parts

**User Story:** En tant qu'Administratrice, je veux voir le calcul en clair avant de payer, afin
de l'expliquer à l'enseignant.

#### Acceptance Criteria

1. LE Système DOIT calculer la Part_Enseignant et la Part_École en `BigDecimal`, à deux
   décimales, la Part_École étant la différence exacte.
2. LE Système DOIT afficher le calcul : « 72 000,00 DA × 60 % = 43 200,00 DA ; école
   28 800,00 DA ».
3. LE Système DOIT lire l'Encaissé_Net à la même source que le relevé de recettes du Groupe : les
   deux écrans NE DOIVENT jamais annoncer deux montants pour la même Série.
4. LE Système DOIT refuser une Paie dont l'Encaissé_Net est nul ou négatif : il n'y a rien à
   partager.

### Requirement 4: Enregistrer une Paie

**User Story:** En tant qu'Administratrice, je veux enregistrer la paie d'une série terminée en
choisissant le taux, afin d'en garder une pièce.

#### Acceptance Criteria

1. QUAND l'Administratrice choisit un taux actif, LE Système DOIT afficher un Aperçu des deux
   parts avant toute écriture.
2. QUAND elle confirme, LE Système DOIT enregistrer la Paie avec un Numéro_Paie, la date et
   l'auteur fixés par le serveur, et la copie figée de l'Enseignant, du Groupe, de la Série, de
   l'Encaissé_Net, du libellé et du pourcentage, et des deux parts.
3. LE Système DOIT refuser la confirmation si l'Encaissé_Net, le taux ou l'état de la Série a
   changé depuis l'Aperçu, et présenter le nouvel Aperçu.
4. LE Système DOIT garantir au plus une Paie_Initiale active par Série, y compris sous deux
   confirmations simultanées.
5. LE Système DOIT refuser, en nommant la cause : Série non terminée, Groupe sans Enseignant, taux
   inactif, Encaissé_Net nul, Série déjà payée.
6. Un Numéro_Paie NE DOIT PAS être consommé par une Paie refusée ou un Aperçu.

### Requirement 5: Bordereau de paie

**User Story:** En tant qu'Administratrice, je veux imprimer un bordereau signé, afin d'attester
la remise de l'argent des deux côtés.

#### Acceptance Criteria

1. LE Système DOIT proposer l'impression du Bordereau après l'enregistrement d'une Paie.
2. LE Bordereau DOIT porter : Numéro_Paie, date, Enseignant, Groupe, Série, Encaissé_Net, libellé
   et pourcentage du taux, Part_Enseignant, Part_École, auteur, et deux lignes de signature.
3. LE Système DOIT permettre de réimprimer un Bordereau ; à partir de la deuxième impression, il
   DOIT porter la mention « DUPLICATA » et son rang.
4. Un Bordereau de Paie annulée DOIT porter la mention « ANNULÉE » et le Numéro_Paie qui la
   remplace, s'il existe.
5. Une Régularisation négative DOIT s'intituler « Retenue » et son montant se lire comme une somme
   due par l'Enseignant.

### Requirement 6: Argent arrivé ou rendu après la Paie

**User Story:** En tant qu'Administratrice, je veux que l'argent encaissé ou remboursé après la
paie soit rattrapé sur une paie suivante, sans toucher à celle déjà versée.

#### Acceptance Criteria

1. LE Système NE DOIT JAMAIS modifier les montants d'une Paie enregistrée.
2. LE Système DOIT calculer l'Écart_À_Régulariser de chaque Série payée avec le pourcentage figé
   de sa Paie_Initiale.
3. QUAND l'Administratrice enregistre une Régularisation, LE Système DOIT en faire une Paie à part
   entière (Numéro_Paie, Aperçu, Bordereau), de montant égal à l'Écart_À_Régulariser, rattachée à
   la Paie_Initiale.
4. LE Système DOIT permettre une Régularisation négative (Retenue) ; il NE DOIT jamais en créer
   une sans action de l'Administratrice.
5. LE Système DOIT refuser une Régularisation dont l'écart est nul.

### Requirement 7: Corriger une Paie

**User Story:** En tant qu'Administratrice, je veux pouvoir annuler ou refaire une paie saisie
par erreur, afin de garder de la souplesse sans perdre la trace de ce qui a été versé.

#### Acceptance Criteria

1. LE Système DOIT permettre d'annuler une Paie active avec un Motif, après Aperçu ; la Paie reste
   lisible, marquée annulée, avec la date, l'auteur et le Motif.
2. LE Système DOIT permettre de remplacer une Paie active par une nouvelle Paie (autre taux) : la
   Paie d'origine est annulée et désigne sa remplaçante, qui reçoit un nouveau Numéro_Paie.
3. LE Système NE DOIT corriger que la Paie active la plus récente d'une Série ; sinon il DOIT
   nommer la Paie à corriger d'abord.
4. Chaque Correction DOIT laisser une Trace immuable : valeur avant, valeur après, auteur,
   horodatage, Motif, et l'effet sur les deux parts.
5. QUAND la Paie_Initiale est annulée sans remplacement, la Série DOIT redevenir « à payer ».

### Requirement 8: Cohérence avec les séances

**User Story:** En tant qu'Administratrice, je ne veux pas qu'une série payée redevienne non
terminée sans que je le sache.

#### Acceptance Criteria

1. QUAND une Séance d'une Série portant une Paie active serait dévalidée, LE Système DOIT refuser
   en nommant la Paie à annuler d'abord.
2. LE Système DOIT appliquer la même règle à la suppression ou la désactivation d'une Séance
   d'une telle Série.

### Requirement 9: Consultation

**User Story:** En tant qu'Administratrice, je veux retrouver ce que j'ai versé à un enseignant,
afin de lui répondre s'il conteste.

#### Acceptance Criteria

1. LE Système DOIT offrir une page « Paie des enseignants » en trois onglets : À payer, Paies
   versées, Taux.
2. L'onglet Paies versées DOIT filtrer par Enseignant, Groupe, période et statut, et totaliser les
   Parts_Enseignant et les Parts_École du filtre.
3. La fiche Enseignant DOIT lister ses Paies avec le total versé, et permettre de réimprimer un
   Bordereau.
4. Toutes les routes de lecture et d'écriture DOIVENT être réservées au rôle ADMIN.
5. Les montants DOIVENT s'afficher au format de la langue active.

### Requirement 10: Année scolaire close

**User Story:** En tant qu'Administratrice, je veux payer la dernière série de l'année après sa
clôture.

#### Acceptance Criteria

1. LE Système DOIT permettre une Paie, une Régularisation et une Correction de Paie sur une Série
   d'une année close : payer l'enseignant ne modifie aucune donnée pédagogique de l'année.
2. LE Système DOIT continuer de refuser, sur une année close, toute écriture sur les Séances et
   les présences, Règle 8 comprise.
