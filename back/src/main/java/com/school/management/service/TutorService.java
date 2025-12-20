package com.school.management.service;

import com.school.management.persistance.TutorEntity;
import com.school.management.repository.TutorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class TutorService {

    private final TutorRepository tutorRepository;

    @Autowired
    public TutorService(TutorRepository tutorRepository) {
        this.tutorRepository = tutorRepository;
    }

    public List<TutorEntity> getAllTutors() {
        return tutorRepository.findAll();
    }

    public TutorEntity getTutorById(Long id) {
        return tutorRepository.findById(Objects.requireNonNull(id))
                .orElseThrow(() -> new RuntimeException("Tutor not found")); // Customize this exception
    }

    public TutorEntity createTutor(TutorEntity tutor) {
        return tutorRepository.save(Objects.requireNonNull(tutor));
    }

    public TutorEntity updateTutor(Long id) {
        TutorEntity existingTutor = getTutorById(id);
        // Update properties of existingTutor using values from tutor
        return tutorRepository.save(Objects.requireNonNull(existingTutor));
    }

    public void deleteTutor(Long id) {
        tutorRepository.deleteById(Objects.requireNonNull(id));
    }

    // Additional methods as needed...
}
