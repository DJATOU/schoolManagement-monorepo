package com.school.management.service;

import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.repository.SessionSeriesRepository;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests de propriété (jqwik) pour {@link SeriesNamingService}.
 *
 * <p>Chaque propriété correspond à une propriété de correction du design
 * (payment-attendance-rules). Le repository est mocké (Mockito) afin que les 100+
 * itérations restent rapides.</p>
 */
class SeriesNamingServicePropertyTest {

    private static final long GROUP_ID = 42L;

    /** Regex de parsing du nom : "{group} - {MM}-{yyyy}-{NNN}" (neutre en langue). */
    private static final Pattern NAME_PATTERN =
            Pattern.compile("^(.+) - (\\d{2})-(\\d{4})-(\\d{3})$");

    // ------------------------------------------------------------------
    // Property 12 — Series name round-trip
    // ------------------------------------------------------------------

    // Feature: payment-attendance-rules, Property 12: For any group name, series start date, and sequence number, parsing the name produced by buildName recovers the original group name, month, year, and sequence number, and the sequence is always rendered zero-padded to three digits.
    @Property(tries = 100)
    void property12_seriesNameRoundTrip(
            @ForAll("groupName") String groupName,
            @ForAll @IntRange(min = 2000, max = 2100) int year,
            @ForAll @IntRange(min = 1, max = 12) int month,
            @ForAll @IntRange(min = 1, max = 999) int sequence) {

        SessionSeriesRepository repository = mock(SessionSeriesRepository.class);
        // On force nextSequenceNumber == sequence en fournissant (sequence - 1) séries
        // existantes dans le mois cible.
        Date seriesStart = dateAt(year, month);
        when(repository.findByGroupId(GROUP_ID))
                .thenReturn(seriesInMonth(year, month, sequence - 1));

        SeriesNamingService service = new SeriesNamingService(repository);

        GroupEntity group = new GroupEntity();
        group.setId(GROUP_ID);
        group.setName(groupName);

        String name = service.buildName(group, seriesStart);

        Matcher matcher = NAME_PATTERN.matcher(name);
        assertThat(matcher.matches())
                .as("Le nom doit correspondre au format attendu : %s", name)
                .isTrue();

        assertThat(matcher.group(1)).isEqualTo(groupName);
        assertThat(Integer.parseInt(matcher.group(2))).isEqualTo(month);
        assertThat(Integer.parseInt(matcher.group(3))).isEqualTo(year);
        assertThat(matcher.group(4)).hasSize(3);
        assertThat(Integer.parseInt(matcher.group(4))).isEqualTo(sequence);
    }

    /** Noms de groupe ne contenant pas le délimiteur " - " pour un parsing sans ambiguïté. */
    @Provide
    Arbitrary<String> groupName() {
        return Arbitraries.strings()
                .alpha().numeric().withChars(' ', '_', '.')
                .ofMinLength(1).ofMaxLength(20)
                .filter(s -> !s.contains(" - ") && !s.startsWith(" ") && !s.endsWith(" "));
    }

    // ------------------------------------------------------------------
    // Property 13 — Series sequence numbering
    // ------------------------------------------------------------------

    // Feature: payment-attendance-rules, Property 13: For any number N of existing series for a group within a given calendar month, the next sequence number assigned to a series created in that month is N + 1; for a series created in a different calendar month than the previous series, the sequence restarts at 1 (001) regardless of prior months' counts.
    @Property(tries = 100)
    void property13_seriesSequenceNumbering(
            @ForAll @IntRange(min = 0, max = 50) int existingInMonth,
            @ForAll @IntRange(min = 0, max = 20) int existingInOtherMonths,
            @ForAll @IntRange(min = 2000, max = 2100) int year,
            @ForAll @IntRange(min = 1, max = 12) int month) {

        List<SessionSeriesEntity> series = new ArrayList<>();
        // N séries dans le mois cible.
        series.addAll(seriesInMonth(year, month, existingInMonth));
        // Des séries dans une année DIFFÉRENTE (donc mois calendaire différent), qui ne
        // doivent jamais compter pour le mois cible.
        int otherYear = year + 1;
        series.addAll(seriesInMonth(otherYear, month, existingInOtherMonths));

        SessionSeriesRepository repository = mock(SessionSeriesRepository.class);
        when(repository.findByGroupId(GROUP_ID)).thenReturn(series);
        SeriesNamingService service = new SeriesNamingService(repository);

        // Le prochain numéro ne compte que les séries du mois calendaire cible : N + 1.
        int next = service.nextSequenceNumber(GROUP_ID, dateAt(year, month));
        assertThat(next).isEqualTo(existingInMonth + 1);

        // Restart : pour l'année différente (mois calendaire distinct), le compteur ne
        // dépend que des séries de ce mois-là (existingInOtherMonths), pas du mois cible.
        int nextOther = service.nextSequenceNumber(GROUP_ID, dateAt(otherYear, month));
        assertThat(nextOther).isEqualTo(existingInOtherMonths + 1);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Crée une Date au jour 15 du mois/année donné (milieu de mois, sans bord TZ). */
    private static Date dateAt(int year, int month) {
        LocalDate ld = LocalDate.of(year, month, 15);
        return Date.from(ld.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private static SessionSeriesEntity seriesAt(int year, int month) {
        SessionSeriesEntity s = new SessionSeriesEntity();
        s.setSerieTimeStart(dateAt(year, month));
        return s;
    }

    private static List<SessionSeriesEntity> seriesInMonth(int year, int month, int count) {
        List<SessionSeriesEntity> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(seriesAt(year, month));
        }
        return list;
    }
}
