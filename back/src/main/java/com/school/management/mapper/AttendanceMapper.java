package com.school.management.mapper;

import com.school.management.dto.AttendanceDTO;
import com.school.management.persistance.*;
import com.school.management.shared.exception.ResourceNotFoundException;
import com.school.management.shared.mapper.MappingContext;
import org.mapstruct.*;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = false))
public interface AttendanceMapper {

    @Mapping(source = "student.id", target = "studentId")
    @Mapping(source = "session.id", target = "sessionId")
    @Mapping(source = "sessionSeries.id", target = "sessionSeriesId")
    @Mapping(source = "group.id", target = "groupId")
    @Mapping(source = "missedSession.id", target = "missedSessionId")
    AttendanceDTO attendanceToAttendanceDTO(AttendanceEntity attendance);

    /**
     * {@code missedSession} était absent de ce mapping, et c'est l'un des trois verrous qui
     * produisaient le défaut de facturation : même si le client avait envoyé l'identifiant de la
     * séance manquée, MapStruct laissait le lien nul. Le qualificateur de rattrapage retombait
     * alors sur « aucune autre série ne facture cette séance » et le groupe d'accueil facturait,
     * silencieusement.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(source = "studentId", target = "student", qualifiedByName = "idToStudent")
    @Mapping(source = "sessionId", target = "session", qualifiedByName = "idToSession")
    @Mapping(source = "sessionSeriesId", target = "sessionSeries", qualifiedByName = "idToSessionSeries")
    @Mapping(source = "groupId", target = "group", qualifiedByName = "idToGroup")
    @Mapping(source = "missedSessionId", target = "missedSession", qualifiedByName = "idToSession")
    AttendanceEntity attendanceDTOToAttendance(AttendanceDTO attendanceDTO, @Context MappingContext context);

    @Named("idToStudent")
    default StudentEntity idToStudent(Long id, @Context MappingContext context) {
        if (id == null)
            return null;
        return context.getStudentRepository().findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Student", id));
    }

    @Named("idToSession")
    default SessionEntity idToSession(Long id, @Context MappingContext context) {
        if (id == null)
            return null;
        return context.getSessionRepository().findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Session", id));
    }

    @Named("idToSessionSeries")
    default SessionSeriesEntity idToSessionSeries(Long id, @Context MappingContext context) {
        if (id == null)
            return null;
        return context.getSessionSeriesRepository().findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SessionSeries", id));
    }

    @Named("idToGroup")
    default GroupEntity idToGroup(Long id, @Context MappingContext context) {
        if (id == null)
            return null;
        return context.getGroupRepository().findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Group", id));
    }
}
