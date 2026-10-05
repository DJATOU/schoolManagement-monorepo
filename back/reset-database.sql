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
    payment_idempotency,
    payment_detail,
    payment_carry_over,
    encashment_allocation,
    encashment,
    correction_audit,
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

-- Numérotation des reçus repartie de zéro : le prochain encaissement reçoit RECU-AAAA-0001.
-- La ligne unique du compteur est conservée (créée par la migration V6).
UPDATE receipt_counter SET counter_year = 0, last_rank = 0;

-- Après ce reset : redémarrez le backend. Le SchoolYearMigrationRunner recréera
-- une année scolaire courante initiale (aucun groupe/élève n'existant plus).
-- Pensez ensuite à redéfinir le rang (levelSequence) des niveaux réimportés.
