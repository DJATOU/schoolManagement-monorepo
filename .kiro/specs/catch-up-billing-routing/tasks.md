# Plan d'implémentation — Routage et traçabilité de la facturation des rattrapages

## Vue d'ensemble

Ce plan referme à sa source le défaut qui a produit la facturation erronée : sur l'écran de
validation d'une séance, ajouter un étudiant hors groupe le marque **silencieusement** rattrapage
(`session-modal.component.ts`, `isCatchUp: !isGroupMember`), et le lien vers la séance manquée
**ne peut structurellement pas être transmis** — `AttendanceDTO` n'a pas le champ, et
`AttendanceMapper` ne mappe pas `missedSession`. `CatchUpBillingQualifier.qualify(null, …)` retombe
alors sur `CONSOMME` et le groupe d'accueil facture, sans que personne ne l'ait décidé.

La correction est un **routage automatique en deux cas**, décidé côté serveur :

- **Cas 1 — vrai rattrapage** : l'étudiant appartient à un groupe de **même niveau ET même
  matière** que le groupe de la séance d'accueil. La présence est créée `PENDING` (visible, ne
  facture rien, ne bloque pas le pointage des autres). Sa complétion exige **deux** décisions :
  quelle séance manquée, et **déjà payée ?** — choix explicite, **sans valeur par défaut**.
  La facturation reste ancrée à la séance manquée dans son groupe d'origine.
- **Cas 2 — pas un rattrapage** : aucun groupe de même niveau+matière. La séance est facturée
  directement au groupe d'accueil, comme pour un membre. Aucune séance manquée n'est demandée :
  aucune place n'était réservée ailleurs. L'administrateur n'est interrogé sur rien.

### Décisions actées (ne pas les rouvrir en cours d'implémentation)

- **Le test déterminant est `level_id` + `subject_id`**, et **jamais** `group_type_id`.
  `group_types` désigne l'effectif (petit / moyen / grand / individuel), pas la paire
  niveau+matière. Les deux notions doivent porter des noms distincts dans le code : toute
  confusion route un étudiant dans le mauvais cas, donc facture au mauvais groupe.
- Le test est **borné à l'année scolaire du groupe d'accueil** : un groupe de l'année précédente
  n'ouvre aucun droit au rattrapage.
- Le test **accepte une inscription inactive** : un étudiant ayant quitté un groupe reste
  débiteur, règle déjà admise pour l'encaissement (`requireEnrolmentOrCatchUp`).
- **La qualification par dates de `CatchUpBillingQualifier.qualify()` n'est pas modifiée.** Le
  repli vers `CONSOMME` (« facturer plutôt que perdre silencieusement une recette ») est un choix
  délibéré en faveur de l'école et reste en place. Ce plan supprime les entrées `missedSession`
  nulles ; il ne change pas la façon dont elles étaient traitées.
- Le **lien vers la séance manquée est obligatoire en Cas 1 uniquement**. En Cas 2 son absence est
  légitime et voulue, pas un oubli.
- La **série reste l'unité de facturation** : la décision est portée par le rattrapage, les
  montants continuent d'être résolus par série via `BillableSessionsResolver`.

### Classes sous seuil JaCoCo 100 % (lignes + branches)

`BillableSessionsResolver*`, `CatchUpBillingQualifier*`, `CatchUpService`, `PaymentStatusService`,
`RefundService`. Toute branche ajoutée ou retirée dans ces classes **doit** être couverte par un
test, dans la même tâche. Aucune branche défensive inatteignable : un `default:` ou un garde qu'un
test ne peut pas atteindre fait échouer le seuil. `AttendanceService`, `AttendanceMapper` et
`AttendanceDTO` ne sont pas sous seuil.

### Conventions de build (IMPORTANT)

- Backend en **Java 21**, toujours depuis `back/` : `bash build.sh clean verify`.
  Lancé depuis la racine, `build.sh` échoue avec un code de sortie 0 (`./mvnw` introuvable) —
  l'échec passe inaperçu.
- Frontend : `cd front && npm test -- --watch=false --browsers=ChromeHeadless`.
- Ne pas renommer le dossier `persistance`. Mapping DTO ↔ entité via `MappingContext`.
  Contrôleurs minces. Commentaires et messages français préservés. Un service par entité côté
  frontend. i18n FR + EN à parité. Montants en `BigDecimal` échelle 2 `HALF_UP`.

