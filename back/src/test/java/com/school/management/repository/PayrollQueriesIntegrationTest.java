package com.school.management.repository;

import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.persistance.TeacherPayRateEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Requêtes de la Paie des enseignants, sur H2 (spec teacher-payroll). */
@DataJpaTest
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
class PayrollQueriesIntegrationTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private TeacherPayRateRepository rateRepository;

    private SessionSeriesEntity series(GroupEntity group, String name) {
        return em.persist(SessionSeriesEntity.builder().name(name).group(group).serieTimeStart(new Date()).build());
    }

    /**
     * Séance d'une série. {@code BaseEntity.onCreate} force {@code active = true} : une séance
     * désactivée est donc repassée par une mise à jour, qui ne déclenche pas le callback.
     */
    private void session(GroupEntity group, SessionSeriesEntity series, Boolean finished, boolean active) {
        SessionEntity session = em.persist(SessionEntity.builder()
                .title("Séance").group(group).sessionSeries(series).isFinished(finished)
                .sessionTimeStart(new Date()).build());
        if (!active) {
            em.flush();
            em.getEntityManager().createQuery("UPDATE SessionEntity s SET s.active = false WHERE s.id = :id")
                    .setParameter("id", session.getId()).executeUpdate();
        }
    }

    @Test
    @DisplayName("avancement : séances actives et validées par série ; désactivées ignorées ; non validée ≠ nulle")
    void completionCounts() {
        GroupEntity group = em.persist(GroupEntity.builder().name("Maths 4 AM A").build());
        SessionSeriesEntity done = series(group, "Octobre");
        SessionSeriesEntity ongoing = series(group, "Novembre");
        SessionSeriesEntity empty = series(group, "Décembre");
        session(group, done, true, true);
        session(group, done, true, true);
        session(group, done, false, false);   // désactivée : ni comptée ni attendue
        session(group, ongoing, true, true);
        session(group, ongoing, false, true);
        session(group, ongoing, null, true);  // jamais validée
        em.flush();
        em.clear();

        Map<Long, long[]> counts = new HashMap<>();
        for (Object[] row : sessionRepository.countCompletionBySeries(
                List.of(done.getId(), ongoing.getId(), empty.getId()))) {
            counts.put((Long) row[0], new long[]{((Number) row[1]).longValue(), ((Number) row[2]).longValue()});
        }

        assertThat(counts.get(done.getId())).containsExactly(2, 2);
        assertThat(counts.get(ongoing.getId())).containsExactly(3, 1);
        assertThat(counts).doesNotContainKey(empty.getId());
    }

    private TeacherPayRateEntity rate(String label, String percent) {
        return em.persist(TeacherPayRateEntity.builder().label(label).teacherPercent(new BigDecimal(percent)).build());
    }

    @Test
    @DisplayName("taux : libellé actif déjà pris, casse et espaces ignorés ; l'exclusion de soi-même et les inactifs")
    void activeLabelLookup() {
        TeacherPayRateEntity standard = rate("Standard", "60.00");
        TeacherPayRateEntity old = rate("Ancien", "50.00");
        em.flush();
        old.setActive(false);
        em.flush();

        assertThat(rateRepository.existsActiveLabel("  standard ", -1L)).isTrue();
        assertThat(rateRepository.existsActiveLabel("Standard", standard.getId())).isFalse();
        assertThat(rateRepository.existsActiveLabel("ancien", -1L)).isFalse();
        assertThat(rateRepository.existsActiveLabel("Confirmé", -1L)).isFalse();
    }

    @Test
    @DisplayName("taux : actifs d'abord, puis par pourcentage")
    void ratesOrdered() {
        TeacherPayRateEntity high = rate("Expert", "70.00");
        TeacherPayRateEntity low = rate("Débutant", "50.00");
        TeacherPayRateEntity disabled = rate("Ancien", "40.00");
        em.flush();
        disabled.setActive(false);
        em.flush();
        em.clear();

        assertThat(rateRepository.findAllOrdered()).extracting(TeacherPayRateEntity::getId)
                .containsExactly(low.getId(), high.getId(), disabled.getId());
    }
}
