-- =============================================================================
-- Script de remise à zéro de la base (DÉVELOPPEMENT / TESTS UNIQUEMENT)
--
-- ⚠️  DESTRUCTIF : supprime TOUTES les données de TOUTES les tables métier.
--     À n'exécuter que sur une base de dev/test, jamais en production.
--
-- PostgreSQL : TRUNCATE ... RESTART IDENTITY CASCADE vide les tables et
-- réinitialise les compteurs d'identité. CASCADE gère les clés étrangères.
--
-- Utilisation (exemple) :
--   psql -h localhost -U <user> -d schoolManagement4 -f reset-database.sql
-- =============================================================================

TRUNCATE TABLE
    payment_detail_audit,
    payment_detail,
    payment_carry_over,
    refund,
    payments,
    attendance,
    catch_up_request,
    session,
    session_series,
    student_groups,
    discount,
    student,
    teacher,
    tutor,
    groups,
    group_types,
    subject,
    price,
    room,
    level,
    administrator,
    school_year
RESTART IDENTITY CASCADE;

-- Après ce reset : redémarrez le backend. Le SchoolYearMigrationRunner recréera
-- une année scolaire courante initiale (aucun groupe/élève n'existant plus).
-- Pensez ensuite à redéfinir le rang (levelSequence) des niveaux réimportés.
