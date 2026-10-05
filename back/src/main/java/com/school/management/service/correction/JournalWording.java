package com.school.management.service.correction;

import com.school.management.domain.valueobject.EnrolmentWindow;
import com.school.management.persistance.SessionEntity;
import com.school.management.service.session.RollCallService;

/**
 * Rédaction commune aux sources du Journal : une séance nommée par son jour et son groupe, jamais
 * par son identifiant (exigence 12.2).
 */
final class JournalWording {

    private JournalWording() {
    }

    /**
     * « 14/01/2030 (Math 1ère A) ».
     *
     * <p>Une séance tracée a toujours un jour et un groupe : la justification comme la décision de
     * rattrapage passent par le garde d'année, qui refuse une séance sans groupe.</p>
     */
    static String dayAndGroup(SessionEntity session) {
        return EnrolmentWindow.format(EnrolmentWindow.dayOf(session.getSessionTimeStart())) + " ("
                + RollCallService.groupOf(session).getName() + ")";
    }
}
