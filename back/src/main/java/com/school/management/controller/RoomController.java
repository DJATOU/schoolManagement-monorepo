package com.school.management.controller;

import com.school.management.persistance.RoomEntity;
import com.school.management.service.RoomService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService roomService;

    @Autowired
    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @GetMapping
    public ResponseEntity<List<RoomEntity>> getAllRooms() {
        return ResponseEntity.ok(roomService.getAllRooms());
    }

    @GetMapping("/{id}")
    public ResponseEntity<RoomEntity> getRoomById(@PathVariable Long id) {
        return ResponseEntity.ok(roomService.getRoomById(id));
    }

    @PostMapping
    public ResponseEntity<RoomEntity> createRoom(@RequestBody RoomEntity room) {
        return ResponseEntity.ok(roomService.createRoom(room));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RoomEntity> updateRoom(@PathVariable Long id, @RequestBody RoomEntity room) {
        return ResponseEntity.ok(roomService.updateRoom(id, room));
    }

    /*
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRoom(@PathVariable Long id) {
        roomService.deleteRoom(id);
        return ResponseEntity.noContent().build();
    }
    */

    //disable rooms
    @DeleteMapping("disable/{id_list}")
    public ResponseEntity<Boolean> disableRooms(@PathVariable String id_list) {
        System.out.println("Request recieved: " + id_list);
        for (String id : id_list.split(",")) {
            roomService.disableRooms(Long.parseLong(id));
        }
        return ResponseEntity.ok(true);
    }
}
