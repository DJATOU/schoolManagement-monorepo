package com.school.management.persistance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

/**
 * Empreinte d'un encaissement déjà traité, indexée par la clé fournie par le client.
 *
 * <p><b>Le problème résolu.</b> Deux situations produisent une requête d'encaissement
 * rigoureusement identique — même étudiant, même groupe, même série, même montant, même jour :
 * un double clic sur « Encaisser » alors que la page tardait, et un second versement réel le
 * même jour, prévu par la règle du paiement par facilité. Aucune donnée ne les distingue. Les
 * accepter toutes deux inscrit au registre de l'argent qui n'est jamais entré en caisse ; les
 * refuser toutes deux perd un versement réel et lèse le parent.</p>
 *
 * <p><b>Ce qui les distingue.</b> Non pas les données, mais l'<strong>intention</strong>. Le
 * client engendre une clé à l'ouverture du formulaire de saisie : un rejeu de la même soumission
 * porte la même clé, tandis qu'un second encaissement passe par un nouveau formulaire, donc une
 * nouvelle clé. Le serveur n'a plus à deviner, et aucun seuil de temps arbitraire n'intervient —
 * un délai n'aurait fait que déplacer l'erreur vers la vitesse de frappe de la caissière.</p>
 *
 * <p><b>Pourquoi une table et non un cache.</b> L'unicité est portée par un index du stockage.
 * Elle vaut alors quel que soit le nombre d'instances de l'application, et deux requêtes
 * strictement simultanées ne peuvent pas la contourner : la seconde se heurte à l'index. Un
 * cache applicatif ne tiendrait ni le redémarrage ni la montée en charge, précisément les
 * moments où un rejeu est le plus probable.</p>
 *
 * <p><b>L'empreinte est vérifiée, pas seulement la clé.</b> Les champs de la requête sont
 * conservés pour détecter la réutilisation d'une clé sur un versement différent. C'est un défaut
 * du client, jamais un rejeu : le signaler vaut mieux que renvoyer le résultat d'un autre
 * encaissement, qui donnerait un reçu portant un montant que personne n'a versé.</p>
 */
@Entity
@Table(name = "payment_idempotency")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class PaymentIdempotencyEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // La clé fournie par le client. Unique : c'est l'index qui garantit réellement le
    // dédoublonnage, la vérification applicative n'étant qu'un raccourci de lecture.
    @Column(name = "idempotency_key", length = 100, nullable = false, unique = true)
    private String idempotencyKey;

    // ------------------------------------------------------------------
    // Empreinte de la requête : sert à détecter une clé réutilisée à tort
    // ------------------------------------------------------------------

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "session_series_id", nullable = false)
    private Long sessionSeriesId;

    // Montant reçu, échelle 2 : jamais de double sur un montant (audit H4)
    @Column(name = "amount_received", precision = 12, scale = 2, nullable = false)
    private BigDecimal amountReceived;

    // ------------------------------------------------------------------
    // Résultat conservé, pour que le rejeu réponde ce qu'a répondu l'original
    // ------------------------------------------------------------------

    // La part imputée sur la série visée. Nulle lorsque cette série était soldée et que la
    // totalité est partie en report.
    @Column(name = "amount_allocated", precision = 12, scale = 2, nullable = false)
    private BigDecimal amountAllocated;

    // La ligne de paiement principale créditée. Permet au rejeu de rendre le même reçu.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private PaymentEntity payment;

    /**
     * Date d'encaissement retenue par le serveur lors du traitement original.
     *
     * <p>Elle sert de clé de relecture des reports : toutes les lignes de
     * {@code payment_carry_over} nées d'un même encaissement partagent cet étudiant, cette série
     * source et cet horodatage. Le rejeu restitue ainsi le détail exact des reports sans en
     * dupliquer le stockage — un second exemplaire du même détail pourrait diverger de la table
     * qui fait foi, et c'est elle que consultent les relevés.</p>
     */
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "origin_payment_date", nullable = false)
    private java.util.Date originPaymentDate;

    /**
     * Encaissement produit par la requête d'origine (spec admin-corrections, D3) : un rejeu
     * renverra son numéro de reçu. Facultatif tant que le code d'encaissement ne l'écrit pas.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "encashment_id")
    private EncashmentEntity encashment;

    /**
     * Séance payée, pour un encaissement de rattrapage ; nulle pour un versement de série. Elle
     * complète l'empreinte : deux rattrapages du même montant sur deux séances de la même série
     * sont deux encaissements, et une clé passée d'un chemin à l'autre est une clé réutilisée.
     */
    @Column(name = "session_id")
    private Long sessionId;
}
