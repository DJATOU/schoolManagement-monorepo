package com.school.management.domain.model.tutor;

public record Tutor(
        Long id,
        String firstName,
        String lastName,
        String email,
        String phoneNumber
) {}