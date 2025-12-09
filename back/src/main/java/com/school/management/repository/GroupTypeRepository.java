package com.school.management.repository;

import com.school.management.persistance.GroupTypeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GroupTypeRepository extends JpaRepository<GroupTypeEntity, Long> {
    List<GroupTypeEntity> findByName(String name);
    List<GroupTypeEntity> findBySize(int size);

}
