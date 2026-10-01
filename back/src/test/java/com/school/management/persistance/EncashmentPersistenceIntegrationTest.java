package com.school.management.persistance;

import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Écriture et relecture par JPA des entités du lot A (spec admin-corrections, D2, D3, D8).
 *
 * <p>Complète {@code MigrationSchemaPostgresIntegrationTest} : la validation Hibernate vérifie que
 * chaque colonne existe avec le bon type, pas que les associations s'écrivent et se relisent. Ce
 * test le vérifie, après un {@code clear()} qui force la relecture depuis la base plutôt que depuis
 * le cache de premier niveau.</p>
 */
@DataJpaTest
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
@DisplayName("Encaissement : écriture et relecture JPA")
class EncashmentPersistenceIntegrationTest {

    @Autowired
    private TestEntityManager em;

    private StudentEntity student;
    private GroupEntity group;
    private SessionSeriesEntity series;
    private SessionSeriesEntity nextSeries;
    private PaymentEntity payment;

    @BeforeEach
    void setUp() {
        student = em.persist(StudentEntity.builder().firstName("Amine").lastName("Belkacem").build());
        group = em.persist(GroupEntity.builder().name("Math 1ère A").build());
        series = em.persist(SessionSeriesEntity.builder().name("Série 1").group(group).build());
        nextSeries = em.persist(SessionSeriesEntity.builder().name("Série 2").group(group).build());
        payment = em.persist(PaymentEntity.builder()
                .student(student).group(group).sessionSeries(series).amountPaid(0.0).build());
    }

