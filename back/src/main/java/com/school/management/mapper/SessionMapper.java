package com.school.management.mapper;

import com.school.management.dto.session.SessionDTO;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.RoomEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.persistance.TeacherEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.shared.exception.ResourceNotFoundException;
import com.school.management.shared.mapper.MappingContext;
import org.mapstruct.*;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = false))
public interface SessionMapper {

    @Mapping(source = "group.id", target = "groupId")
    @Mapping(source = "group.name", target = "groupName")
    @Mapping(source = "teacher.id", target = "teacherId")
    @Mapping(source = "teacher", target = "teacherName", qualifiedByName = "formatTeacherName")
    @Mapping(source = "room.id", target = "roomId")
    @Mapping(source = "room.name", target = "roomName")
    @Mapping(source = "sessionSeries.id", target = "sessionSeriesId")
    @Mapping(source = "sessionSeries.name", target = "seriesName")
    @Mapping(source = "isFinished", target = "isFinished")
    SessionDTO sessionEntityToSessionDto(SessionEntity entity);

    @Mapping(source = "groupId", target = "group", qualifiedByName = "idToGroup")
    @Mapping(source = "teacherId", target = "teacher", qualifiedByName = "idToTeacher")
    @Mapping(source = "roomId", target = "room", qualifiedByName = "idToRoom")
    @Mapping(source = "sessionSeriesId", target = "sessionSeries", qualifiedByName = "idToSeries")
    @Mapping(source = "isFinished", target = "isFinished")
    @Mapping(target = "dateCreation", ignore = true)
    @Mapping(target = "dateUpdate", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "description", ignore = true)
    @Mapping(target = "paymentDetails", ignore = true)
    @Mapping(target = "attendances", ignore = true)
    SessionEntity sessionDtoToSessionEntity(SessionDTO dto, @Context MappingContext context);

    @Named("idToGroup")
    default GroupEntity idToGroup(Long id, @Context MappingContext context) {
        if (id == null) return null;
        return context.getGroupRepository().findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Group", id));
    }

    @Named("idToTeacher")
    default TeacherEntity idToTeacher(Long id, @Context MappingContext context) {
        if (id == null) return null;
        return context.getTeacherRepository().findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher", id));
    }

    @Named("idToRoom")
    default RoomEntity idToRoom(Long id, @Context MappingContext context) {
        if (id == null) return null;
        return context.getRoomRepository().findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Room", id));
    }

    @Named("idToSeries")
    default SessionSeriesEntity idToSeries(Long id, @Context MappingContext context) {
        if (id == null) return null;
        return context.getSessionSeriesRepository().findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SessionSeries", id));
    }

    @Named("formatTeacherName")
    default String formatTeacherName(TeacherEntity teacher) {
        if (teacher != null) {
            return teacher.getFirstName() + " " + teacher.getLastName();
        }
        return null;
    }
}
