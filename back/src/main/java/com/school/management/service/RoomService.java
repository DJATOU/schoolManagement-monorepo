package com.school.management.service;

import com.school.management.persistance.BaseEntity;
import com.school.management.persistance.RoomEntity;
import com.school.management.repository.RoomRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class RoomService {

    private final RoomRepository roomRepository;

    @Autowired
    public RoomService(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    public List<RoomEntity> getAllRooms() {
        return roomRepository.findAll().stream().filter(BaseEntity::isActive).toList();
    }

    public RoomEntity getRoomById(Long id) {
        return roomRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Room not found")); // Customize this exception
    }

    public RoomEntity createRoom(RoomEntity room) {
        return roomRepository.save(room);
    }
    @Transactional
    public RoomEntity updateRoom(Long id, RoomEntity updatedRoom) {
        RoomEntity existingRoom = getRoomById(id);
        existingRoom.setName(updatedRoom.getName());
        existingRoom.setCapacity(updatedRoom.getCapacity());
        // Update other fields if present
        return roomRepository.save(existingRoom);
    }


    /*public void deleteRoom(Long id) {
        roomRepository.deleteById(id);
    }*/


    public void disableRooms(long id) {
        RoomEntity room = getRoomById(id);
        room.setActive(false);
        roomRepository.save(room);
    }
}
