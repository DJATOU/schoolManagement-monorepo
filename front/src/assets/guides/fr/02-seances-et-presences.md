# Séances et présences

## La série, unité de facturation

Les séances d'un groupe sont rangées en **séries**. Une série compte le nombre de séances prévu pour le groupe, par exemple 4. C'est la série qu'on paie, et c'est sur elle qu'on calcule ce que doit un élève.

Vous ne créez pas les séries : quand la série en cours est pleine, la séance suivante en ouvre une nouvelle, toute seule. Son nom suit le modèle **Groupe - MM-AAAA-NNN**.

> [!TIP]
> « Anglais 1 AS - 10-2026-001 » : première série du groupe Anglais 1 AS commencée en octobre 2026.

> [!NOTE]
> Un mois peut contenir deux ou trois séries d'un même groupe : une série n'est pas un mois.

<!-- page -->

## Créer une séance

Menu **Suivi Académique › Sessions**.

1. **Détails de la session** : un titre et le **Type de session** (Cours, Exercices, Examen, Révision, Autre).
2. **Planification** : la **Date de début** et l'**Heure de début**. La fin, deux heures plus tard, est proposée toute seule.
3. **Identifiants** : le **Groupe**, la **Salle**, l'**Enseignant**. L'enseignant est pré-rempli d'après le groupe.
4. Cliquez sur **Enregistrer la session**.

La séance rejoint la série en cours du groupe.

<!-- page -->

## Répéter une séance

Onglet **Répétition** du même formulaire :

1. Cochez **Répéter cette séance**.
2. Choisissez les **Jours de la semaine**.
3. Indiquez **Répéter jusqu'au** (date incluse).
4. Cliquez sur **Calculer les séances** : l'écran annonce combien seront créées.
5. Cliquez sur **Enregistrer la session**.

> [!WARNING]
> Si la salle ou l'enseignant est déjà pris sur un créneau, toute la répétition est refusée. Cochez **Ignorer les créneaux déjà occupés** pour créer les autres séances et n'écarter que celles-là.

<!-- page -->

## Le calendrier

Menu **Suivi Académique › Calendrier**. Vues **Mois**, **Semaine**, **Jour** et **Liste** ; **Aujourd'hui** revient à la date du jour.

Cliquez sur une séance pour ouvrir sa fenêtre :

- onglet **Détails de la séance** : groupe, salle, enseignant, date, horaire ;
- onglet **Présences** : la feuille d'appel.

En bas : **Modifier la séance**, **Valider la séance**, **Imprimer la présence**, **Supprimer**, **Fermer**.

<!-- page -->

## Faire l'appel

Dans l'onglet **Présences**, chaque élève inscrit à la date de la séance a sa ligne :

- case cochée : **présent** ; case décochée : **absent** ;
- **Justifié** : pour une absence excusée ;
- **Ajouter une note** : un mot libre.

**Tout cocher** marque toute la classe présente. Le bouton **+** ajoute un élève d'un autre groupe, venu en rattrapage.

Cliquez enfin sur **Valider la séance**. Une fenêtre propose d'**Imprimer le PDF** de la feuille de présence.

<!-- page -->

## Valider : pas avant l'heure

Une séance se valide **à partir de son heure de début**. Avant, le bouton est grisé et l'écran l'explique :

> « Séance pas encore commencée : validation possible à partir du 12/10/2026 à 18:00. »

Une séance validée compte : ses présences entrent dans ce que doivent les élèves, et sa série peut être payée à l'enseignant. La valider d'avance fausserait ces calculs.

> [!WARNING]
> Une absence notée pour un élève qui n'était pas inscrit ce jour-là bloque la validation, et l'écran nomme ces lignes. Cliquez sur **Retirer ces lignes**, puis validez de nouveau.

<!-- page -->

## Corriger une séance validée

Une fois la séance validée, la feuille ne se coche plus : chaque ligne se corrige seule, avec le bouton **Corriger** (icône crayon) :

- présent : **Noter absent**, **Noter absent (justifié)**, **Retirer la ligne** ;
- absent : **Noter présent**, **Modifier la justification…**, **Retirer la ligne** ;
- élève sans ligne : **Ajouter : présent**, **absent** ou **absent (justifié)**.

Chaque correction demande un **Motif**, puis **Voir l'aperçu** (« Ce qui va changer » : coût, dû, statut), puis **Confirmer**. Elle est inscrite au journal des corrections.

<!-- page -->

## Dévalider une séance

**Dévalider la séance** retire toutes ses lignes de présence : elle redevient à valider. Choisissez le motif, lisez l'aperçu, puis confirmez.

Une séance validée ne se supprime pas : dévalidez-la d'abord, puis cliquez sur **Supprimer**.

> [!WARNING]
> Si sa série a déjà été payée à l'enseignant, la séance ne se dévalide pas : annulez d'abord la paie (voir [Paie des enseignants](#paie-enseignants)).

<!-- page -->

## Absence justifiée : aucun effet sur l'argent

La justification sert au suivi de l'élève et à son droit au rattrapage. Elle **ne change aucun montant** :

- **aucune absence n'augmente le montant dû à ce jour**, justifiée ou non : seules les séances suivies le font ;
- **toute absence après l'inscription reste comptée dans le coût de la série** : la place était réservée.

Pour changer une justification après validation : **Corriger › Modifier la justification…**. L'historique garde qui a changé quoi, et quand.

<!-- page -->

## Les rattrapages

Un élève qui a manqué une séance peut la rattraper dans un autre groupe :

- menu **Suivi Académique › Rattrapages**, puis **Nouvelle demande de rattrapage** ;
- ou bouton **+** sur la feuille de la séance d'accueil.

L'application décide seule de la facturation :

- l'élève a sa place dans un autre groupe de **même niveau et même matière** : le rattrapage est « **À préciser** » ;
- sinon : « **Facturée sur place** » ; le groupe d'accueil la facture comme à un membre.

<!-- page -->

## Rattrapages à préciser

Menu **Suivi Académique › Rattrapages à préciser**. Sur chaque ligne, cliquez sur **Préciser** :

1. **Quelle séance est rattrapée ?** Choisissez la séance manquée.
2. **Cette séance était-elle déjà payée ?** **Déjà payée — ne pas refacturer**, ou **À facturer**.
3. Cliquez sur **Enregistrer la décision**.

Aucune réponse n'est cochée d'avance : c'est à vous de trancher. D'ici là, le rattrapage ne facture rien et n'est pas une dette.

> [!TIP]
> Passez sur cette liste chaque semaine : une séance suivie que personne ne facture est un oubli.
