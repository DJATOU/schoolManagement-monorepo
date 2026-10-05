-- =====================================================================
-- V7 — Spec admin-corrections, tâche A.6 : tout argent compté désigne son Encaissement
--
-- V6 a ajouté ces trois liens FACULTATIFS, le temps que le code les écrive :
-- depuis A.4 et A.5, chaque ligne de ventilation et chaque report sont la part
-- d'une Imputation, et chaque empreinte d'idempotence désigne l'Encaissement
-- de sa requête. Les rendre obligatoires porte l'invariant dans le stockage
-- (exigence 1.3) : une ligne qui ne dirait pas de quel versement elle vient ne
-- peut plus exister, quel que soit le code qui l'écrit.
--
-- Aucune donnée n'est reprise : la base est réinitialisée avant l'installation
-- chez le client. Sur une base qui contiendrait des lignes antérieures à A.4,
-- cette migration échoue, et c'est voulu : de telles lignes n'ont pas
-- d'Encaissement auquel les rattacher.
-- =====================================================================

ALTER TABLE payment_detail
    ALTER COLUMN encashment_allocation_id SET NOT NULL;

ALTER TABLE payment_carry_over
    ALTER COLUMN encashment_allocation_id SET NOT NULL;

ALTER TABLE payment_idempotency
    ALTER COLUMN encashment_id SET NOT NULL;
