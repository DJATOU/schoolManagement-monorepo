package com.school.management.service.correction;

import com.school.management.persistance.CorrectionAction;
import com.school.management.persistance.CorrectionDomain;
import com.school.management.persistance.CorrectionReasonType;
import com.school.management.persistance.EncashmentAllocationEntity;
import com.school.management.persistance.PaymentEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.service.exception.CustomServiceException;
import com.school.management.service.payment.EncashmentService;
import com.school.management.service.payment.PaymentAllocationResult;
import com.school.management.service.payment.PaymentCostResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * {@link CorrectionRunner} : l'Aperçu est la correction exécutée puis annulée (spec
 * admin-corrections, exigences 4.1 à 4.4, D7).
 *
 * <p>Sur une vraie base H2 et les vrais services d'encaissement : ce qui est vérifié, c'est
 * qu'un Aperçu n'écrit rien — ni annulation, ni cumul, ni numéro de reçu —, que la confirmation
 * écrit exactement ce qu'il annonçait, et qu'une confirmation sur des données qui ont bougé est
 * refusée avec le nouvel Aperçu. Les états sont relus en SQL, hors du code vérifié.</p>
 *
 * <p>Les corrections sont des commandes de test : l'annulation réelle et ses règles appartiennent
 * à B.3. Elles s'appuient sur la mécanique de neutralisation déjà livrée en A.4.</p>
 */
@DisplayName("CorrectionRunner — Aperçu et confirmation")
class CorrectionRunnerIntegrationTest extends CorrectionIntegrationTestSupport {

    @Autowired private CorrectionRunner runner;
    @Autowired private EncashmentService encashmentService;
    @Autowired private PaymentCostResolver costResolver;
    @Autowired private PlatformTransactionManager transactionManager;

    // ------------------------------------------------------------------
    // Aperçu
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Aperçu")
    class Apercu {

