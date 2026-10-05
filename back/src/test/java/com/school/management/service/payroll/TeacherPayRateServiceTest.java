package com.school.management.service.payroll;

import com.school.management.dto.payroll.TeacherPayRateDTO;
import com.school.management.dto.payroll.TeacherPayRateRequest;
import com.school.management.persistance.TeacherPayRateEntity;
import com.school.management.repository.TeacherPayRateRepository;
import com.school.management.service.exception.CustomServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Catalogue des taux de rémunération (spec teacher-payroll, exigence 1), sur H2. */
@DataJpaTest
@Import(TeacherPayRateService.class)
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
@DisplayName("Catalogue des taux de rémunération")
class TeacherPayRateServiceTest {

    @Autowired
    private TeacherPayRateService rates;

    @Autowired
    private TeacherPayRateRepository rateRepository;

    private static TeacherPayRateRequest request(String label, String percent) {
        return new TeacherPayRateRequest(label, percent == null ? null : new BigDecimal(percent));
    }

    private static HttpStatus statusOf(Throwable e) {
        return ((CustomServiceException) e).getStatus();
    }

    @Test
    @DisplayName("création : libellé nettoyé, pourcentage à deux décimales, part de l'école déduite, actif")
    void createsARate() {
        TeacherPayRateDTO created = rates.create(request("  Standard ", "60"));

        assertThat(created.id()).isNotNull();
        assertThat(created.label()).isEqualTo("Standard");
        assertThat(created.teacherPercent()).isEqualByComparingTo("60.00").hasScaleOf(2);
        assertThat(created.schoolPercent()).isEqualTo(new BigDecimal("40.00"));
        assertThat(created.active()).isTrue();
        assertThat(rateRepository.findById(created.id())).get()
                .extracting(TeacherPayRateEntity::getLabel).isEqualTo("Standard");
    }

    @Test
    @DisplayName("part de l'école au centime : 62,50 % → 37,50 %")
    void schoolPercentIsTheExactComplement() {
        assertThat(rates.create(request("Confirmé", "62.5")).schoolPercent()).isEqualTo(new BigDecimal("37.50"));
    }

