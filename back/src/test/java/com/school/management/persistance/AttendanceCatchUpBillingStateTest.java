package com.school.management.persistance;

import com.school.management.repository.AttendanceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Nouveaux champs de facturation du rattrapage sur {@link AttendanceEntity}.
 *
 * <p>Ce que ces tests protègent n'est pas la persistance d'une colonne — Hibernate s'en charge —
 * mais deux <strong>propriétés du modèle</strong> qu'une simplification bien intentionnée casserait
 * facilement :</p>
 * <ul>
 *   <li>une présence ordinaire n'a <strong>aucun</strong> état de facturation : ajouter un état par
 *       défaut ferait entrer toutes les présences du projet dans la logique de rattrapage ;</li>
 *   <li>« déjà payée » distingue <strong>trois</strong> valeurs et non deux : vrai, faux, et
 *       <em>pas encore décidé</em>. Remplacer {@code Boolean} par un {@code boolean} primitif, ou
 *       poser un défaut en base, supprimerait la troisième — c'est-à-dire déciderait à la place de
 *       l'administrateur, ce que ce champ existe précisément pour empêcher.</li>
 * </ul>
 */
@DataJpaTest
class AttendanceCatchUpBillingStateTest {

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Test
    @DisplayName("Une présence ordinaire ne porte aucun état de facturation de rattrapage")
    void ordinaryAttendanceCarriesNoCatchUpBillingState() {
        AttendanceEntity saved = attendanceRepository.save(
                AttendanceEntity.builder().isPresent(true).build());

        assertThat(saved.getCatchUpBillingState())
                .as("une présence ordinaire n'est pas un rattrapage : aucun état ne doit être posé")
                .isNull();
        assertThat(saved.getMissedSessionAlreadyPaid())
                .as("aucune décision de facturation ne concerne une présence ordinaire")
                .isNull();
    }

    @Test
    @DisplayName("« Déjà payée » admet un état non tranché, distinct de vrai et de faux")
    void alreadyPaidSupportsUndecidedState() {
        AttendanceEntity pending = attendanceRepository.save(AttendanceEntity.builder()
                .isPresent(true)
                .isCatchUp(true)
                .catchUpBillingState(CatchUpBillingState.PENDING)
                .build());

        // Non tranché : c'est ce que PENDING signifie, et c'est ce qui interdit à un défaut
        // implicite de s'installer.
        assertThat(pending.getMissedSessionAlreadyPaid())
                .as("un rattrapage à préciser n'a pas encore de décision")
                .isNull();

        pending.setMissedSessionAlreadyPaid(false);
        AttendanceEntity toBill = attendanceRepository.saveAndFlush(pending);
        assertThat(toBill.getMissedSessionAlreadyPaid())
                .as("« à facturer » est une décision prise, à ne pas confondre avec l'absence de décision")
                .isFalse();

        toBill.setMissedSessionAlreadyPaid(true);
        assertThat(attendanceRepository.saveAndFlush(toBill).getMissedSessionAlreadyPaid()).isTrue();
    }

    @Test
    @DisplayName("Les trois états de facturation sont persistés et relus fidèlement")
    void allBillingStatesRoundTrip() {
        for (CatchUpBillingState state : CatchUpBillingState.values()) {
            AttendanceEntity saved = attendanceRepository.saveAndFlush(AttendanceEntity.builder()
                    .isPresent(true)
                    .isCatchUp(true)
                    .catchUpBillingState(state)
                    .build());

            assertThat(saved.getCatchUpBillingState())
                    .as("l'état %s doit être relu à l'identique", state)
                    .isEqualTo(state);
        }
    }

    @Test
    @DisplayName("Les champs existants de la présence ne sont pas altérés par les nouveaux")
    void existingFieldsRemainUntouched() {
        AttendanceEntity saved = attendanceRepository.saveAndFlush(AttendanceEntity.builder()
                .isPresent(false)
                .isJustified(true)
                .isCatchUp(true)
                .catchUpBillingState(CatchUpBillingState.HOST_BILLED)
                .build());

        assertThat(saved.getIsPresent()).isFalse();
        assertThat(saved.getIsJustified()).isTrue();
        assertThat(saved.getIsCatchUp()).isTrue();
        // catchUpRight vaut vrai par défaut, indépendamment de la justification : la nouvelle
        // colonne ne doit pas avoir déplacé ce défaut.
        assertThat(saved.getCatchUpRight()).isTrue();
    }
}
