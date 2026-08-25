package com.school.management.service;

import com.school.management.persistance.AttendanceEntity;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.PaymentEntity;
import com.school.management.persistance.SchoolYearEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.persistance.SessionSeriesEntity;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de propriété (jqwik) pour la règle de résolution de l'année scolaire des enregistrements
 * enfants (Exigence 3.4).
 *
 * <p>Aucune colonne d'année n'est stockée sur une série, une séance, un paiement ou une présence :
 * leur année est <b>résolue</b> en remontant jusqu'au groupe
 * ({@code session.sessionSeries.group.schoolYear}, {@code series.group.schoolYear}, etc.).
 * Cette propriété vérifie que, pour tout groupe portant une année scolaire et tout enfant
 * atteignable depuis lui, l'année résolue de l'enfant est exactement l'année du groupe.</p>
 */
class ChildYearInheritancePropertyTest {

    // ------------------------------------------------------------------
    // Property 10 — Child records inherit their Group's year
    // ------------------------------------------------------------------

    // Feature: school-year, Property 10: For any Group with a School Year and any Series/Session/payment/attendance reachable from it, the resolved School Year of that child equals the Group's School Year.
    @Property(tries = 100)
    void property10_childRecordsInheritGroupYear(
            @ForAll("schoolYears") SchoolYearEntity year,
            @ForAll("childKinds") ChildKind kind) {

        // Un groupe portant l'année scolaire générée.
        GroupEntity group = GroupEntity.builder().schoolYear(year).build();

        // Construit un enfant atteignable depuis ce groupe selon le type choisi, puis résout son
        // année en remontant le graphe d'objets (règle de résolution du design).
        SchoolYearEntity resolved = switch (kind) {
            case SERIES -> {
                SessionSeriesEntity series = SessionSeriesEntity.builder().group(group).build();
                yield resolveFromSeries(series);
            }
            case SESSION_VIA_SERIES -> {
                SessionSeriesEntity series = SessionSeriesEntity.builder().group(group).build();
                SessionEntity session = SessionEntity.builder().sessionSeries(series).build();
                yield resolveFromSession(session);
            }
            case SESSION_VIA_GROUP -> {
                SessionEntity session = SessionEntity.builder().group(group).build();
                yield resolveFromSession(session);
            }
            case ATTENDANCE_VIA_SESSION -> {
                SessionSeriesEntity series = SessionSeriesEntity.builder().group(group).build();
                SessionEntity session = SessionEntity.builder().sessionSeries(series).build();
                AttendanceEntity attendance = AttendanceEntity.builder().session(session).build();
                yield resolveFromAttendance(attendance);
            }
            case ATTENDANCE_VIA_GROUP -> {
                AttendanceEntity attendance = AttendanceEntity.builder().group(group).build();
                yield resolveFromAttendance(attendance);
            }
            case PAYMENT_VIA_SESSION -> {
                SessionSeriesEntity series = SessionSeriesEntity.builder().group(group).build();
                SessionEntity session = SessionEntity.builder().sessionSeries(series).build();
                PaymentEntity payment = PaymentEntity.builder().session(session).build();
                yield resolveFromPayment(payment);
            }
            case PAYMENT_VIA_GROUP -> {
                PaymentEntity payment = PaymentEntity.builder().group(group).build();
                yield resolveFromPayment(payment);
            }
        };

        // L'année résolue de l'enfant doit être exactement l'année du groupe.
        assertThat(resolved)
                .as("l'année résolue de l'enfant (type=%s) doit être celle du groupe", kind)
                .isSameAs(year);
        assertThat(resolved.getId())
                .as("identifiant d'année résolu (type=%s)", kind)
                .isEqualTo(group.getSchoolYear().getId());
    }

    // ------------------------------------------------------------------
    // Résolveurs : remontent le graphe d'objets jusqu'au groupe (design 3.4/3.5).
    // ------------------------------------------------------------------

    private static SchoolYearEntity resolveFromGroup(GroupEntity group) {
        return Optional.ofNullable(group).map(GroupEntity::getSchoolYear).orElse(null);
    }

    private static SchoolYearEntity resolveFromSeries(SessionSeriesEntity series) {
        return resolveFromGroup(Optional.ofNullable(series)
                .map(SessionSeriesEntity::getGroup)
                .orElse(null));
    }

    private static SchoolYearEntity resolveFromSession(SessionEntity session) {
        if (session == null) {
            return null;
        }
        // Résolution via la série (session.sessionSeries.group.schoolYear), avec repli sur le
        // groupe directement porté par la séance (session.group.schoolYear).
        SessionSeriesEntity series = session.getSessionSeries();
        if (series != null) {
            return resolveFromSeries(series);
        }
        return resolveFromGroup(session.getGroup());
    }

    private static SchoolYearEntity resolveFromAttendance(AttendanceEntity attendance) {
        if (attendance == null) {
            return null;
        }
        // Résolution via la séance, puis la série, puis repli sur le groupe.
        if (attendance.getSession() != null) {
            return resolveFromSession(attendance.getSession());
        }
        if (attendance.getSessionSeries() != null) {
            return resolveFromSeries(attendance.getSessionSeries());
        }
        return resolveFromGroup(attendance.getGroup());
    }

    private static SchoolYearEntity resolveFromPayment(PaymentEntity payment) {
        if (payment == null) {
            return null;
        }
        // Résolution via la séance, puis la série, puis repli sur le groupe.
        if (payment.getSession() != null) {
            return resolveFromSession(payment.getSession());
        }
        if (payment.getSessionSeries() != null) {
            return resolveFromSeries(payment.getSessionSeries());
        }
        return resolveFromGroup(payment.getGroup());
    }

    // ------------------------------------------------------------------
    // Générateurs
    // ------------------------------------------------------------------

    /** Années scolaires aléatoires (identifiant + libellé "YYYY-(YYYY+1)"). */
    @Provide
    Arbitrary<SchoolYearEntity> schoolYears() {
        Arbitrary<Long> ids = Arbitraries.longs().between(1L, 1_000_000L);
        Arbitrary<Integer> firstYears = Arbitraries.integers().between(2000, 2100);
        return Combinators.combine(ids, firstYears).as((id, firstYear) -> {
            SchoolYearEntity year = SchoolYearEntity.builder()
                    .label(firstYear + "-" + (firstYear + 1))
                    .startDate(new java.util.Date())
                    .endDate(new java.util.Date())
                    .isCurrent(false)
                    .build();
            year.setId(id);
            return year;
        });
    }

    /** Les différents types d'enfants atteignables depuis un groupe et leur chemin de résolution. */
    @Provide
    Arbitrary<ChildKind> childKinds() {
        return Arbitraries.of(ChildKind.class);
    }

    /** Type d'enregistrement enfant et chemin par lequel son année est résolue. */
    private enum ChildKind {
        SERIES,
        SESSION_VIA_SERIES,
        SESSION_VIA_GROUP,
        ATTENDANCE_VIA_SESSION,
        ATTENDANCE_VIA_GROUP,
        PAYMENT_VIA_SESSION,
        PAYMENT_VIA_GROUP
    }
}
