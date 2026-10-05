package com.school.management.service;

import com.school.management.persistance.SessionEntity;
import com.school.management.service.exception.CustomServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Objects;

/**
 * Une séance ne se valide pas avant d'avoir commencé.
 *
 * <p>Trois séances avaient été validées, feuille de présence comprise, avant d'avoir eu lieu : la
 * validation n'était bornée par aucune date. Une séance validée compte comme suivie, entre dans le
 * montant dû à ce jour des élèves et rend sa série payable à l'enseignant ; la valider d'avance
 * fausse ces trois calculs.</p>
 *
 * <p>Le garde porte sur la <strong>transition</strong> vers « validée » et sur la feuille de
 * présence, qui en est la première étape. Il ne touche ni la dévalidation, ni la correction d'une
 * séance déjà validée. Une séance sans heure de début n'est pas bornée : on ne refuse pas sur une
 * date inconnue.</p>
 */
@Service
public class SessionStartGuard {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final Clock clock;

    public SessionStartGuard(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Vrai si la séance a une heure de début, et qu'elle n'est pas encore atteinte. */
    public boolean notStarted(SessionEntity session) {
        Date start = session == null ? null : session.getSessionTimeStart();
        return start != null && start.toInstant().isAfter(clock.instant());
    }

    /**
     * Refuse une séance qui n'a pas encore commencé.
     *
     * @throws CustomServiceException 409, nommant le jour et l'heure à partir desquels la séance se
     *                                valide
     */
    public void assertStarted(SessionEntity session) {
        if (!notStarted(session)) {
            return;
        }
        ZonedDateTime start = session.getSessionTimeStart().toInstant().atZone(ZoneId.systemDefault());
        throw new CustomServiceException("La séance du " + DAY.format(start) + " à " + TIME.format(start)
                + " n'a pas encore eu lieu : elle ne peut être validée qu'à partir de son heure de début.",
                HttpStatus.CONFLICT);
    }
}
