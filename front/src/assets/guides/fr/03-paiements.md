# Paiements

## Où encaisser

On encaisse **depuis la fiche de l'élève** :

1. Barre du haut : onglet **Élèves**, tapez son nom, ouvrez sa fiche.
2. Cliquez sur **Ajouter un paiement**.

Le menu **Gestion Financière › Paiements** sert à rechercher et à contrôler les versements (filtres, export CSV, historique) et à rembourser. On n'y encaisse pas.

> [!NOTE]
> Sur une année passée, ou pour un compte Consultation, **Ajouter un paiement** est grisé.

<!-- page -->

## La fenêtre « Ajouter un paiement »

1. Choisissez le **Groupe**, puis la **Série de sessions**. Une série soldée est grisée.
2. Lisez le récapitulatif : « Série : 4 séance(s) × 800 DA = 3 200 DA », puis le déjà versé et le reste à payer.
3. Saisissez le **Montant payé**, ou cochez **Paiement intégral de la série**.
4. Choisissez la **Méthode de paiement** : Espèces, Chèque, Carte bancaire ou Autre.
5. Cliquez sur **Confirmer**, relisez le récapitulatif, puis confirmez.

Le reçu **RECU-AAAA-NNNN** part à l'impression.

<!-- page -->

## Ce que coûte la série

**Coût de la série = séances facturables × prix d'une séance**, moins la réduction de l'élève s'il en a une.

Un élève arrivé en cours de série **ne paie pas les séances passées** auxquelles il n'a pas assisté. La fenêtre l'annonce : « 2 séance(s) non facturée(s) à cet étudiant », avec le motif « Séance antérieure à l'inscription ».

> [!TIP]
> Série de 4 séances à 800 DA. Inès s'inscrit juste avant la 3ᵉ séance : elle paie 2 × 800 = **1 600 DA**, et non 3 200 DA.

<!-- page -->

## À jour ou en retard

Le statut compare ce que l'élève a versé à ce qu'il a **déjà suivi** :

- **dû à ce jour = séances suivies × prix d'une séance** ;
- **En retard** si le versé est inférieur au dû à ce jour, sinon **À jour**.

Un élève peut donc payer séance par séance et rester à jour. Une absence n'augmente jamais le dû à ce jour.

> [!TIP]
> Séance à 800 DA. Lucas a suivi 2 séances (1 600 DA) et versé 1 600 DA : il est **À jour**, même si la série coûte 3 200 DA. Après sa 3ᵉ séance suivie, il est **En retard** tant qu'il n'a pas versé 2 400 DA.

<!-- page -->

## Le report du surplus

Si le montant dépasse ce que doit la série choisie, le surplus passe **tout seul** sur les séries suivantes du groupe. La fenêtre le montre avant la confirmation, dans **Répartition du versement** : « Imputé sur … », « Reporté sur … ».

> [!TIP]
> Il reste 1 600 DA à payer sur la série d'octobre et l'élève verse 3 200 DA : 1 600 DA soldent octobre, 1 600 DA sont reportés sur la série suivante.

> [!WARNING]
> Si aucune série suivante n'a de séances, le versement est **refusé en entier**, avec le maximum encaissable. Créez d'abord les séances de la série suivante, ou ramenez le montant à ce maximum.

<!-- page -->

## Les reçus

Chaque versement a son reçu : montant reçu, élève, groupe, série, mode de règlement, **situation de la série** (coût, séances facturées, total versé, reste à payer) et répartition du versement.

Pour le réimprimer : fiche de l'élève, bloc **Versements**, bouton **Réimprimer le reçu**.

Un reçu annulé porte la mention **ANNULÉ** et, s'il a été remplacé, le numéro du nouveau reçu.

> [!NOTE]
> Le reçu ne couvre que le versement qu'il mentionne : il ne vaut pas quittance pour toute la série.

<!-- page -->

## Corriger un versement

Fiche de l'élève, bloc **Versements** :

- **Annuler** : choisissez le motif, **Voir l'aperçu**, puis **Confirmer**. Le reçu est annulé.
- **Corriger** : changer le montant, l'élève, le groupe ou la série crée un **nouveau reçu** qui remplace l'ancien. Changer seulement le mode de paiement ou la note garde le même reçu.

L'aperçu montre, avant et après : coût de la série, dû à ce jour, versé, reste à payer et statut.

> [!NOTE]
> Un versement déjà remboursé ne se corrige pas : l'écran nomme le remboursement en cause.

<!-- page -->

## Rembourser

Menu **Paiements** : sur la ligne du versement, bouton **Enregistrer un remboursement**.

1. Lisez le **Montant versé**, le **Déjà remboursé** et le **Plafond remboursable**.
2. Saisissez le **Montant à rembourser** et le **Motif du remboursement**. Le motif est obligatoire : il figure sur le reçu.
3. Cliquez sur **Rembourser**, puis sur **Confirmer et rembourser**.

Le reçu de remboursement **REMB-AAAA-NNNN** s'imprime ; une réimpression porte **DUPLICATA n°…**.

> [!WARNING]
> Un remboursement ne s'annule pas dans l'application.

<!-- page -->

## Les réductions

Menu **Gestion Financière › Réductions**, puis **Ajouter une réduction** : l'élève, la portée (**Groupe**, **Série** ou **Séance**) et le taux. À 100 %, c'est une **Exemption**.

La réduction baisse le prix de la séance, et la fenêtre de paiement l'annonce :

> « Réduction de 20 % : 640 DA la séance au lieu de 800 DA »

**Modifier le taux** ou **Supprimer la réduction** recalcule les montants dus.

<!-- page -->

## Le journal des corrections

Sur la fiche de l'élève, le **Journal des corrections** liste chaque correction : la date, ce qui a changé, l'effet sur le dû, le motif et son auteur. Il couvre versements, inscriptions, présences, justifications et rattrapages.

Choisissez les dates (**Du**, **Au**, **Afficher**), puis **Imprimer** pour répondre à une famille qui conteste.

> [!NOTE]
> Rien ne se corrige sans motif : c'est ce qui permet de tout expliquer plus tard.