## Tâches

- [x] 1. Poser le test « même niveau + même matière » (socle du routage)
  - [x] 1.1 Ajouter la requête du test au `StudentGroupRepository`
    - `@Query` renvoyant un booléen : existe-t-il une inscription de l'étudiant à un groupe
      dont `level_id` ET `subject_id` égalent ceux du groupe d'accueil, dans la **même année
      scolaire**, en excluant le groupe d'accueil lui-même (`og.id <> :hostGroupId`) ?
    - **Ne pas** filtrer sur `active` : une inscription inactive compte (décision actée).
    - **Ne pas** faire intervenir `group_type_id`.
    - _Exigences : routage Cas 1/Cas 2, bornage année scolaire, inscription inactive_

  - [x] 1.2 Créer le service de routage (`CatchUpRoutingService`)
    - Une méthode `route(studentId, hostGroupId)` renvoyant un verdict à deux valeurs
      (`TRUE_CATCH_UP` / `HOST_BILLED`), appuyée sur 1.1.
    - Nommage explicite : ce service décide **si c'est un rattrapage** (niveau+matière). Il ne
      doit pas être confondu avec `CatchUpService.isCompatible()`, qui décide **où** un
      rattrapage est autorisé (année + `group_type` + prix). Documenter la distinction dans le
      Javadoc des deux méthodes.
    - _Exigences : routage automatique, séparation des deux notions de « type »_

  - [x] 1.3 Écrire le property test du routage niveau+matière
    - **Property 1 : le routage ne dépend que de (niveau, matière, année)** — pour tout étudiant
      et tout groupe d'accueil, le verdict est `TRUE_CATCH_UP` si et seulement s'il existe une
      inscription à un autre groupe de même niveau, même matière et même année ; faire varier
      arbitrairement le `group_type`, le prix, l'enseignant, la salle et l'état actif/inactif de
      l'inscription ne change **jamais** le verdict.
    - Commentaire : `Feature: catch-up-billing-routing, Property 1: Le routage ne dépend que du niveau, de la matière et de l'année scolaire`.
    - `@Property(tries = 100)`, H2, groupes et inscriptions générés.
    - **Validates : test déterminant, bornage année, inscription inactive, indépendance au group_type**

- [x] 2. Introduire l'état de facturation du rattrapage sur la présence
  - [x] 2.1 Créer l'enum `CatchUpBillingState` (`persistance`)
    - Trois valeurs commentées en français : `PENDING` (à préciser, ne facture rien),
      `RESOLVED` (séance manquée et décision « déjà payée » renseignées), `HOST_BILLED`
      (Cas 2 : facturée sur place, aucune séance manquée attendue).
    - _Exigences : état visible, Cas 2 légitime_

  - [x] 2.2 Ajouter le champ à `AttendanceEntity` + migration Flyway
    - Colonne `catch_up_billing_state` (`@Enumerated(STRING)`, nullable pour les présences
      ordinaires qui ne sont pas des rattrapages) et colonne de la décision
      `missed_session_already_paid` (`Boolean`, **nullable** — `null` signifie « non tranché »,
      c'est ce qui rend l'absence de défaut représentable).
    - Script Flyway versionné dans `back/src/main/resources/db/migration`, sans toucher aux
      scripts déjà appliqués (`validate-on-migrate` est actif).
    - Ne pas renommer le dossier `persistance`.
    - _Exigences : PENDING inerte, choix explicite sans défaut_

  - [x] 2.3 Écrire le test de migration et de valeurs par défaut
    - Une présence ordinaire reste avec un état nul ; les colonnes existantes ne sont pas
      modifiées par la migration.
    - _Exigences : non-régression du schéma_