        @Test
        @DisplayName("annonce les montants avant et après, par Série touchée, et n'écrit rien")
        void announcesBeforeAndAfterAndWritesNothing() {
            attend(s1First);
            Long encashmentId = pay(s1, 3000).encashment().getId();
            Ledger before = ledger();

            CorrectionOutcome<String> outcome = runner.run(cancel(encashmentId), CorrectionMode.PREVIEW, null);

            assertThat(outcome.mode()).isEqualTo(CorrectionMode.PREVIEW);
            assertThat(outcome.result()).as("rien n'a été écrit : aucun résultat").isNull();
            assertThat(outcome.preview().amountsUnchanged()).isFalse();
            assertThat(outcome.preview().series()).singleElement().satisfies(change -> {
                assertThat(change.studentId()).isEqualTo(student.getId());
                assertThat(change.studentName()).isEqualTo("Amine Belkacem");
                assertThat(change.seriesId()).isEqualTo(s1.getId());
                assertThat(change.seriesName()).isEqualTo("Janvier");
                assertThat(change.groupName()).isEqualTo("Math 1ère A");
                // Une séance suivie : dû à ce jour 2 000. 3 000 versés : à jour ; après annulation, en retard.
                assertThat(change.before()).isEqualTo(snapshot("4000", "2000", "3000", "1000", false));
                assertThat(change.after()).isEqualTo(snapshot("4000", "2000", "0", "4000", true));
            });
            assertThat(outcome.preview().effects()).extracting(CorrectionEffect::type)
                    .containsExactly(CorrectionEffectType.ENCASHMENT_CANCELLED);
            assertThat(outcome.previewToken()).matches("[0-9a-f]{64}");

            assertThat(ledger()).as("l'Aperçu est annulé avec sa transaction").isEqualTo(before);
            assertThat(statusOf(encashmentId)).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("ne consomme aucun numéro de reçu : la confirmation reprend la séquence")
        void consumesNoReceiptNumber() {
            pay(s1, 1000);
            long rankBefore = lastRank();

            CorrectionOutcome<String> preview = runner.run(payCommand(s1, 500), CorrectionMode.PREVIEW, null);
            assertThat(lastRank()).isEqualTo(rankBefore);

            CorrectionOutcome<String> confirmed =
                    runner.run(payCommand(s1, 500), CorrectionMode.CONFIRM, preview.previewToken());
            assertThat(confirmed.result()).endsWith(String.format("-%04d", rankBefore + 1));
            assertThat(lastRank()).isEqualTo(rankBefore + 1);
        }

        @Test
        @DisplayName("dit explicitement qu'aucun montant ne change")
        void saysExplicitlyWhenNoAmountChanges() {
            pay(s1, 3000);

            CorrectionOutcome<String> outcome = runner.run(traceOnly("RIEN"), CorrectionMode.PREVIEW, null);

            assertThat(outcome.preview().amountsUnchanged()).isTrue();
            assertThat(outcome.preview().series()).isEmpty();
            assertThat(outcome.previewToken()).matches("[0-9a-f]{64}");
        }

        @Test
        @DisplayName("tait les Séries de la portée dont rien ne change")
        void omitsUnchangedSeriesOfTheScope() {
            pay(s1, 3000);
            pay(s2, 1000);
            Long onS2 = encashmentRepository.findAll().stream()
                    .filter(e -> e.getTargetSeries().getId().equals(s2.getId())).findFirst().orElseThrow().getId();

            CorrectionOutcome<String> outcome = runner.run(cancel(onS2), CorrectionMode.PREVIEW, null);

            assertThat(outcome.preview().series()).extracting(SeriesAmountChange::seriesId)
                    .containsExactly(s2.getId());
        }

        @Test
        @DisplayName("nomme une Série sans groupe sans échouer")
        void namesASeriesWithoutGroup() {
            SessionSeriesEntity orphan = seriesRepository.save(SessionSeriesEntity.builder()
                    .name("Hors groupe").totalSessions(1).serieTimeStart(date(2030, 1, 1)).build());

            CorrectionOutcome<String> outcome = runner.run(
                    directPayment(orphan, 700, CorrectionScope.empty().series(student.getId(), orphan.getId())),
                    CorrectionMode.PREVIEW, null);

            assertThat(outcome.preview().series()).singleElement().satisfies(change -> {
                assertThat(change.groupName()).isNull();
                assertThat(change.after().paid()).isEqualByComparingTo("700");
            });
        }

        @Test
        @DisplayName("un même état donne le même jeton ; une autre commande, un autre jeton")
        void tokenIsDeterministicAndBoundToTheCommand() {
            pay(s1, 3000);

            String first = runner.run(traceOnly("A"), CorrectionMode.PREVIEW, null).previewToken();
            String again = runner.run(traceOnly("A"), CorrectionMode.PREVIEW, null).previewToken();
            String other = runner.run(traceOnly("B"), CorrectionMode.PREVIEW, null).previewToken();

            assertThat(again).isEqualTo(first);
            assertThat(other).as("même Aperçu, autre commande").isNotEqualTo(first);
        }
    }

    // ------------------------------------------------------------------
    // Confirmation
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Confirmation")
    class Confirmation {

        @Test
        @DisplayName("écrit exactement ce que l'Aperçu annonçait")
        void writesExactlyWhatThePreviewAnnounced() {
            attend(s1First);
            Long encashmentId = pay(s1, 3000).encashment().getId();
            CorrectionOutcome<String> preview = runner.run(cancel(encashmentId), CorrectionMode.PREVIEW, null);

            CorrectionOutcome<String> confirmed =
                    runner.run(cancel(encashmentId), CorrectionMode.CONFIRM, preview.previewToken());

            assertThat(confirmed.mode()).isEqualTo(CorrectionMode.CONFIRM);
            assertThat(confirmed.result()).isEqualTo(receiptOf(encashmentId));
            assertThat(confirmed.preview()).isEqualTo(preview.preview());
            assertThat(confirmed.previewToken()).isEqualTo(preview.previewToken());
            assertThat(statusOf(encashmentId)).isEqualTo("CANCELLED");
            assertThat(AmountSnapshot.of(costResolver.resolve(student.getId(), s1.getId())))
                    .as("montants relus après validation = montants « après » de l'Aperçu")
                    .isEqualTo(preview.preview().series().get(0).after());
        }

