package com.school.management.service;

import com.school.management.persistance.LevelEntity;
import com.school.management.repository.LevelRepository;
import com.school.management.service.exception.CustomServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class LevelService {

    private final LevelRepository levelRepository;

    @Autowired
    public LevelService(LevelRepository levelRepository) {
        this.levelRepository = levelRepository;
    }

    public List<LevelEntity> getAllLevels() {
        return levelRepository.findAll().stream().filter(LevelEntity::isActive).toList();
    }

    @Transactional(readOnly = true)
    public Optional<LevelEntity> findById(Long id) {
        try {
            return levelRepository.findById(id);
        } catch (DataAccessException e) {
            throw new CustomServiceException("Error fetching level with ID " + id, e);
        }
    }

    public LevelEntity createLevel(LevelEntity level) {
        return levelRepository.save(level);
    }

    public LevelEntity updateLevel(Long id, LevelEntity level) {
        LevelEntity levelToUpdate = levelRepository.findById(id).orElseThrow();
        levelToUpdate.setName(level.getName());
        return levelRepository.save(levelToUpdate);
    }

    public void disableLevels(Long id) {
        levelRepository.findById(id).ifPresent(level -> {
            level.setActive(false);
            levelRepository.save(level);
        });
    }
}
