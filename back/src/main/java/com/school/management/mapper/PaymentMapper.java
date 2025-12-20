package com.school.management.mapper;

import com.school.management.dto.PaymentDTO;
import com.school.management.persistance.*;
import com.school.management.shared.exception.ResourceNotFoundException;
import com.school.management.shared.mapper.MappingContext;
import org.mapstruct.*;

/**
 * Mapper pour PaymentEntity ↔ PaymentDTO
 *
 * PHASE 1 REFACTORING: Utilise MappingContext pour résoudre les relations
 */
@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = false))
public interface PaymentMapper {

    @Mapping(source = "student.id", target = "studentId")
    @Mapping(source = "session.id", target = "sessionId")
    @Mapping(source = "sessionSeries.id", target = "sessionSeriesId")
    @Mapping(source = "group.id", target = "groupId")
    @Mapping(source = "description", target = "paymentDescription")
    @Mapping(target = "amountOwed", ignore = true)
    @Mapping(target = "totalPaidForSeries", ignore = true)
    @Mapping(target = "totalSeriesCost", ignore = true)
    PaymentDTO toDto(PaymentEntity paymentEntity);

    @Mapping(source = "studentId", target = "student", qualifiedByName = "idToStudent")
    @Mapping(source = "sessionId", target = "session", qualifiedByName = "idToSession")
    @Mapping(source = "sessionSeriesId", target = "sessionSeries", qualifiedByName = "idToSessionSeries")
    @Mapping(source = "groupId", target = "group", qualifiedByName = "idToGroup")
    @Mapping(source = "paymentDescription", target = "description")
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "dateCreation", ignore = true)
    @Mapping(target = "dateUpdate", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "paymentDate", ignore = true)
    @Mapping(target = "paymentDetails", ignore = true)
    PaymentEntity toEntity(PaymentDTO paymentDTO, @Context MappingContext context);

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