- [x] 3. Refermer le trou : transmettre le lien et l'état de bout en bout
  - [x] 3.1 Étendre `AttendanceDTO`
    - Ajouter `missedSessionId`, `catchUpBillingState`, `missedSessionAlreadyPaid`.
    - _Exigences : le lien doit pouvoir être transmis_

  - [x] 3.2 Mapper `missedSession` dans `AttendanceMapper`
    - Résolution `missedSessionId` → `SessionEntity` via `MappingContext` (motif du projet,
      **pas** `ApplicationContextProvider`), sur le modèle de `idToSession`.
    - Sans ce mapping, le lien reste nul quoi qu'envoie le client : c'est l'un des trois
      verrous qui produisaient le défaut.
    - _Exigences : le lien doit pouvoir être transmis_

  - [x] 3.3 Router dans `AttendanceService.saveAll`
    - Pour toute présence marquée rattrapage, appeler `CatchUpRoutingService` :
      Cas 1 → `PENDING` et lien exigé à la complétion ; Cas 2 → `HOST_BILLED`, aucune séance
      manquée demandée.
    - Conserver `normalizeCatchUpFlag` : un membre du groupe d'accueil n'est jamais un
      rattrapage, contrôle appliqué **avant** le routage.
    - Le client ne décide plus : il envoie la présence, le serveur classe.
    - _Exigences : routage côté serveur, pointage non bloquant_

  - [x] 3.4 Écrire le property test « aucun rattrapage n'est créé sans classement »
    - **Property 2 : tout rattrapage enregistré porte un état de facturation** — pour toute
      soumission en masse contenant des présences de rattrapage, chaque présence persistée avec
      `isCatchUp = true` a un état non nul, et un lien vers la séance manquée nul **si et
      seulement si** son état est `HOST_BILLED`.
    - Commentaire : `Feature: catch-up-billing-routing, Property 2: Tout rattrapage enregistré porte un état de facturation`.
    - `@Property(tries = 100)`, H2, soumissions générées.
    - **Validates : lien obligatoire en Cas 1 uniquement, plus de NULL silencieux**

- [x] 4. Rendre `PENDING` inerte dans la facturation
  - [x] 4.1 Ajouter la branche `PENDING` à `BillableSessionsResolverImpl`
    - Une présence `PENDING` rend la séance **ni facturable ni écartée** : elle n'entre ni dans
      `billable()` ni dans `excluded()`, et n'alimente pas `attendedCount`.
    - Ne pas la traiter comme « écartée » : une séance écartée est une décision prise
      (« déjà payée ailleurs ») que l'interface annonce comme telle. Une séance en attente n'est
      pas décidée — les confondre reproduirait la classe de défaut déjà corrigée dans
      `statusLabel`.
    - **`qualify()` n'est pas touché.**
    - ⚠ **JaCoCo 100 % lignes+branches** : les deux côtés de la branche doivent être couverts,
      et l'état `PENDING` doit être atteignable en test — aucun garde inatteignable.
    - _Exigences : PENDING ne facture rien_

  - [x] 4.2 Écrire le property test de neutralité de `PENDING`
    - **Property 3 : un rattrapage en attente est neutre sur les montants** — pour toute série,
      ajouter, retirer ou faire varier des présences `PENDING` laisse identiques le coût au
      prorata, le montant dû à ce jour, le plafond encaissable et le statut de paiement.
    - Commentaire : `Feature: catch-up-billing-routing, Property 3: Un rattrapage en attente est neutre sur les montants`.
    - `@Property(tries = 100)`, H2, séries et présences générées.
    - **Validates : PENDING inerte, série comme unité de facturation**