        @Test
        @DisplayName("refuse un Aperçu périmé avec le nouvel Aperçu, sans rien écrire")
        void refusesAStalePreviewWithTheNewOne() {
            Long encashmentId = pay(s1, 3000).encashment().getId();
            CorrectionOutcome<String> preview = runner.run(cancel(encashmentId), CorrectionMode.PREVIEW, null);
            // Entre l'Aperçu et la confirmation, un autre versement arrive sur la même Série.
            pay(s1, 500);
            Ledger before = ledger();

            StalePreviewException stale = catchThrowableOfType(
                    () -> runner.run(cancel(encashmentId), CorrectionMode.CONFIRM, preview.previewToken()),
                    StalePreviewException.class);

            assertThat(stale.getStatus()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(stale.getPreviewToken()).isNotEqualTo(preview.previewToken());
            assertThat(stale.getPreview().series()).singleElement().satisfies(change -> {
                assertThat(change.before().paid()).isEqualByComparingTo("3500");
                assertThat(change.after().paid()).isEqualByComparingTo("500");
            });
            assertThat(ledger()).isEqualTo(before);
            assertThat(statusOf(encashmentId)).isEqualTo("ACTIVE");

            // Le nouvel Aperçu, relu, se confirme.
            runner.run(cancel(encashmentId), CorrectionMode.CONFIRM, stale.getPreviewToken());
            assertThat(statusOf(encashmentId)).isEqualTo("CANCELLED");
        }

        @Test
        @DisplayName("refuse un Aperçu dont seuls les effets ont changé, aucun montant ne bougeant")
        void refusesAPreviewWhoseEffectsChangedWithoutAnyAmount() {
            // Cas réel à venir (lot C) : la liste des absences à retirer s'allonge entre l'Aperçu et la
            // confirmation, sans qu'aucun montant ne change. L'administratrice n'a pas lu ce qui serait écrit.
            Long encashmentId = pay(s1, 3000).encashment().getId();
            CorrectionOutcome<String> preview =
                    runner.run(replaceNote(encashmentId, "Versement du père"), CorrectionMode.PREVIEW, null);
            assertThat(preview.preview().amountsUnchanged()).isTrue();
            jdbc.update("UPDATE encashment SET notes = 'Saisie par l''accueil' WHERE id = ?", encashmentId);

            assertThatThrownBy(() -> runner.run(replaceNote(encashmentId, "Versement du père"),
                    CorrectionMode.CONFIRM, preview.previewToken()))
                    .isInstanceOf(StalePreviewException.class);
            assertThat(jdbc.queryForObject("SELECT notes FROM encashment WHERE id = ?", String.class, encashmentId))
                    .isEqualTo("Saisie par l'accueil");
        }

        @Test
        @DisplayName("refuse un jeton obtenu pour une autre commande")
        void refusesATokenOfAnotherCommand() {
            Long first = pay(s1, 1000).encashment().getId();
            Long second = pay(s1, 1000).encashment().getId();
            String tokenForFirst = runner.run(cancel(first), CorrectionMode.PREVIEW, null).previewToken();

            assertThatThrownBy(() -> runner.run(cancel(second), CorrectionMode.CONFIRM, tokenForFirst))
                    .isInstanceOf(StalePreviewException.class);
            assertThat(statusOf(second)).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("exige le jeton de l'Aperçu")
        void requiresThePreviewToken() {
            Long encashmentId = pay(s1, 3000).encashment().getId();

            for (String missing : new String[] { null, "  " }) {
                CustomServiceException refused = catchThrowableOfType(
                        () -> runner.run(cancel(encashmentId), CorrectionMode.CONFIRM, missing),
                        CustomServiceException.class);
                assertThat(refused.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
            }
            assertThat(statusOf(encashmentId)).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("accepte un jeton entouré d'espaces")
        void acceptsATokenSurroundedBySpaces() {
            Long encashmentId = pay(s1, 3000).encashment().getId();
            String token = runner.run(cancel(encashmentId), CorrectionMode.PREVIEW, null).previewToken();

            runner.run(cancel(encashmentId), CorrectionMode.CONFIRM, " " + token + "\n");

            assertThat(statusOf(encashmentId)).isEqualTo("CANCELLED");
        }
    }

    // ------------------------------------------------------------------
    // Refus et erreurs de programmation
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Refus")
    class Refus {

        @Test
        @DisplayName("une correction refusée n'écrit rien, même ce qu'elle avait déjà écrit")
        void aRefusedCorrectionWritesNothing() {
            Long encashmentId = pay(s1, 3000).encashment().getId();
            Ledger before = ledger();

            for (CorrectionMode mode : CorrectionMode.values()) {
                CustomServiceException refused = catchThrowableOfType(
                        () -> runner.run(cancelThenRefuse(encashmentId), mode, "jeton"),
                        CustomServiceException.class);
                assertThat(refused.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                assertThat(ledger()).as(mode + " : rien n'est écrit").isEqualTo(before);
            }
            assertThat(statusOf(encashmentId)).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("une correction que la base refuserait échoue dès l'Aperçu, pas à la confirmation")
        void aCorrectionTheDatabaseWouldRefuseFailsInPreview() {
            // Sans écriture forcée avant la mesure, l'Aperçu serait annulé sans jamais atteindre la
            // base : il réussirait, et seule la confirmation échouerait, en erreur serveur.
            Long first = pay(s1, 1000).encashment().getId();
            Long second = pay(s1, 1000).encashment().getId();
            String taken = receiptOf(first);

            assertThatThrownBy(() -> runner.run(duplicateReceiptNumber(second, taken), CorrectionMode.PREVIEW, null))
                    .isInstanceOf(jakarta.persistence.PersistenceException.class);
            assertThat(receiptOf(second)).isNotEqualTo(taken);
        }

        @Test
        @DisplayName("une Série touchée hors de la portée déclarée est refusée : l'Aperçu la tairait")
        void touchingASeriesOutsideTheScopeIsRefused() {
            Ledger before = ledger();

            assertThatThrownBy(() -> runner.run(
                    directPayment(s2, 700, CorrectionScope.empty().series(student.getId(), s1.getId())),
                    CorrectionMode.PREVIEW, null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("hors de la portée");
            assertThat(ledger()).isEqualTo(before);
        }

        @Test
        @DisplayName("une correction qui ne change rien est refusée (exigence 11.3)")
        void aCorrectionThatChangesNothingIsRefused() {
            for (CorrectionMode mode : CorrectionMode.values()) {
                CustomServiceException refused = catchThrowableOfType(
                        () -> runner.run(noOp("RIEN"), mode, "jeton"), CustomServiceException.class);
                assertThat(refused.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(refused.getMessage()).contains("sans changement effectif");
            }
        }

        @Test
        @DisplayName("un montant changé sans trace est une erreur de programmation, et rien n'est écrit")
        void aChangeWithoutTraceIsRefused() {
            Long encashmentId = pay(s1, 3000).encashment().getId();
            Ledger before = ledger();

            assertThatThrownBy(() -> runner.run(untraced(cancel(encashmentId)), CorrectionMode.PREVIEW, null))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("sans trace");
            // Un effet seul, sans aucun montant changé, doit lui aussi être tracé.
            assertThatThrownBy(() -> runner.run(untraced(replaceNote(encashmentId, "Note")),
                    CorrectionMode.PREVIEW, null))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("sans trace");
            assertThat(ledger()).isEqualTo(before);
        }

        @Test
        @DisplayName("refuse d'être appelé dans une transaction déjà ouverte")
        void refusesToRunInsideAnOpenTransaction() {
            TransactionTemplate outer = new TransactionTemplate(transactionManager);

            assertThatThrownBy(() -> outer.executeWithoutResult(status ->
                    runner.run(noOp("RIEN"), CorrectionMode.PREVIEW, null)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("propre transaction");
        }
    }

    // ------------------------------------------------------------------
    // Traces (B.2)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Trace")
    class Trace {

        @Test
        @DisplayName("la confirmation écrit la trace : qui, quoi, motif, et l'effet mesuré sur les montants")
        void confirmationWritesTheTrace() {
            attend(s1First);
            Long encashmentId = pay(s1, 3000).encashment().getId();
            String token = runner.run(cancel(encashmentId), CorrectionMode.PREVIEW, null).previewToken();

            runner.run(cancel(encashmentId), CorrectionMode.CONFIRM, token);

            assertThat(jdbc.queryForList("SELECT * FROM correction_audit")).singleElement().satisfies(row -> {
                assertThat(row.get("DOMAIN")).isEqualTo("ENCASHMENT");
                assertThat(row.get("ACTION")).isEqualTo("ENCASHMENT_CANCELLED");
                assertThat(((Number) row.get("ENTITY_ID")).longValue()).isEqualTo(encashmentId);
                assertThat(((Number) row.get("STUDENT_ID")).longValue()).isEqualTo(student.getId());
                assertThat(((Number) row.get("GROUP_ID")).longValue()).isEqualTo(group.getId());
                assertThat(row.get("OLD_VALUE")).isEqualTo("{\"status\":\"ACTIVE\"}");
                assertThat(row.get("NEW_VALUE")).isEqualTo("{\"status\":\"CANCELLED\"}");
                assertThat(row.get("SUMMARY")).isEqualTo("Reçu " + receiptOf(encashmentId) + " annulé");
                assertThat(row.get("AMOUNT_EFFECT")).isEqualTo("Janvier (Math 1ère A) : versé 3 000,00 → 0,00 DA, "
                        + "reste 1 000,00 → 4 000,00 DA, à jour → en retard");
                assertThat(row.get("REASON_TYPE")).isEqualTo("WRONG_AMOUNT");
                assertThat(row.get("PERFORMED_BY")).as("l'administrateur authentifié").isEqualTo("admin-test");
                assertThat(row.get("PERFORMED_AT")).isNotNull();
            });
        }

        @Test
        @DisplayName("un Aperçu, un Aperçu périmé ou un refus ne laissent aucune trace")
        void noTraceWithoutConfirmation() {
            Long encashmentId = pay(s1, 3000).encashment().getId();
            String token = runner.run(cancel(encashmentId), CorrectionMode.PREVIEW, null).previewToken();
            pay(s1, 500);

            assertThatThrownBy(() -> runner.run(cancel(encashmentId), CorrectionMode.CONFIRM, token))
                    .isInstanceOf(StalePreviewException.class);
            assertThatThrownBy(() -> runner.run(cancelThenRefuse(encashmentId), CorrectionMode.CONFIRM, token))
                    .isInstanceOf(CustomServiceException.class);
            assertThat(count("SELECT COUNT(*) FROM correction_audit")).isZero();
        }

        @Test
        @DisplayName("sans administrateur authentifié, la correction est refusée et rien n'est écrit")
        void refusedWithoutAuthenticatedUser() {
            Long encashmentId = pay(s1, 3000).encashment().getId();
            SecurityContextHolder.clearContext();
            Ledger before = ledger();

            CustomServiceException refused = catchThrowableOfType(
                    () -> runner.run(cancel(encashmentId), CorrectionMode.PREVIEW, null), CustomServiceException.class);

            assertThat(refused.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(ledger()).isEqualTo(before);
        }

        @Test
        @DisplayName("des valeurs avant et après identiques sont refusées : aucune trace sans changement")
        void identicalValuesAreRefused() {
            Long encashmentId = pay(s1, 3000).encashment().getId();
            jdbc.update("UPDATE encashment SET notes = 'Versement du père' WHERE id = ?", encashmentId);
            Ledger before = ledger();

            CustomServiceException refused = catchThrowableOfType(
                    () -> runner.run(replaceNote(encashmentId, "Versement du père"), CorrectionMode.PREVIEW, null),
                    CustomServiceException.class);

            assertThat(refused.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(ledger()).isEqualTo(before);
        }

        @Test
        @DisplayName("le rang des traces suit l'ordre des corrections")
        void ranksFollowTheOrderOfCorrections() {
            Long first = pay(s1, 1000).encashment().getId();
            Long second = pay(s1, 1000).encashment().getId();

            runner.run(cancel(second), CorrectionMode.CONFIRM,
                    runner.run(cancel(second), CorrectionMode.PREVIEW, null).previewToken());
            runner.run(cancel(first), CorrectionMode.CONFIRM,
                    runner.run(cancel(first), CorrectionMode.PREVIEW, null).previewToken());

            assertThat(jdbc.queryForList("SELECT entity_id FROM correction_audit ORDER BY id", Long.class))
                    .containsExactly(second, first);
        }

        @Test
        @DisplayName("la trace survit à la disparition de ce qu'elle décrit, et reste lisible")
        void theTraceSurvivesTheData() {
            attend(s1First);
            Long encashmentId = pay(s1, 3000).encashment().getId();
            String receipt = receiptOf(encashmentId);
            runner.run(cancel(encashmentId), CorrectionMode.CONFIRM,
                    runner.run(cancel(encashmentId), CorrectionMode.PREVIEW, null).previewToken());

            // Tout ce que la trace désigne disparaît : encaissement, Série, groupe, étudiant.
            deleteDomainData();
            assertThat(count("SELECT COUNT(*) FROM encashment")).isZero();
            assertThat(count("SELECT COUNT(*) FROM student")).isZero();

            assertThat(jdbc.queryForList("SELECT summary, amount_effect FROM correction_audit"))
                    .singleElement().satisfies(row -> {
                        assertThat(row.get("SUMMARY")).isEqualTo("Reçu " + receipt + " annulé");
                        assertThat((String) row.get("AMOUNT_EFFECT")).startsWith("Janvier (Math 1ère A) : versé");
                    });
        }
    }

    // ------------------------------------------------------------------
    // Commandes de test
    // ------------------------------------------------------------------

    /** Annule un encaissement par la neutralisation livrée en A.4 ; rend son numéro de reçu. */
    private CorrectionCommand<String> cancel(Long encashmentId) {
        return new CorrectionCommand<>() {
            @Override
            public String fingerprint() {
                return "TEST_CANCEL|" + encashmentId;
            }

            @Override
            public CorrectionScope scope() {
                return CorrectionScope.empty().group(student.getId(), group.getId());
            }

            @Override
            public CorrectionExecution<String> execute() {
                Set<SeriesKey> touched = allocationRepository.findByEncashmentIdAndActiveTrueOrderByIdAsc(encashmentId)
                        .stream()
                        .map(EncashmentAllocationEntity::getSeries)
                        .map(series -> new SeriesKey(student.getId(), series.getId()))
                        .collect(Collectors.toSet());
                String receipt = encashmentService
                        .neutralize(encashmentId, CorrectionReason.of(CorrectionReasonType.WRONG_AMOUNT))
                        .getReceiptNumber();
                return new CorrectionExecution<>(receipt,
                        List.of(new CorrectionEffect(CorrectionEffectType.ENCASHMENT_CANCELLED,
                                "Reçu " + receipt + " annulé")),
                        touched,
                        List.of(trace(CorrectionAction.ENCASHMENT_CANCELLED, encashmentId,
                                Map.of("status", "ACTIVE"), Map.of("status", "CANCELLED"),
                                "Reçu " + receipt + " annulé")));
            }
        };
    }

    /** Annule, puis refuse : tout ce qui a été écrit doit disparaître avec la transaction. */
    private CorrectionCommand<String> cancelThenRefuse(Long encashmentId) {
        CorrectionCommand<String> cancel = cancel(encashmentId);
        return new CorrectionCommand<>() {
            @Override
            public String fingerprint() {
                return "TEST_REFUSE|" + encashmentId;
            }

            @Override
            public CorrectionScope scope() {
                return cancel.scope();
            }

            @Override
            public CorrectionExecution<String> execute() {
                cancel.execute();
                throw new CustomServiceException("Refus métier après écriture", HttpStatus.UNPROCESSABLE_ENTITY);
            }
        };
    }

    /** Encaisse par le chemin ordinaire ; rend le numéro de reçu attribué. */
    private CorrectionCommand<String> payCommand(SessionSeriesEntity series, double amount) {
        return new CorrectionCommand<>() {
            @Override
            public String fingerprint() {
                return "TEST_PAY|" + series.getId() + "|" + amount;
            }

            @Override
            public CorrectionScope scope() {
                return CorrectionScope.empty().group(student.getId(), group.getId());
            }

            @Override
            public CorrectionExecution<String> execute() {
                PaymentAllocationResult result = pay(series, amount);
                return new CorrectionExecution<>(result.encashment().getReceiptNumber(),
                        List.of(new CorrectionEffect(CorrectionEffectType.ENCASHMENT_CREATED,
                                "Versement de " + amount + " DA sur « " + series.getName() + " »")),
                        Set.of(new SeriesKey(student.getId(), series.getId())),
                        List.of(trace(CorrectionAction.ENCASHMENT_REPLACED, result.encashment().getId(),
                                null, Map.of("amount", amount), "Versement de " + amount + " DA")));
            }
        };
    }

    /** Écrit un cumul directement, sans Encaissement : pour éprouver la portée seule. */
    private CorrectionCommand<String> directPayment(SessionSeriesEntity series, double amount, CorrectionScope scope) {
        return new CorrectionCommand<>() {
            @Override
            public String fingerprint() {
                return "TEST_DIRECT|" + series.getId();
            }

            @Override
            public CorrectionScope scope() {
                return scope;
            }

            @Override
            public CorrectionExecution<String> execute() {
                PaymentEntity payment = paymentRepository.save(PaymentEntity.builder()
                        .student(student).sessionSeries(series).amountPaid(amount).status("PARTIAL").build());
                return new CorrectionExecution<>("ok", List.of(),
                        Set.of(new SeriesKey(student.getId(), series.getId())),
                        List.of(trace(CorrectionAction.ENCASHMENT_REPLACED, payment.getId(),
                                null, Map.of("amount", amount), "Cumul écrit directement")));
            }
        };
    }

    /** Donne à un encaissement le numéro d'un autre : refusé par l'index unique, au flush seulement. */
    private CorrectionCommand<String> duplicateReceiptNumber(Long encashmentId, String taken) {
        return new CorrectionCommand<>() {
            @Override
            public String fingerprint() {
                return "TEST_DUPLICATE|" + encashmentId;
            }

            @Override
            public CorrectionScope scope() {
                return CorrectionScope.empty().group(student.getId(), group.getId());
            }

            @Override
            public CorrectionExecution<String> execute() {
                encashmentRepository.findById(encashmentId).orElseThrow().setReceiptNumber(taken);
                return new CorrectionExecution<>("doublon", List.of(), Set.of(),
                        List.of(trace(CorrectionAction.ENCASHMENT_DETAILS_EDITED, encashmentId,
                                Map.of("receipt", "autre"), Map.of("receipt", taken), "Numéro dupliqué")));
            }
        };
    }

    /** Remplace la note d'un encaissement ; l'effet nomme l'ancienne note, lue en base. */
    private CorrectionCommand<String> replaceNote(Long encashmentId, String note) {
        return new CorrectionCommand<>() {
            @Override
            public String fingerprint() {
                return "TEST_NOTE|" + encashmentId + "|" + note;
            }

            @Override
            public CorrectionScope scope() {
                return CorrectionScope.empty().group(student.getId(), group.getId());
            }

            @Override
            public CorrectionExecution<String> execute() {
                var encashment = encashmentRepository.findById(encashmentId).orElseThrow();
                String old = encashment.getNotes() == null ? "aucune" : encashment.getNotes();
                encashment.setNotes(note);
                // Le type importe peu ici : seule la description, qui change avec la note lue, compte.
                return new CorrectionExecution<>(note,
                        List.of(new CorrectionEffect(CorrectionEffectType.ENCASHMENT_CREATED,
                                "Note « " + old + " » remplacée par « " + note + " »")),
                        Set.of(),
                        List.of(trace(CorrectionAction.ENCASHMENT_DETAILS_EDITED, encashmentId,
                                Map.of("notes", old), Map.of("notes", note), "Note corrigée")));
            }
        };
    }

    /** Ne change rien. */
    private CorrectionCommand<String> noOp(String fingerprint) {
        return new CorrectionCommand<>() {
            @Override
            public String fingerprint() {
                return fingerprint;
            }

            @Override
            public CorrectionScope scope() {
                return CorrectionScope.empty().group(student.getId(), group.getId());
            }

            @Override
            public CorrectionExecution<String> execute() {
                return new CorrectionExecution<>("rien", List.of(), Set.of(), List.of());
            }
        };
    }

    /** Ne change aucun montant et ne produit aucun effet, mais trace un changement de valeur. */
    private CorrectionCommand<String> traceOnly(String fingerprint) {
        return new CorrectionCommand<>() {
            @Override
            public String fingerprint() {
                return fingerprint;
            }

            @Override
            public CorrectionScope scope() {
                return CorrectionScope.empty().group(student.getId(), group.getId());
            }

            @Override
            public CorrectionExecution<String> execute() {
                return new CorrectionExecution<>("tracé", List.of(), Set.of(),
                        List.of(trace(CorrectionAction.ENCASHMENT_DETAILS_EDITED, 1L,
                                Map.of("v", "a"), Map.of("v", "b"), "Valeur corrigée")));
            }
        };
    }

    /** La même correction, privée de ses traces : un changement que personne ne retrouverait. */
    private CorrectionCommand<String> untraced(CorrectionCommand<String> command) {
        return new CorrectionCommand<>() {
            @Override
            public String fingerprint() {
                return command.fingerprint();
            }

            @Override
            public CorrectionScope scope() {
                return command.scope();
            }

            @Override
            public CorrectionExecution<String> execute() {
                CorrectionExecution<String> execution = command.execute();
                return new CorrectionExecution<>(execution.result(), execution.effects(), execution.touched(),
                        List.of());
            }
        };
    }

    private AuditDraft trace(CorrectionAction action, Long entityId, Map<String, ?> oldValue,
                             Map<String, ?> newValue, String summary) {
        return AuditDraft.builder()
                .domain(CorrectionDomain.ENCASHMENT).action(action).entityId(entityId)
                .studentId(student.getId()).groupId(group.getId())
                .oldValue(oldValue).newValue(newValue).summary(summary)
                .reason(CorrectionReason.of(CorrectionReasonType.WRONG_AMOUNT))
                .build();
    }
}
