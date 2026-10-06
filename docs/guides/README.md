# Guides utilisateur

Les guides ont rejoint l'application : `front/src/assets/guides/`, un dossier par langue
(`fr`, `en`, `ar`), affichés sur la page **Guide d'utilisation** (`/guide`, icône livre de la barre
du haut).

L'ancien `paie-des-enseignants.md` est devenu le chapitre
`front/src/assets/guides/fr/04-paie-des-enseignants.md`.

Écrire ou modifier un guide :

- un fichier Markdown par chapitre, déclaré dans le `manifest.json` de sa langue ;
- `<!-- page -->` sépare deux pages du livre ;
- encadrés : `> [!TIP]` (exemple), `> [!NOTE]` (à savoir), `> [!WARNING]` (attention) ;
- lien vers un chapitre : `[Paiements](#paiements)` (identifiant du manifeste) ;
- les trois langues gardent les mêmes chapitres et le même nombre de pages, et chaque page doit
  tenir dans le format : `user-guide-content.spec.ts` le vérifie.