    @ParameterizedTest(name = "{0} % refusé")
    @ValueSource(strings = {"0", "100", "-5", "100.01", "60.125"})
    @DisplayName("pourcentage hors de ]0 ; 100[ ou à trois décimales : 400, rien n'est créé")
    void invalidPercentIsRefused(String percent) {
        assertThatThrownBy(() -> rates.create(request("Taux", percent)))
                .isInstanceOf(CustomServiceException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThat(rateRepository.count()).isZero();
    }

    @Test
    @DisplayName("bornes intérieures acceptées : 0,01 % et 99,99 %")
    void innerBoundsAreAccepted() {
        assertThat(rates.create(request("Plancher", "0.01")).teacherPercent()).isEqualByComparingTo("0.01");
        assertThat(rates.create(request("Plafond", "99.99")).teacherPercent()).isEqualByComparingTo("99.99");
    }

    @Test
    @DisplayName("pourcentage manquant : 400, nommé")
    void missingPercentIsRefused() {
        assertThatThrownBy(() -> rates.create(request("Taux", null)))
                .hasMessageContaining("pourcentage de l'enseignant est obligatoire")
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("libellé vide ou trop long : 400 ; 100 caractères acceptés")
    void labelIsRequiredAndBounded() {
        assertThatThrownBy(() -> rates.create(request("   ", "60")))
                .hasMessageContaining("libellé du taux est obligatoire")
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> rates.create(request(null, "60")))
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> rates.create(request("x".repeat(101), "60")))
                .hasMessageContaining("100 caractères")
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.BAD_REQUEST));

        assertThat(rates.create(request("y".repeat(100), "60")).label()).hasSize(100);
    }

    @Test
    @DisplayName("libellé déjà porté par un taux actif, casse et espaces ignorés : 409")
    void activeLabelIsUnique() {
        rates.create(request("Standard", "60"));

        assertThatThrownBy(() -> rates.create(request(" STANDARD ", "65")))
                .hasMessageContaining("« STANDARD »")
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.CONFLICT));
        assertThat(rateRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("un taux désactivé libère son libellé")
    void disabledRateFreesItsLabel() {
        TeacherPayRateDTO old = rates.create(request("Standard", "60"));
        rates.disable(old.id());

        assertThat(rates.create(request("Standard", "65")).active()).isTrue();
    }

    @Test
    @DisplayName("modification : libellé et pourcentage changent ; garder son propre libellé est permis")
    void updatesAnActiveRate() {
        TeacherPayRateDTO rate = rates.create(request("Standard", "60"));

        TeacherPayRateDTO updated = rates.update(rate.id(), request("Standard", "55"));
        assertThat(updated.teacherPercent()).isEqualByComparingTo("55.00");
        assertThat(updated.schoolPercent()).isEqualByComparingTo("45.00");

        TeacherPayRateDTO renamed = rates.update(rate.id(), request("Débutant", "55"));
        assertThat(renamed.label()).isEqualTo("Débutant");
        assertThat(rateRepository.findById(rate.id())).get()
                .extracting(TeacherPayRateEntity::getTeacherPercent)
                .satisfies(p -> assertThat(p).isEqualByComparingTo("55.00"));
    }

    @Test
    @DisplayName("modification vers le libellé d'un autre taux actif : 409, rien ne change")
    void updateCannotTakeAnotherActiveLabel() {
        rates.create(request("Standard", "60"));
        TeacherPayRateDTO other = rates.create(request("Expert", "70"));

        assertThatThrownBy(() -> rates.update(other.id(), request("standard", "70")))
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.CONFLICT));
        assertThat(rateRepository.findById(other.id())).get()
                .extracting(TeacherPayRateEntity::getLabel).isEqualTo("Expert");
    }

    @Test
    @DisplayName("modification d'un taux désactivé : 409 ; valeur invalide : 400 ; introuvable : 404")
    void updateRefusals() {
        TeacherPayRateDTO rate = rates.create(request("Standard", "60"));
        TeacherPayRateDTO active = rates.create(request("Expert", "70"));
        rates.disable(rate.id());

        assertThatThrownBy(() -> rates.update(rate.id(), request("Standard", "65")))
                .hasMessageContaining("désactivé")
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.CONFLICT));
        assertThatThrownBy(() -> rates.update(active.id(), request("Expert", "100")))
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> rates.update(9_999L, request("Expert", "70")))
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("désactivation : n'est plus proposé ; une seconde désactivation est sans effet")
    void disableIsIdempotent() {
        TeacherPayRateDTO rate = rates.create(request("Standard", "60"));

        assertThat(rates.disable(rate.id()).active()).isFalse();
        assertThat(rates.disable(rate.id()).active()).isFalse();
        assertThat(rates.list()).singleElement().extracting(TeacherPayRateDTO::active).isEqualTo(false);
        assertThatThrownBy(() -> rates.disable(9_999L))
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("taux exigé pour une paie : actif rendu, désactivé 409, introuvable 404")
    void requireActive() {
        TeacherPayRateDTO active = rates.create(request("Standard", "60"));
        TeacherPayRateDTO disabled = rates.create(request("Ancien", "50"));
        rates.disable(disabled.id());

        assertThat(rates.requireActive(active.id()).getLabel()).isEqualTo("Standard");
        assertThatThrownBy(() -> rates.requireActive(disabled.id()))
                .hasMessageContaining("« Ancien » est désactivé")
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.CONFLICT));
        assertThatThrownBy(() -> rates.requireActive(9_999L))
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("liste : actifs d'abord, par pourcentage croissant")
    void listIsOrdered() {
        rates.create(request("Expert", "70"));
        TeacherPayRateDTO old = rates.create(request("Ancien", "40"));
        rates.create(request("Débutant", "50"));
        rates.disable(old.id());

        assertThat(rates.list()).extracting(TeacherPayRateDTO::label)
                .containsExactly("Débutant", "Expert", "Ancien");
    }
}