- [x] 5. Complétion : quelle séance manquée + déjà payée
  - [x] 5.1 Remplacer le véto « séance non payée » par une information dans `CatchUpService.create`
    - Le blocage actuel (400 « La séance manquée n'est pas payée ») est retiré : l'état de
      paiement de la série d'origine est **relayé** à l'appelant pour être affiché à côté du
      choix, jamais opposé à l'administrateur. Le droit au rattrapage explicitement révoqué
      (`catchUpRight == FALSE`) continue, lui, de bloquer.
    - ⚠ **`CatchUpService` est sous JaCoCo 100 %** : retirer cette branche impose de mettre ses
      tests existants à jour dans la même tâche.
    - _Exigences : l'administrateur n'est jamais acculé_

  - [x] 5.2 Implémenter la résolution d'un rattrapage `PENDING`
    - Exige les deux décisions : `missedSessionId` (obligatoire) et `missedSessionAlreadyPaid`
      (obligatoire, **aucun défaut** — l'absence de décision laisse l'état `PENDING`).
      Passe l'état à `RESOLVED`.
    - Réutiliser les invariants déjà portés par `CatchUpService.complete()` — résolution de la
      série, unicité du rattrapage par séance manquée (409), série obligatoire — en les
      extrayant dans un point unique appelé par les deux chemins. **Aucune logique de
      facturation dupliquée** : les montants restent résolus par `BillableSessionsResolver`, qui
      lit la décision stockée.
    - _Exigences : choix explicite sans défaut, réutilisation des chemins existants_

  - [x] 5.3 Restreindre `getEligibleAbsences` aux groupes de même niveau+matière
    - Le sélecteur de séance manquée ne propose que les absences non résolues des groupes
      passant le test de la tâche 1. Proposer une absence d'un autre niveau ou d'une autre
      matière permettrait de créer un lien que le modèle interdit.
    - _Exigences : sélecteur cohérent avec le test déterminant_

  - [x] 5.4 Écrire le property test « la décision stockée gouverne, la date ne décide plus seule »
    - **Property 4 : la décision « déjà payée » est respectée telle qu'elle a été prise** — pour
      tout rattrapage `RESOLVED`, « déjà payée » ⇒ la séance d'accueil n'est pas facturée et la
      séance manquée reste facturée dans sa série d'origine ; « à facturer » ⇒ la séance
      d'accueil est facturée ; dans les deux cas le résultat est **indépendant de l'ordre
      d'évaluation** et ne consulte ni le montant versé ni le statut de paiement de la série
      d'origine au moment du calcul.
    - Commentaire : `Feature: catch-up-billing-routing, Property 4: La décision « déjà payée » est respectée telle qu'elle a été prise`.
    - `@Property(tries = 100)`, H2.
    - **Validates : ancrage à la séance manquée, pas de double facturation, aucune récursion entre séries**

  - [x] 5.5 Écrire le property test d'unicité du rattrapage par séance manquée
    - **Property 5 : une séance manquée n'est rattrapée qu'une fois** — pour toute séance
      manquée, une seconde résolution active vers cette même séance est refusée (409) et l'état
      existant reste inchangé.
    - Commentaire : `Feature: catch-up-billing-routing, Property 5: Une séance manquée n'est rattrapée qu'une fois`.
    - `@Property(tries = 100)`, H2.
    - **Validates : décompte des séances suivies déterminé**

- [x] 6. Traçabilité et correction après coup
  - [x] 6.1 Créer le journal d'audit du rattrapage
    - Table et entité sur le modèle **exact** de `attendance_justification_audit` : valeur avant,
      valeur après, auteur, horodatage, commentaire. Le journal **survit à la suppression** de la
      présence auditée — c'est après la disparition d'une donnée qu'on a besoin de savoir qui l'a
      modifiée. Migration Flyway.
    - _Exigences : trace complète, correction traçable_

  - [x] 6.2 Implémenter la correction d'un rattrapage résolu
    - Point d'entrée réservé à l'ADMIN permettant de changer la séance manquée ou la décision
      « déjà payée », chaque changement écrivant une entrée d'audit. Les invariants de 5.2
      s'appliquent à la correction.
    - _Exigences : l'administrateur peut corriger son erreur_

  - [x] 6.3 Écrire le property test de traçabilité des corrections
    - **Property 6 : toute correction laisse une trace immuable** — pour toute suite de
      corrections, le journal contient une entrée par changement effectif, avec valeur avant et
      après, auteur et horodatage ; aucune entrée n'est modifiée ni supprimée par une correction
      ultérieure, et les entrées subsistent après suppression de la présence auditée.
    - Commentaire : `Feature: catch-up-billing-routing, Property 6: Toute correction laisse une trace immuable`.
    - `@Property(tries = 100)`, H2.
    - **Validates : trace obligatoire, auditabilité**

- [x] 7. Checkpoint — build et tests backend (Java 21)
  - Depuis `back/` : `bash build.sh clean verify`. Vérifier que le seuil JaCoCo 100 % est tenu sur
    `BillableSessionsResolver*`, `CatchUpBillingQualifier*` et `CatchUpService`. Poser une question
    à l'utilisateur en cas de souci.

- [x] 8. Exposer le routage et la complétion par l'API
  - [x] 8.1 Contrôleur mince pour les rattrapages en attente
    - Liste des rattrapages `PENDING` (étudiant, groupe d'accueil, séance, date), résolution, et
      correction. Logique dans les services, contrôleur mince. Écritures réservées à l'ADMIN par
      la chaîne de sécurité existante.
    - Reprendre le motif d'enrichissement de `CatchUpService.getAllRequests()` : résoudre les
      libellés LAZY **dans** la transaction.
    - _Exigences : liste des rattrapages à préciser_

  - [x] 8.2 Relayer l'état et les mentions dans `SessionHistoryDTO`
    - Exposer l'état de facturation et, en Cas 2, le motif « facturée sur place — aucun groupe de
      même niveau et même matière ». Les mentions de rattrapage existantes
      (`billedInOriginSeries`, `caughtUpElsewhere`) sont conservées.
    - _Exigences : le cas est visible à l'écran_

- [x] 9. Frontend — pointage non bloquant et complétion
  - [x] 9.1 Retirer la décision du `session-modal` et afficher « à préciser »
    - Le composant n'écrit plus `isCatchUp: !isGroupMember` comme un verdict : il soumet la
      présence et affiche l'état renvoyé par le serveur. Un rattrapage `PENDING` porte un badge
      visible et **ne bloque pas** la validation de la séance pour les autres étudiants.
    - _Exigences : routage serveur, pointage rapide préservé_

  - [x] 9.2 Implémenter l'écran de complétion
    - Sélecteur de séance manquée alimenté par `getEligibleAbsences` (déjà filtré en 5.3), et
      choix « Déjà payée, ne pas refacturer » / « À facturer » **sans présélection**. L'état de
      paiement de la série d'origine est affiché à titre d'information, sans empêcher la
      validation. Bouton d'enregistrement inactif tant que les deux décisions ne sont pas prises.
    - _Exigences : choix explicite sans défaut, information et non blocage_

  - [x] 9.3 Implémenter la liste des rattrapages à préciser
    - Vue listant les `PENDING` avec accès direct à la complétion, et un service dédié
      (un service par entité, gestion d'erreur centralisée selon le motif de
      `payment.service.ts`).
    - _Exigences : le cas est visible et corrigeable_

  - [x] 9.4 Afficher les mentions dans l'historique et le reçu
    - Ligne d'un rattrapage en attente : « à préciser — non facturée pour l'instant », distincte
      de « non facturée » (décidée) et de « non payée » (dette). Cas 2 : « facturée sur place ».
    - Réutiliser `shared/session-billing.ts` afin que les trois vues (fenêtre de paiement,
      historique complet, PDF) tranchent identiquement.
    - _Exigences : ne pas rejouer la confusion d'étiquettes déjà corrigée_

  - [x] 9.5 Écrire les tests frontend de la complétion et du badge
    - Aucune présélection du choix « déjà payée » ; enregistrement impossible tant que les deux
      décisions manquent ; badge « à préciser » présent sans bloquer la validation.
    - _Exigences : choix explicite sans défaut_

- [x] 10. Ajouter les traductions i18n FR + EN
  - Clés des nouveaux libellés (état à préciser, écran de complétion, choix « déjà payée »,
    mention Cas 2, liste des rattrapages en attente, messages d'audit) dans `fr.json` et `en.json`.
  - Ne pas traduire les commentaires et messages français existants.
  - _Exigences : i18n FR+EN à parité_

  - [x] 10.1 Vérifier la parité des clés FR/EN
    - `fr.json` et `en.json` possèdent exactement les mêmes clés (le test de parité existant
      couvre ce point).
    - _Exigences : parité i18n_

- [x] 11. Correction des données existantes — rapport d'abord, jamais d'UPDATE aveugle
  - [x] 11.1 Produire le rapport en lecture seule
    - Pour chaque présence `is_catch_up = true AND active AND missed_session_id IS NULL` :
      étudiant, séance et groupe d'accueil **avec niveau et matière**, ses groupes d'inscription
      **avec niveau et matière**, verdict du test de la tâche 1, montant déjà encaissé sur la
      séance, et séances manquées candidates.
    - Afficher niveau et matière **des deux côtés** est le but du rapport : c'est ce qui permet de
      distinguer un vrai Cas 2 d'une inscription manquante. Sur les données actuelles, les
      4 enregistrements (Sirine 10, Malak 66, Rania 68, Younes 126) ressortent en Cas 2 sans
      changement de facturation — Sirine est inscrite en 1er année/Math et a assisté à
      2eme année/Physique. À confirmer par le propriétaire produit avant toute écriture.
    - **Aucune écriture dans cette tâche.**
    - _Exigences : correction appliquée en connaissance de cause_

  - [x] 11.2 ABANDONNÉE — aucune migration de données (enregistrements de test)
    - Décision du propriétaire produit, après lecture du rapport 11.1 : les 4 enregistrements sont
      des données de test, et le déploiement (Mini PC) part d'une base vide. Corriger des lignes
      qui n'atteindront jamais la production n'apporte rien.
    - Le rapport 11.1 a rempli son rôle : il a confirmé que le test niveau + matière classe
      correctement les 4 en Cas 2, sans changement de montant. C'est le **modèle** qui est ainsi
      validé, et la correction des lignes jetables n'y ajoutait rien.
    - La correctness de la feature repose sur les property tests et sur ce rapport, non sur la
      réparation de données temporaires.
    - Le script `back/reports/catch-up-billing-audit-report.sql` reste disponible : il est
      ré-exécutable si le cas se présentait sur une base réelle.
    - _Exigences : pas d'UPDATE aveugle sur des lignes portant de l'argent — respectée par
      l'abandon même de l'écriture_

<!-- Contenu d'origine de 11.2, conservé pour mémoire si le cas survenait sur une base réelle :
    - Poser l'état `HOST_BILLED` et le libellé de trace sur les seuls enregistrements validés par
      le propriétaire produit. Aucun montant modifié : la facturation sur place est déjà correcte
      pour ces lignes. Script Flyway idempotent, ciblant des identifiants explicites plutôt qu'un
      prédicat large.
    - Ne pas lancer cette tâche avant la validation explicite du rapport 11.1.
-->

- [x] 12. Étendre la couverture JaCoCo aux nouvelles classes
  - Ajouter aux `includes` de `jacoco-check` (`back/pom.xml`) `CatchUpRoutingService` et le service
    de résolution/correction des rattrapages, en excluant le code généré `*MapperImpl`
    conformément à la politique existante.
  - _Exigences : zone monétaire sous seuil_

- [x] 13. Consigner la règle dans le référentiel métier
  - Compléter `.kiro/steering/business-rules.md` : le test déterminant niveau+matière, sa
    distinction avec `group_types` (effectif), le bornage à l'année scolaire, l'acceptation des
    inscriptions inactives, les deux cas de routage, et le caractère explicite de la décision
    « déjà payée ». Le fichier est la référence du domaine : une règle appliquée dans le code mais
    absente d'ici sera contredite par la prochaine évolution.
  - _Exigences : décisions tracées hors du code_

- [x] 14. Checkpoint final — build, tests et couverture
  - Depuis `back/` : `bash build.sh clean verify`. Puis
    `cd front && npm test -- --watch=false --browsers=ChromeHeadless`.
  - Vérifier que tous les tests passent et que le seuil JaCoCo est tenu. Poser une question à
    l'utilisateur en cas de souci.

## Notes

- Les tâches de property test (1.3, 3.4, 4.2, 5.4, 5.5, 6.3) ne sont **pas** optionnelles : elles
  couvrent les 6 correctness properties, chacune par un test étiqueté `@Property(tries = 100)`.
- Deux tâches modifient des classes sous **seuil JaCoCo 100 % lignes+branches** et doivent porter
  leurs tests dans la même tâche : **4.1** (branche `PENDING` du résolveur) et **5.1** (retrait du
  véto de `CatchUpService`). Aucune branche défensive inatteignable.
- `CatchUpBillingQualifier.qualify()` — la comparaison de dates et le repli vers `CONSOMME` — n'est
  modifié par **aucune** tâche de ce plan.
- La tâche 11.2 a été **abandonnée** après lecture du rapport 11.1 : les enregistrements concernés
  sont des données de test, et le déploiement part d'une base vide. Le rapport avait déjà rempli son
  rôle en confirmant que le test niveau + matière les classe tous en Cas 2, sans changement de
  montant.
- Tous les builds backend s'exécutent depuis `back/` en Java 21 : lancé depuis la racine,
  `build.sh` échoue silencieusement avec un code de sortie 0.
