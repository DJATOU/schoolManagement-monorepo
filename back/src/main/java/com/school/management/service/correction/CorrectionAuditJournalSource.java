package com.school.management.service.correction;

import com.school.management.persistance.CorrectionAction;
import com.school.management.persistance.CorrectionAuditEntity;
import com.school.management.persistance.CorrectionDomain;
import com.school.management.repository.CorrectionAuditRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Les Traces des corrections (D8) : Encaissements, inscriptions, présences, rattrapages retirés.
 *
 * <p>Rien à rédiger : la phrase et l'effet sur le dû ont été écrits avec la correction, quand toutes
 * les données étaient là. Une Trace reste ainsi lisible après la disparition de ce qu'elle
 * décrit.</p>
 */
@Component
class CorrectionAuditJournalSource implements JournalSource {

    private final CorrectionAuditRepository repository;

    CorrectionAuditJournalSource(CorrectionAuditRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<Item> itemsOf(Long studentId) {
        return repository.findByStudentId(studentId).stream()
                .map(trace -> new Item(new JournalEntry(trace.getPerformedAt(), category(trace), trace.getSummary(),
                        trace.getAmountEffect(), trace.getReasonType(), trace.getReasonText(),
                        trace.getPerformedBy()), trace.getId()))
                .toList();
    }

    /** Un rattrapage retiré se range avec les rattrapages, toute autre présence avec les présences. */
    private static JournalCategory category(CorrectionAuditEntity trace) {
        if (trace.getDomain() == CorrectionDomain.ENCASHMENT) {
            return JournalCategory.ENCASHMENT;
        }
        if (trace.getDomain() == CorrectionDomain.ENROLMENT) {
            return JournalCategory.ENROLMENT;
        }
        return trace.getAction() == CorrectionAction.CATCH_UP_REMOVED ? JournalCategory.CATCH_UP
                : JournalCategory.ATTENDANCE;
    }
}
