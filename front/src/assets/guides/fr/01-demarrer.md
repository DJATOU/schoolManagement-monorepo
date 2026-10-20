# Démarrer

## Se connecter

1. Saisissez votre **Identifiant** et votre **Mot de passe**.
2. Cliquez sur **Se connecter**. Le **Tableau de bord** s'ouvre.

Pour quitter : cliquez sur votre nom en haut à droite, puis sur **Déconnexion**.

## Deux rôles

| Rôle | Ce qu'il peut faire |
|---|---|
| **Administrateur** | tout : inscrire, encaisser, valider, corriger, payer les enseignants |
| **Consultation** | tout voir, rien modifier |

> [!NOTE]
> Pour un compte **Consultation**, les boutons qui modifient sont grisés : « Action réservée aux administrateurs ». Les **Recettes** et la **Paie des enseignants** ne lui sont pas proposées.

<!-- page -->

## La barre du haut

- **☰** ouvre ou ferme le menu de gauche.
- Le **logo** ramène au tableau de bord.
- **Élèves**, **Groupes**, **Professeurs** : choisissez ce que vous cherchez, puis tapez au moins deux lettres dans la recherche.
- L'icône **livre** ouvre ce guide.
- **Année scolaire** : l'année affichée partout.
- **Votre nom** : mode sombre ou clair, langue (**Français**, **English**, **العربية**), **Gestion des comptes**, **Déconnexion**.

> [!TIP]
> Pour ouvrir la fiche de Lucas Lefebvre : onglet **Élèves**, tapez « Luc », puis choisissez « Lucas Lefebvre » dans les suggestions.

<!-- page -->

## Le menu de gauche

Quatre rubriques. Une seule reste ouverte à la fois.

- **Inscription** : Étudiants, Enseignants, Groupes, Import CSV.
- **Gestion Financière** : Paiements, Réductions, Recettes, Paie des enseignants.
- **Suivi Académique** : Sessions, Calendrier, Rattrapages, Rattrapages à préciser, Fin d'année / Passage.
- **Ressources Académiques** : Niveaux, Salles, Matières, Types de Groupes, Tarification.

> [!NOTE]
> Les chapitres suivants suivent ce menu : [Séances et présences](#seances), [Paiements](#paiements), [Paie des enseignants](#paie-enseignants).

<!-- page -->

## L'année scolaire

Le sélecteur **Année scolaire** choisit l'année que vous consultez. L'année en cours porte la mention **(courante)**.

Une **année passée** s'ouvre en **lecture seule** : un bandeau le rappelle, et rien ne peut être modifié. Tout reste consultable : élèves, séances, paiements, reçus.

> [!WARNING]
> Avant d'inscrire, d'encaisser ou de valider, vérifiez que l'année **courante** est sélectionnée. Sinon, les boutons restent grisés.

<!-- page -->

## Le tableau de bord

Il s'ouvre après la connexion. Les cartes donnent les chiffres de l'année choisie :

- **Étudiants actifs** et **Enseignants** ;
- **Groupes en activité** : ceux qui ont des élèves, « sur N créés » ;
- **Nouveaux inscrits**, **Sortants (total)**, **Rattrapages**.

En dessous : les **Sessions**, les **Présences** et la **Répartition par genre**.

Choisissez la période : **Année**, **Mois** ou **Période**. **Imprimer** produit un PDF.

> [!NOTE]
> **Année** couvre l'année scolaire, inscriptions de l'été comprises, et non l'année civile.

<!-- page -->

## Préparer l'école

Pour démarrer vite, chargez vos listes avec **Inscription › Import CSV**, dans cet ordre :

1. Niveaux, Matières, Salles, Types de groupe
2. Tarifs (le prix d'une séance)
3. Enseignants, puis Élèves (dateOfBirth facultative, AAAA-MM-JJ, colonne en **Texte** dans Excel)
4. Groupes : ils citent un niveau, une matière, un enseignant et un tarif déjà importés
5. Inscriptions : un élève, un groupe et sa date d'arrivée par ligne

Chaque fichier commence par une ligne d'en-tête. L'écran affiche ensuite « Importés : N — Erreurs : M », et la raison de chaque ligne refusée.

> [!NOTE]
> Un enseignant déjà présent est refusé, pour ne pas créer de doublon. Deux élèves peuvent porter le même nom : ils sont acceptés.

<!-- page -->

## Importer les inscriptions

Le fichier des inscriptions place les élèves dans leurs groupes, comme **Ajouter un étudiant** sur la fiche du groupe, avec les mêmes contrôles : niveau, année scolaire, inscription déjà en cours.

- Une ligne par inscription : un élève suivi dans deux groupes a deux lignes.
- Le groupe est cherché par son nom, dans l'année **courante**.
- La **date d'arrivée** est obligatoire (AAAA-MM-JJ). Les séances à partir de ce jour sont dues ; celles d'avant ne concernent pas l'élève.
- Deux élèves du même nom : ajoutez leur téléphone (colonne phoneNumber) pour désigner le bon ; un téléphone qui ne correspond pas donne un avertissement.

> [!NOTE]
> L'import crée seulement les inscriptions : aucune présence, absence ni paiement.
