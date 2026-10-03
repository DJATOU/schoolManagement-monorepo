package com.school.management.service;

import com.school.management.domain.valueobject.EnrolmentWindow;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.SchoolYearEntity;
import com.school.management.service.exception.CustomServiceException;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;

/**
 * Règles de date communes à l'inscription et à sa correction (spec admin-corrections, exigences
 * 5.2, 5.3, 6.1).
 *
 * <p>Une seule définition des bornes : l'inscription et sa correction ne doivent pas accepter des
 * dates différentes, sans quoi une date refusée à la saisie passerait par la correction.</p>
 */
public final class EnrolmentDates {

    private EnrolmentDates() {
    }

    /**
     * Le jour tombe dans l'année scolaire du groupe, bornes comprises.
     *
     * <p>Une date future est admise : une famille inscrit en avance, un départ s'annonce. Une date
     * hors de l'année désignerait des séances d'une autre année, que ce groupe n'a pas. L'appelant
     * a déjà vérifié que l'année du groupe existe et qu'elle est l'année courante.</p>
     *
     * @param group le groupe
     * @param day   le jour à situer
     * @param what  ce qu'est ce jour, en tête de phrase : « La date d'arrivée », « La date de départ »
     * @throws CustomServiceException 400 nommant l'année et ses bornes
     */
    public static void assertWithinSchoolYear(GroupEntity group, LocalDate day, String what) {
        SchoolYearEntity year = group.getSchoolYear();
        LocalDate start = EnrolmentWindow.dayOf(year.getStartDate());
        LocalDate end = EnrolmentWindow.dayOf(year.getEndDate());
        if (day.isBefore(start) || day.isAfter(end)) {
            throw new CustomServiceException(
                    what + " du " + EnrolmentWindow.format(day)
                            + " est hors de l'année scolaire " + year.getLabel()
                            + " du groupe « " + group.getName() + " », qui va du "
                            + EnrolmentWindow.format(start) + " au " + EnrolmentWindow.format(end) + ".",
                    HttpStatus.BAD_REQUEST);
        }
    }
}
