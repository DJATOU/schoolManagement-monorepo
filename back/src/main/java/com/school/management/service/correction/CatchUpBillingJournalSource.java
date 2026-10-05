package com.school.management.service.correction;

import com.school.management.persistance.AttendanceEntity;
import com.school.management.persistance.CatchUpBillingAuditEntity;
import com.school.management.persistance.CatchUpBillingAuditField;
import com.school.management.repository.AttendanceRepository;
import com.school.management.repository.CatchUpBillingAuditRepository;
import com.school.management.repository.SessionRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Adaptateur de {@code catch_up_billing_audit} (D8) : les décisions qui rendent un rattrapage
 * facturable ou non. Une ligne par champ changé :
 *
 * <ul>
 *   <li>« Rattrapage du 09/01/2030 (Math 1ère B) : séance manquée aucune → 07/01/2030 (Math 1ère A) » ;</li>
 *   <li>« Rattrapage du 09/01/2030 (Math 1ère B) : déjà payée non tranché → oui ».</li>
 * </ul>
 *
 * <p>La table garde la séance manquée par son identifiant, en texte : elle est relue pour être nommée,
 * « supprimée » si elle n'existe plus. L'effet sur le dû n'a pas été mesuré à l'écriture : il reste
 * vide plutôt que reconstitué après coup.</p>
 */
@Component
class CatchUpBillingJournalSource implements JournalSource {

    private final AttendanceRepository attendanceRepository;
    private final CatchUpBillingAuditRepository auditRepository;
    private final SessionRepository sessionRepository;

    CatchUpBillingJournalSource(AttendanceRepository attendanceRepository,
                                CatchUpBillingAuditRepository auditRepository,
                                SessionRepository sessionRepository) {
        this.attendanceRepository = attendanceRepository;
        this.auditRepository = auditRepository;
        this.sessionRepository = sessionRepository;
    }

    @Override
    public List<Item> itemsOf(Long studentId) {
        Map<Long, AttendanceEntity> attendances = attendanceRepository.findByStudentId(studentId).stream()
                .collect(Collectors.toMap(AttendanceEntity::getId, Function.identity()));
        return auditRepository.findByAttendanceIdIn(attendances.keySet()).stream()
                .map(audit -> item(audit, attendances.get(audit.getAttendanceId())))
                .toList();
    }

    private Item item(CatchUpBillingAuditEntity audit, AttendanceEntity attendance) {
        String what = audit.getField() == CatchUpBillingAuditField.MISSED_SESSION
                ? "séance manquée " + missed(audit.getOldValue()) + " → " + missed(audit.getNewValue())
                : "déjà payée " + paid(audit.getOldValue()) + " → " + paid(audit.getNewValue());
        String description = "Rattrapage du " + JournalWording.dayAndGroup(attendance.getSession()) + " : " + what;
        return new Item(new JournalEntry(audit.getPerformedAt(), JournalCategory.CATCH_UP, description, null, null,
                audit.getComment(), audit.getPerformedBy()), audit.getId());
    }

    /** La séance manquée désignée : aucune, son jour et son groupe, ou supprimée depuis. */
    private String missed(String sessionId) {
        if (sessionId == null) {
            return "aucune";
        }
        return sessionRepository.findById(Long.valueOf(sessionId))
                .map(JournalWording::dayAndGroup)
                .orElse("supprimée");
    }

    /** Valeur nulle : la décision n'était pas prise, ce que l'état « non tranché » exprime. */
    private static String paid(String alreadyPaid) {
        if (alreadyPaid == null) {
            return "non tranché";
        }
        return Boolean.parseBoolean(alreadyPaid) ? "oui" : "non";
    }
}
