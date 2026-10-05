package com.school.management.service.payroll;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lecture d'une violation de contrainte à l'enregistrement d'une paie : seul l'index des paies
 * initiales actives se traduit en « payée par ailleurs » ; toute autre violation est un défaut et
 * remonte telle quelle (vérifié sur PostgreSQL par {@code MigrationSchemaPostgresIntegrationTest}).
 */
@DisplayName("Paie : violation de contrainte à l'enregistrement")
class TeacherPayoutServiceTest {

    private static final String INDEX = TeacherPayoutService.INITIAL_ACTIVE_INDEX;

    @Test
    @DisplayName("l'index des paies initiales actives, nommé par le pilote en profondeur : reconnu")
    void initialActiveIndexIsRecognised() {
        DataIntegrityViolationException e = new DataIntegrityViolationException("could not execute statement",
                new RuntimeException("wrapper", new SQLException(
                        "ERROR: duplicate key value violates unique constraint \"" + INDEX + "\"")));

        assertThat(TeacherPayoutService.violates(e, INDEX)).isTrue();
    }

    @Test
    @DisplayName("une autre contrainte, ou aucun message : non reconnue")
    void otherViolationsAreNot() {
        DataIntegrityViolationException number = new DataIntegrityViolationException(
                "duplicate key value violates unique constraint \"uk_teacher_payout_number\"");
        DataIntegrityViolationException silent = new DataIntegrityViolationException(null, new SQLException());

        assertThat(TeacherPayoutService.violates(number, INDEX)).isFalse();
        assertThat(TeacherPayoutService.violates(silent, INDEX)).isFalse();
    }
}