    private EncashmentEntity encashment(String receiptNumber, String amount) {
        return EncashmentEntity.builder()
                .receiptNumber(receiptNumber)
                .student(student)
                .group(group)
                .targetSeries(series)
                .amountReceived(new BigDecimal(amount))
                .kind(EncashmentKind.REGULAR)
                .receivedAt(new Date())
                .receivedBy("admin")
                .status(EncashmentStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("encaissement, imputations et ligne de ventilation relus depuis la base")
    void encashmentAndItsPartsAreReadBack() {
        EncashmentEntity encashment = em.persist(encashment("RECU-2030-0001", "6000.00"));
        EncashmentAllocationEntity direct = em.persist(EncashmentAllocationEntity.builder()
                .encashment(encashment).series(series).payment(payment)
                .amount(new BigDecimal("4000.00")).carriedOver(false).active(true).build());
        EncashmentAllocationEntity carried = em.persist(EncashmentAllocationEntity.builder()
                .encashment(encashment).series(nextSeries).payment(payment)
                .amount(new BigDecimal("2000.00")).carriedOver(true).active(true).build());
        PaymentDetailEntity detail = em.persist(PaymentDetailEntity.builder()
                .payment(payment).amountPaid(2000.0).encashmentAllocation(direct).build());
        em.flush();
        em.clear();

        EncashmentEntity reloaded = em.find(EncashmentEntity.class, encashment.getId());
        assertThat(reloaded.getReceiptNumber()).isEqualTo("RECU-2030-0001");
        // Échelle monétaire conservée : 6000.00 et non 6000.
        assertThat(reloaded.getAmountReceived()).isEqualByComparingTo("6000.00");
        assertThat(reloaded.getAmountReceived().scale()).isEqualTo(2);
        assertThat(reloaded.getKind()).isEqualTo(EncashmentKind.REGULAR);
        assertThat(reloaded.isActive()).isTrue();
        assertThat(reloaded.getTargetSeries().getId()).isEqualTo(series.getId());

        EncashmentAllocationEntity reloadedCarried = em.find(EncashmentAllocationEntity.class, carried.getId());
        assertThat(reloadedCarried.getEncashment().getId()).isEqualTo(encashment.getId());
        assertThat(reloadedCarried.getSeries().getId()).isEqualTo(nextSeries.getId());
        assertThat(reloadedCarried.getCarriedOver()).isTrue();

        // La ligne de ventilation remonte jusqu'à son encaissement par son Imputation.
        PaymentDetailEntity reloadedDetail = em.find(PaymentDetailEntity.class, detail.getId());
        assertThat(reloadedDetail.getEncashmentAllocation().getEncashment().getReceiptNumber())
                .isEqualTo("RECU-2030-0001");
    }

    @Test
    @DisplayName("statuts et types enregistrés en clair, pour rester lisibles en SQL")
    void enumsAreStoredAsText() {
        EncashmentEntity encashment = em.persist(encashment("RECU-2030-0002", "2000.00"));
        em.flush();

        Object[] row = (Object[]) em.getEntityManager()
                .createNativeQuery("SELECT status, kind FROM encashment WHERE id = ?1")
                .setParameter(1, encashment.getId())
                .getSingleResult();
        assertThat(row).containsExactly("ACTIVE", "REGULAR");
    }

    @Test
    @DisplayName("numéro de reçu en double refusé")
    void receiptNumberIsUnique() {
        em.persistAndFlush(encashment("RECU-2030-0003", "2000.00"));

        assertThatThrownBy(() -> em.persistAndFlush(encashment("RECU-2030-0003", "500.00")))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    @DisplayName("remplacement relu dans les deux sens")
    void replacementIsLinkedBothWays() {
        EncashmentEntity original = em.persist(encashment("RECU-2030-0004", "20000.00"));
        EncashmentEntity replacement = em.persist(encashment("RECU-2030-0005", "2000.00"));
        original.setStatus(EncashmentStatus.CANCELLED);
        original.setCancelledAt(new Date());
        original.setCancelledBy("admin");
        original.setCancelReasonType(CorrectionReasonType.WRONG_AMOUNT);
        original.setReplacedBy(replacement);
        replacement.setReplaces(original);
        em.flush();
        em.clear();

        EncashmentEntity reloadedOriginal = em.find(EncashmentEntity.class, original.getId());
        EncashmentEntity reloadedReplacement = em.find(EncashmentEntity.class, replacement.getId());
        assertThat(reloadedOriginal.isActive()).isFalse();
        assertThat(reloadedOriginal.getReplacedBy().getReceiptNumber()).isEqualTo("RECU-2030-0005");
        assertThat(reloadedReplacement.getReplaces().getReceiptNumber()).isEqualTo("RECU-2030-0004");
        assertThat(reloadedOriginal.getCancelReasonType()).isEqualTo(CorrectionReasonType.WRONG_AMOUNT);
    }

    @Test
    @DisplayName("trace de correction relue telle qu'écrite, rang croissant")
    void correctionTraceIsReadBack() {
        CorrectionAuditEntity first = em.persist(trace("Reçu RECU-2030-0004 annulé"));
        CorrectionAuditEntity second = em.persist(trace("Reçu RECU-2030-0005 émis en remplacement"));
        em.flush();
        em.clear();

        CorrectionAuditEntity reloaded = em.find(CorrectionAuditEntity.class, second.getId());
        assertThat(reloaded.getSummary()).isEqualTo("Reçu RECU-2030-0005 émis en remplacement");
        assertThat(reloaded.getDomain()).isEqualTo(CorrectionDomain.ENCASHMENT);
        assertThat(reloaded.getAction()).isEqualTo(CorrectionAction.ENCASHMENT_REPLACED);
        assertThat(reloaded.getReasonType()).isEqualTo(CorrectionReasonType.WRONG_AMOUNT);
        assertThat(second.getId()).isGreaterThan(first.getId());
    }

    private CorrectionAuditEntity trace(String summary) {
        return CorrectionAuditEntity.builder()
                .domain(CorrectionDomain.ENCASHMENT)
                .action(CorrectionAction.ENCASHMENT_REPLACED)
                .entityId(1L)
                .studentId(student.getId())
                .summary(summary)
                .amountEffect("versé 20 000,00 → 2 000,00 DA")
                .reasonType(CorrectionReasonType.WRONG_AMOUNT)
                .performedBy("admin")
                .performedAt(LocalDateTime.now())
                .build();
    }
}
