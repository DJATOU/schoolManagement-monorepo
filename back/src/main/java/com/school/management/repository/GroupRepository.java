package com.school.management.repository;

import com.school.management.persistance.GroupEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface GroupRepository extends JpaRepository<GroupEntity, Long> {
    List<GroupEntity> findByStudents_Id(Long studentId);

    @Query("SELECT g FROM GroupEntity g " +
            "JOIN FETCH g.groupType " +
            "JOIN FETCH g.level " +
            "JOIN FETCH g.subject " +
            "JOIN FETCH g.price " +
            "JOIN FETCH g.teacher " +
            "WHERE g.id = :groupId")
    Optional<GroupEntity> findGroupWithDetailsById(@Param("groupId") Long groupId);

}
