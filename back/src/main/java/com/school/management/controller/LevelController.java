package com.school.management.controller;

import com.school.management.dto.LevelDto;
import com.school.management.mapper.LeveLMapper;
import com.school.management.persistance.LevelEntity;
import com.school.management.service.LevelService;
import com.school.management.service.exception.CustomServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/levels")
public class LevelController {
    private static final String LEVEL_NOT_FOUND_MESSAGE = "Level not found with id: ";

    private final LevelService levelService;
    private final LeveLMapper leveLMapper;

    @Autowired
    public LevelController(LevelService levelService, LeveLMapper leveLMapper) {
        this.levelService = levelService;
        this.leveLMapper = leveLMapper;
    }

    @GetMapping
    public ResponseEntity<List<LevelEntity>> getAllLevels() {

        return ResponseEntity.ok(levelService.getAllLevels());

    }

    @GetMapping("id/{id}")
    public ResponseEntity<LevelDto> getLevelById(@PathVariable Long id) {
        LevelEntity level = levelService.findById(id)
                .orElseThrow(() -> new CustomServiceException(LEVEL_NOT_FOUND_MESSAGE + id));
        return ResponseEntity.ok(leveLMapper.toDto(level));
    }


    @PostMapping
    public ResponseEntity<LevelEntity> createLevel(@RequestBody LevelEntity level) {
        return ResponseEntity.ok(levelService.createLevel(level));
    }

    //uodate level
    @PutMapping("/{id}")
    public ResponseEntity<LevelEntity> updateLevel(@PathVariable Long id, @RequestBody LevelEntity level) {
        return ResponseEntity.ok(levelService.updateLevel(id, level));
    }

    //descactivate levels
    @DeleteMapping("disable/{id_list}")
    public ResponseEntity<Boolean> disableLevels(@PathVariable String id_list) {
        System.out.println("Request recieved: " + id_list);
        for (String id : id_list.split(",")) {
            System.out.println("id: " + id);
            levelService.disableLevels(Long.parseLong(id));
        }
        return ResponseEntity.ok(true);
    }
}
