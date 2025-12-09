package com.school.management.repository;

import com.school.management.persistance.RoomEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoomRepository extends JpaRepository<RoomEntity, Long> {

    List<RoomEntity> findByCapacityGreaterThanEqual(Integer capacity);
    List<RoomEntity> findByNameContaining(String name);

    @Query("SELECT r.name FROM RoomEntity r WHERE r.id = :id")
    Optional<String> findRoomNameById(Long id);
    // Add other custom methods if needed
}
