package com.school.management.service.correction;

import com.school.management.persistance.CorrectionAction;
import com.school.management.persistance.CorrectionAuditEntity;
import com.school.management.persistance.CorrectionDomain;
import com.school.management.persistance.CorrectionReasonType;
import com.school.management.repository.CorrectionAuditRepository;
import com.school.management.service.exception.CustomServiceException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * {@link CorrectionAuditService} appelé directement, dans une transaction de test : les cas limites
 * que le runner n'atteint pas facilement (spec admin-corrections, exigences 11.3 à 11.5, D8).
 *
 * <p>Même configuration que {@code CorrectionRunnerIntegrationTest} : le contexte est partagé.</p>
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:correction-runner;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
@DisplayName("CorrectionAuditService — écriture d'une trace")
class CorrectionAuditServiceIntegrationTest {

    private static final AmountSnapshot PAID = snapshot("4000", "3000");
    private static final AmountSnapshot UNPAID = snapshot("4000", "0");

    @Autowired private CorrectionAuditService service;
    @Autowired private CorrectionAuditRepository repository;
    @Autowired private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactions;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        transactions = new TransactionTemplate(transactionManager);
        authenticate(new UsernamePasswordAuthenticationToken(
                "admin-test", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("l'effet sur les montants ne retient que les Séries de l'étudiant de la trace")
    void amountEffectKeepsOnlyTheStudentOfTheTrace() {
        List<SeriesAmountChange> changes = List.of(
                new SeriesAmountChange(1L, "Amine", 10L, "Janvier", "Math", PAID, UNPAID),
                new SeriesAmountChange(2L, "Lina", 20L, "Mars", "Physique", PAID, UNPAID));

        CorrectionAuditEntity amine = record(draft(1L, Map.of("v", 1), Map.of("v", 2), "Amine corrigé"), changes);
        CorrectionAuditEntity global = record(draft(null, Map.of("v", 1), Map.of("v", 2), "Séance corrigée"), changes);

        assertThat(amine.getAmountEffect()).isEqualTo("Janvier (Math) : versé 3 000,00 → 0,00 DA, "
                + "reste 1 000,00 → 4 000,00 DA");
        assertThat(global.getAmountEffect()).as("trace sans étudiant : toutes les Séries")
                .contains("Janvier (Math)").contains(" ; Mars (Physique)");
    }

    @Test
    @DisplayName("sans montant changé, la trace n'a pas d'effet sur les montants")
    void noAmountEffectWithoutChange() {
        CorrectionAuditEntity trace = record(draft(1L, null, Map.of("notes", "x"), "Note ajoutée"), List.of());

        assertThat(trace.getAmountEffect()).isNull();
        assertThat(trace.getOldValue()).isNull();
        assertThat(trace.getNewValue()).isEqualTo("{\"notes\":\"x\"}");
    }

    @Test
    @DisplayName("une création ou un retrait sans valeurs structurées reste traçable")
    void valuesMayBothBeAbsent() {
        CorrectionAuditEntity trace = record(draft(1L, null, null, "Présence retirée"), List.of());

        assertThat(trace.getOldValue()).isNull();
        assertThat(trace.getNewValue()).isNull();
    }

    @Test
    @DisplayName("les valeurs structurées sont écrites clés triées : la même valeur s'écrit toujours pareil")
    void structuredValuesAreWrittenWithSortedKeys() {
        Map<String, Object> unordered = new LinkedHashMap<>();
        unordered.put("status", "CANCELLED");
        unordered.put("amount", new BigDecimal("3000.00"));

        CorrectionAuditEntity trace = record(draft(1L, Map.of("status", "ACTIVE"), unordered, "Annulé"), List.of());

        assertThat(trace.getNewValue()).isEqualTo("{\"amount\":3000.00,\"status\":\"CANCELLED\"}");
    }

    @Test
    @DisplayName("un résumé trop long est ramené à la colonne, en le signalant")
    void aTooLongSummaryIsShortened() {
        String longSummary = "x".repeat(CorrectionAuditService.MAX_TEXT_LENGTH + 20);

        CorrectionAuditEntity trace = record(draft(1L, null, Map.of("v", 1), longSummary), List.of());

        assertThat(trace.getSummary()).hasSize(CorrectionAuditService.MAX_TEXT_LENGTH).endsWith("…");
        assertThat(record(draft(1L, null, Map.of("v", 1), "x".repeat(CorrectionAuditService.MAX_TEXT_LENGTH)),
                List.of()).getSummary()).as("exactement à la limite : intact").doesNotContain("…");
    }

    @Test
    @DisplayName("des valeurs avant et après identiques sont refusées")
    void identicalValuesAreRefused() {
        CustomServiceException refused = catchThrowableOfType(
                () -> record(draft(1L, Map.of("v", 1), Map.of("v", 1), "Rien"), List.of()),
                CustomServiceException.class);

        assertThat(refused.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(repository.count()).isZero();
    }

    @Test
    @DisplayName("seul un utilisateur authentifié signe une trace")
    void onlyAnAuthenticatedUserSigns() {
        for (Authentication anonymous : Arrays.asList(
                null,
                new UsernamePasswordAuthenticationToken("admin-test", null),
                new AnonymousAuthenticationToken("clé", "anonymousUser",
                        List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))))) {
            authenticate(anonymous);
            CustomServiceException refused = catchThrowableOfType(
                    () -> record(draft(1L, null, Map.of("v", 1), "Corrigé"), List.of()),
                    CustomServiceException.class);
            assertThat(refused.getStatus()).as(String.valueOf(anonymous)).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
        assertThat(repository.count()).isZero();
    }

    @Test
    @DisplayName("une trace ne s'écrit jamais hors de la transaction d'une correction")
    void neverOutsideATransaction() {
        assertThatThrownBy(() -> service.record(draft(1L, null, Map.of("v", 1), "Corrigé"), List.of()))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(repository.count()).isZero();
    }

    // ------------------------------------------------------------------

    private CorrectionAuditEntity record(AuditDraft draft, List<SeriesAmountChange> changes) {
        return transactions.execute(status -> service.record(draft, changes));
    }

    private static AuditDraft draft(Long studentId, Map<String, ?> oldValue, Map<String, ?> newValue,
                                    String summary) {
        return AuditDraft.builder()
                .domain(CorrectionDomain.ENCASHMENT).action(CorrectionAction.ENCASHMENT_CANCELLED).entityId(42L)
                .studentId(studentId).oldValue(oldValue).newValue(newValue).summary(summary)
                .reason(new CorrectionReason(CorrectionReasonType.OTHER, "Erreur de guichet"))
                .build();
    }

    private static void authenticate(Authentication authentication) {
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private static AmountSnapshot snapshot(String cost, String paid) {
        BigDecimal c = new BigDecimal(cost);
        BigDecimal p = new BigDecimal(paid);
        return new AmountSnapshot(c, BigDecimal.ZERO, p, c.subtract(p), false);
    }
}
