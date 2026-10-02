-- =====================================================================
-- V8 — Spec admin-corrections, tâche C.1 : la Fenêtre_Inscription a une fin
--
-- Une inscription concerne l'étudiant du jour de son arrivée (date_assigned)
-- au jour de son départ (date_left), ce dernier INCLUS. Les deux colonnes
-- portent une date calendaire, stockée à 00:00 de l'heure murale de la JVM
-- (décision D1) : comparer une séance à date_left + 1 jour, exclu, revient à
-- comparer des jours.
--
-- Jusqu'ici, une clôture (active = false) n'était datée que par date_update,
-- qui change à chaque modification de la ligne : le jour du départ n'était
-- pas une donnée, seulement l'heure de la dernière écriture.
--
-- Structure seulement : la base est réinitialisée avant l'installation chez
-- le client, aucune ligne ancienne n'est à reprendre.
-- =====================================================================
ALTER TABLE student_groups
    ADD COLUMN date_left TIMESTAMP;

-- Une fenêtre finit au plus tôt le jour où elle commence : un départ la veille
-- de l'arrivée décrirait une inscription qui n'a jamais concerné personne.
ALTER TABLE student_groups
    ADD CONSTRAINT ck_student_groups_window_ordered
    CHECK (date_left IS NULL OR date_assigned IS NULL OR date_left >= date_assigned);

-- Clôturée si et seulement si datée : une inscription close sans date de
-- départ ne dirait pas jusqu'à quand l'étudiant était attendu, et une
-- inscription ouverte avec une date de départ serait close sans le dire.
ALTER TABLE student_groups
    ADD CONSTRAINT ck_student_groups_closure_dated
    CHECK ((active IS FALSE) = (date_left IS NOT NULL));
