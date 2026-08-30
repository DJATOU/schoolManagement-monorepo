-- =====================================================================
-- RAPPORT EN LECTURE SEULE — rattrapages sans séance manquée
-- Feature : catch-up-billing-routing, tâche 11.1
-- =====================================================================
--
-- OBJET
--   Recenser les présences de rattrapage enregistrées SANS lien vers la
--   séance manquée, et donner à chacune de quoi décider : niveau et
--   matière DES DEUX CÔTÉS — groupe d'accueil et groupes d'inscription.
--
--   C'est cette confrontation qui distingue les deux cas :
--     * Cas 1 (vrai rattrapage)  : il existe une inscription à un autre
--       groupe de MÊME niveau et MÊME matière, même année scolaire. Une
--       place y est réservée, la facturation doit rester ancrée à la
--       séance manquée dans son groupe d'origine.
--     * Cas 2 (facturée sur place) : aucun tel groupe. Personne d'autre
--       ne facture cette séance, la facturer au groupe d'accueil est
--       correct — l'absence de séance manquée est voulue, non un oubli.
--
-- CE SCRIPT N'ÉCRIT RIEN.
--   Aucun INSERT, UPDATE ni DELETE. Les lignes concernées portent de
--   l'argent déjà encaissé : leur reclassement se décide enregistrement
--   par enregistrement, puis s'applique par un script ciblant des
--   identifiants nommés (tâche 11.2). Un UPDATE large déplacerait des
--   montants reconnus sans que personne ne l'ait demandé.
--
-- USAGE
--   psql -U postgres -h localhost -d schoolManagement4 \
--        -f back/reports/catch-up-billing-audit-report.sql
-- =====================================================================

\pset pager off

-- ---------------------------------------------------------------------
-- 1. Vue d'ensemble
-- ---------------------------------------------------------------------
SELECT 'Rattrapages actifs'                       AS indicateur,
       count(*)                                   AS valeur
FROM attendance
WHERE is_catch_up = true AND active = true
UNION ALL
SELECT 'dont sans séance manquée (à traiter)',
       count(*)
FROM attendance
WHERE is_catch_up = true AND active = true AND missed_session_id IS NULL
;
-- Note : la colonne catch_up_billing_state n'existe qu'après la migration V3.
-- Le rapport ne la lit donc pas, afin de rester exécutable AVANT la migration —
-- c'est précisément à ce moment qu'on a besoin de décider.

-- ---------------------------------------------------------------------
-- 2. Détail, un enregistrement par ligne
-- ---------------------------------------------------------------------
-- Colonnes clés pour la décision :
--   niveau_accueil / matiere_accueil   → les deux dimensions du test
--   groupes_inscrits                   → les mêmes, côté inscriptions
--   nb_groupes_meme_type               → verdict du test (0 ⇒ Cas 2)
--   deja_encaisse                      → l'argent en jeu sur cette séance
SELECT a.id                                        AS att_id,
       st.first_name || ' ' || st.last_name        AS etudiant,
       s.title                                     AS seance_accueil,
       s.session_time_start::date                  AS date_seance,
       hg.name                                     AS groupe_accueil,
       hl.name                                     AS niveau_accueil,
       hsub.name                                   AS matiere_accueil,
       hsy.label                                   AS annee_accueil,

       -- Inscriptions de l'étudiant, avec niveau et matière : c'est le côté
       -- qu'il faut comparer au groupe d'accueil pour trancher.
       COALESCE(
         (SELECT string_agg(og.name || ' [' || COALESCE(ol.name, '?')
                            || ' / ' || COALESCE(osub.name, '?') || ']', ' ; '
                            ORDER BY og.name)
          FROM student_groups sg
          JOIN groups og      ON og.id = sg.group_id
          LEFT JOIN level ol  ON ol.id = og.level_id
          LEFT JOIN subject osub ON osub.id = og.subject_id
          WHERE sg.student_id = a.student_id),
         '(aucune inscription)')                   AS groupes_inscrits,

       -- Verdict du test de routage, calculé ici exactement comme le fait
       -- CatchUpRoutingService : même niveau, même matière, même année,
       -- groupe d'accueil exclu, inscriptions clôturées comprises.
       (SELECT count(*)
        FROM student_groups sg
        JOIN groups og ON og.id = sg.group_id
        WHERE sg.student_id = a.student_id
          AND og.id        <> hg.id
          AND og.level_id   = hg.level_id
          AND og.subject_id = hg.subject_id
          AND og.school_year_id = hg.school_year_id)  AS nb_groupes_meme_type,

       CASE WHEN (SELECT count(*)
                  FROM student_groups sg
                  JOIN groups og ON og.id = sg.group_id
                  WHERE sg.student_id = a.student_id
                    AND og.id        <> hg.id
                    AND og.level_id   = hg.level_id
                    AND og.subject_id = hg.subject_id
                    AND og.school_year_id = hg.school_year_id) > 0
            THEN 'CAS 1 — vrai rattrapage : DÉCISION HUMAINE REQUISE'
            ELSE 'CAS 2 — facturée sur place : trace à poser, aucun montant modifié'
       END                                         AS verdict,

       -- Argent déjà encaissé sur cette séance pour cet étudiant : c'est ce
       -- qu'un reclassement en Cas 1 remettrait en cause.
       COALESCE((SELECT sum(pd.amount_paid)
                 FROM payment_detail pd
                 JOIN payments p ON p.id = pd.payment_id
                 WHERE pd.session_id = a.session_id
                   AND p.student_id = a.student_id
                   AND (pd.active IS NULL OR pd.active = true)
                   AND (p.status IS NULL OR p.status <> 'CANCELLED')), 0)
                                                   AS deja_encaisse
