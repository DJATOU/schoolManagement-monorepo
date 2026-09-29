-- =====================================================================
-- Idempotence des encaissements
--
-- Deux situations produisent une requête d'encaissement rigoureusement
-- identique — même étudiant, même groupe, même série, même montant, même
-- jour :
--   1. un double clic sur « Encaisser » alors que la page tardait ;
--   2. un second versement réel le même jour, prévu par la règle du
--      paiement par facilité.
--
-- Aucune donnée ne les distingue. Les accepter toutes deux inscrit au
-- registre de l'argent qui n'est jamais entré en caisse ; les refuser
-- toutes deux perd un versement réel et lèse le parent. Un seuil de temps
-- n'aurait fait que déplacer l'arbitraire vers la vitesse de frappe de la
-- caissière.
--
-- Ce qui les sépare est l'INTENTION, non les données. Le client engendre
-- une clé à l'ouverture du formulaire : un rejeu de la même soumission
-- porte la même clé, un nouvel encaissement en porte une neuve.
--
-- L'unicité est portée par l'INDEX et non par le seul contrôle applicatif :
-- elle vaut alors quel que soit le nombre d'instances de l'application, et
-- deux requêtes strictement simultanées ne peuvent pas la contourner.
-- =====================================================================

CREATE TABLE payment_idempotency (
    id                  BIGSERIAL PRIMARY KEY,

    -- La clé fournie par le client.
    idempotency_key     VARCHAR(100)   NOT NULL,

    -- Empreinte de la requête : permet de détecter une clé réutilisée pour un
    -- versement DIFFÉRENT. C'est un défaut du client, jamais un rejeu ; le
    -- signaler vaut mieux que renvoyer le résultat d'un autre encaissement,
    -- qui produirait un reçu portant un montant que personne n'a versé.
    student_id          BIGINT         NOT NULL,
    group_id            BIGINT         NOT NULL,
    session_series_id   BIGINT         NOT NULL,
    amount_received     NUMERIC(12,2)  NOT NULL,

    -- Résultat conservé, pour que le rejeu réponde ce qu'a répondu l'original.
    amount_allocated    NUMERIC(12,2)  NOT NULL,
    payment_id          BIGINT,

    -- Horodatage d'encaissement retenu par le serveur. Sert de clé de relecture
    -- des reports : toutes les lignes de payment_carry_over nées d'un même
    -- encaissement partagent cet étudiant, cette série source et cet horodatage.
    -- Les relire évite d'en stocker une copie, laquelle pourrait diverger de la
    -- table que consultent les relevés.
    origin_payment_date TIMESTAMP      NOT NULL,

    -- Colonnes d'audit héritées de BaseEntity.
    active              BOOLEAN,
    created_by          VARCHAR(255),
    updated_by          VARCHAR(255),
    date_creation       TIMESTAMP,
    date_update         TIMESTAMP,
    description         VARCHAR(255),

    CONSTRAINT fk_payment_idempotency_payment
        FOREIGN KEY (payment_id) REFERENCES payments (id)
);

-- La garantie réelle du dédoublonnage.
CREATE UNIQUE INDEX uk_payment_idempotency_key ON payment_idempotency (idempotency_key);

-- Relecture des reports d'un encaissement donné (rejeu).
CREATE INDEX idx_payment_carry_over_origin
    ON payment_carry_over (student_id, source_series_id, origin_payment_date);
