package com.school.management.repository;

import com.school.management.persistance.SessionSeriesEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SessionSeriesRepository extends JpaRepository<SessionSeriesEntity, Long> {


    @EntityGraph(attributePaths = {"sessions"})
    List<SessionSeriesEntity> findByGroupId(Long id);


}