FROM attendance a
JOIN student st        ON st.id = a.student_id
LEFT JOIN session s    ON s.id  = a.session_id
LEFT JOIN groups hg    ON hg.id = COALESCE(a.group_id, s.group_id)
LEFT JOIN level hl     ON hl.id = hg.level_id
LEFT JOIN subject hsub ON hsub.id = hg.subject_id
LEFT JOIN school_year hsy ON hsy.id = hg.school_year_id
WHERE a.is_catch_up = true
  AND a.active = true
  AND a.missed_session_id IS NULL
ORDER BY nb_groupes_meme_type DESC, a.id;

-- ---------------------------------------------------------------------
-- 3. Séances manquées candidates, pour les seuls Cas 1
-- ---------------------------------------------------------------------
-- Absences non suivies de l'étudiant dans un groupe de même niveau et même
-- matière : ce sont les seules séances qu'un Cas 1 pourrait légitimement
-- rattraper. Table vide ⇒ aucun Cas 1, donc aucune décision humaine à prendre.
SELECT a.id                                 AS att_id,
       st.first_name || ' ' || st.last_name AS etudiant,
       ms.id                                AS candidate_seance_manquee,
       ms.title                             AS titre,
       ms.session_time_start::date           AS date_seance,
       og.name                              AS groupe_origine,
       ma.status                            AS presence_origine,
       ma.is_justified                      AS justifiee
FROM attendance a
JOIN student st     ON st.id = a.student_id
LEFT JOIN session s ON s.id  = a.session_id
LEFT JOIN groups hg ON hg.id = COALESCE(a.group_id, s.group_id)
JOIN student_groups sg ON sg.student_id = a.student_id
JOIN groups og      ON og.id = sg.group_id
                   AND og.id        <> hg.id
                   AND og.level_id   = hg.level_id
                   AND og.subject_id = hg.subject_id
                   AND og.school_year_id = hg.school_year_id
JOIN session ms     ON ms.group_id = og.id
LEFT JOIN attendance ma ON ma.session_id = ms.id
                       AND ma.student_id = a.student_id
                       AND ma.active = true
WHERE a.is_catch_up = true
  AND a.active = true
  AND a.missed_session_id IS NULL
  -- Absence, ou séance sans feuille de présence : dans les deux cas l'étudiant
  -- n'y était pas, donc elle peut avoir été rattrapée ailleurs.
  AND (ma.id IS NULL OR ma.status = false)
  -- Une séance déjà rattrapée par ailleurs n'est plus candidate : un seul
  -- rattrapage par séance manquée.
  AND NOT EXISTS (SELECT 1 FROM attendance other
                  WHERE other.student_id = a.student_id
                    AND other.missed_session_id = ms.id
                    AND other.active = true)
ORDER BY a.id, ms.session_time_start;
