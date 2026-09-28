# Plan de tests fonctionnels — School Management

Tests fonctionnels manuels/API couvrant paiement, présence, sessions, rattrapage,
réductions, PDF et bascule d'année scolaire, avec leurs cas d'exception.

Méthode : tests exécutés via l'API (`localhost:8080`) pour la logique métier, et via
l'UI pour le rendu (PDF, affichage). Chaque test note le **résultat attendu**.

Référence métier : `.kiro/steering/business-rules.md` (source de vérité).
Rappel des deux montants à ne jamais confondre :
- **Coût total du mois** = séances planifiées × prix
- **Dû à ce jour** = séances **présentes** × prix (seuil du "en retard")
- `isLate = amountPaid < amountDueSoFar`

Jeu de données de référence :
- Groupe Math 1ère A : 2000/séance, 4 séances/série (Grand groupe)
- Groupe Physique 2ème : 4000/séance, 5 séances/série (Moyen groupe)
- Groupe Sciences 3ème : 6000/séance, 8 séances/série (Petit groupe)

---

## 1. Données de base & prérequis

- [x] 1.1 Vérifier l'année scolaire courante (une seule `isCurrent = true`)
- [x] 1.2 Vérifier les niveaux ordonnés (levelSequence 1, 2, 3)
- [x] 1.3 Vérifier que chaque groupe a un prix rattaché
- [x] 1.4 Vérifier l'import de 200 élèves (tous ACTIVE, niveau rattaché)
- [x] 1.5 Inscrire des élèves dans les groupes (au moins 5 par groupe testé)

## 2. Sessions & séries

- [x] 2.1 Créer une séance → rattachement automatique à une série non pleine
- [ ] 2.2 Remplir une série (4 séances) puis créer une 5e séance → nouvelle série auto-créée
  > NOTE : la logique "chercher série non pleine / créer nouvelle série" est côté FRONTEND
  > (session-form). L'API POST /api/sessions exige un sessionSeriesId explicite. À valider via l'UI.
- [x] 2.3 Vérifier le nombre de séances par série respecte `sessionNumberPerSerie`
- [x] 2.4 Valider une séance (isFinished = true)
- [x] 2.5 Dévalider une séance (isFinished = false, présences désactivées)
- [ ] 2.6 **Exception** : créer une séance sur un groupe d'année passée → rejet 409 (lecture seule)
- [ ] 2.7 **Exception** : créer une série sur une année passée → rejet 409

## 3. Présence (attendance)

- [x] 3.1 Marquer un élève présent sur N séances → N comptées comme "attended"
- [x] 3.2 Marquer un élève absent → non compté dans le dû
- [x] 3.3 Absence justifiée → non compté dans le dû (comme absence simple pour le "en retard")
- [ ] 3.4 Cocher "présent" décoche "justifié" (et inversement)
- [ ] 3.5 "Check all" marque tous les élèves présents
- [ ] 3.6 Modifier une présence déjà enregistrée (re-soumission)
- [ ] 3.7 **Exception** : présence sur séance d'année passée → rejet (lecture seule)

## 4. Paiement — cas nominaux

- [x] 4.1 Élève avec 3 présences (dû 6000), paie 6000 → **à jour**, 0 séance en retard
- [x] 4.2 Élève avec 3 présences (dû 6000), paie 4000 → **en retard** (séance 3 non couverte)
- [x] 4.3 Distribution du paiement séance par séance dans l'ordre chronologique
- [x] 4.4 Coût total du mois vs dû à ce jour : distinguer les deux montants (audit H5)
- [x] 4.5 Complément de paiement (4000 puis 2000) → passage à "à jour"

## 5. Paiement — par séance / par facilité

- [x] 5.1 Paiement d'une seule séance (montant = 1 × prix) → cette séance couverte (élève 11 : 2000 → séance 1 à jour)
- [x] 5.2 Paiement fractionné : payer séance par séance sur plusieurs versements (2×2000 → 0 en retard)
- [x] 5.3 Élève reste "à jour" tant que `payé >= présentes × prix`, même si mois non complet
- [ ] 5.4 Sur-paiement (payer plus que le dû actuel) → crédit / séances futures couvertes

## 6. Paiement — réductions

- [x] 6.1 Appliquer une réduction par élève → dû recalculé en conséquence (scope SERIES 50%)
- [ ] 6.2 Appliquer une réduction par paiement → montant owed diminué
- [x] 6.3 Élève en réduction : vérifier statut "à jour/en retard" avec le montant réduit
- [x] 6.4 **Exception** : réduction supérieure au montant → dû plancher à 0 (jamais négatif) — exemption 100% OK

## 7. Rattrapage (catch-up)

- [x] 7.1 Élève fait une séance de rattrapage dans un autre groupe → comptée comme présente
- [x] 7.2 Vérifier que la date et le groupe du rattrapage sont enregistrés
- [x] 7.3 Paiement de rattrapage (`/process/catch-up`) sur une séance précise (4000, COMPLETED)
- [ ] 7.4 Élève suit la même matière dans 2 groupes (G1 + G2) → les deux comptent
- [x] 7.5 **Exception** : rattrapage sur séance inexistante → message clair
  > CONSTAT : message clair « Session not found with ID: 9999 » mais code HTTP 500 au lieu de 404
  > (CustomServiceException sans statut → 500). À améliorer : mapper "not found" en 404.

## 8. Changement de groupe en cours de mois

- [ ] 8.1 Élève change de groupe mi-mois → présences OLD + NEW sommées pour le dû
- [ ] 8.2 Vérifier le comptage cross-group sur le mois courant
- [ ] 8.3 Historique des inscriptions préservé après changement

## 9. Statuts de paiement (indicateur rouge/vert)

- [ ] 9.1 Statut par séance : `paymentOverdue` true/false correct
- [ ] 9.2 Statut par groupe : agrégation correcte des séances
- [ ] 9.3 Liste des séances impayées (`/unpaid-sessions`)
- [ ] 9.4 Statut de paiement d'un groupe entier (`/students-payment-status`)
- [ ] 9.5 Absent non payé → n'apparaît PAS comme en retard

## 10. Génération PDF (UI)

- [ ] 10.1 Feuille de présence vierge (avant validation) → cases à cocher vides
- [ ] 10.2 Feuille de présence remplie (après validation) → présents/justifiés pré-cochés
- [ ] 10.3 Reçu de paiement après un versement
- [ ] 10.4 Impression d'une liste (élèves / groupes / tableaux)
- [ ] 10.5 **Exception** : PDF d'un groupe sans données → pas de crash, message adéquat

## 11. Bascule d'année (year-end workflow)

- [ ] 11.1 Aperçu (preview) : libellé année suivante proposé + décisions par défaut PROMOTION (fait en UI par l'utilisateur)
- [ ] 11.2 Élèves au niveau le plus élevé signalés "à revoir" (UI)
- [x] 11.3 Exécution : promotion → niveau suivant, statut ACTIVE (élève 1 : 1er → 2eme année, ACTIVE)
- [ ] 11.4 Redoublement → niveau inchangé, ACTIVE (non testé isolément)
- [ ] 11.5 Départ → statut INACTIVE, niveau inchangé (non testé isolément)
- [x] 11.6 Nouvelle année devient courante, ancienne isCurrent = false (2026-2027 courante, 2025-2026 passée)
- [x] 11.7 Données de l'ancienne année préservées (groupe 1 conserve ses 5 inscrits)
- [ ] 11.8 **Exception** : bascule sans niveaux ordonnés → message clair (400) (déjà rencontré/corrigé plus tôt)
- [ ] 11.9 **Exception** : élève sans inscription → promotion appliquée quand même (14.3)

> RATTRAPAGE — contrainte "même année scolaire" ajoutée et validée : les séances de
> rattrapage disponibles pour une séance manquée de 2025-2026 excluent désormais les
> groupes de 2026-2027 (pas de rattrapage inter-années).

## 12. Lecture seule des années passées

- [ ] 12.1 Sélectionner une année passée → liste élèves = inscrits de cette année (figé)
- [x] 12.2 Sélectionner l'année courante → tous les élèves ACTIVE (déjà validé section 14)
- [x] 12.3 **Exception** : modifier/créer sur un groupe d'année passée → rejet 409
  > Validé : POST séance (2.6) et POST série (2.7) sur groupe année passée → 409
  > "Cette année scolaire est en lecture seule : modification interdite."
- [ ] 12.4 **Exception** : ajouter un paiement sur année passée → rejet (à tester)
- [x] 12.5 Lecture (consultation) d'une année passée → autorisée (GET groupe 1 → 200)
- [ ] 12.6 UI : contrôles d'édition désactivés en année passée (à valider en UI)

## 13. Parcours élève

- [x] 13.1 Parcours = années où l'élève a au moins une inscription (élève 1 → 2025-2026)
- [x] 13.2 Niveau historique dérivé des groupes suivis cette année-là (1er année via Math 1ère A)
- [ ] 13.3 Élève dans plusieurs niveaux la même année → tous listés (non testé)
- [ ] 13.4 Ordre des entrées par date de début d'année décroissante (1 seule année pour l'instant)
- [x] 13.5 **Exception** : année sans inscription → omise du parcours
  > Confirmé : la présence de RATTRAPAGE (groupe 4, 2026-2027) n'apparaît PAS dans le parcours
  > car le parcours se base sur les INSCRIPTIONS, pas les présences.

## 14. Statut des élèves (actif/inactif)

- [x] 14.1 Liste par défaut exclut les INACTIVE
- [x] 14.2 Liste "inclure inactifs" ajoute les INACTIVE
- [x] 14.3 Réactiver un élève INACTIVE → repasse ACTIVE (PATCH /reactivate)
- [x] 14.4 Élève INACTIVE conserve son historique (inscriptions, paiements)

> ✅ CORRIGÉ : ajout des endpoints `PATCH /api/students/{id}/deactivate` (départ,
> exigence 7.1) et `PATCH /api/students/{id}/reactivate` (exigence 7.5).

## 15. Import CSV

- [ ] 15.1 Import niveaux (avec levelSequence)
- [ ] 15.2 Import matières, salles, types de groupe
- [ ] 15.3 Import enseignants
- [ ] 15.4 Import 200 élèves
- [ ] 15.5 Import groupes (résolution niveau/matière/prof par nom)
- [ ] 15.6 **Exception** : ligne avec niveau/matière introuvable → rejetée, autres lignes OK
- [ ] 15.7 **Exception** : fichier sans en-tête / vide → message d'erreur clair
- [ ] 15.8 Colonnes optionnelles manquantes → import OK (champs null)

## Notes

- Les tests API (logique métier) sont exécutés par l'agent ; les tests UI/PDF sont
  validés manuellement par l'utilisateur.
- Chaque cas d'exception vérifie un rejet propre (message + code HTTP), pas un crash.
- Les montants utilisent BigDecimal côté backend (audit H4) ; vérifier l'arrondi.
- Après chaque test destructif, restaurer un état cohérent pour le test suivant.
