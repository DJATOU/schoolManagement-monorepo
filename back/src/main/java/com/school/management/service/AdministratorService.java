package com.school.management.service;

import com.school.management.persistance.AdministratorEntity;
import com.school.management.persistance.RoleEntity;
import com.school.management.persistance.RoleEntity.RoleName;
import com.school.management.repository.AdministratorRepository;
import com.school.management.repository.RoleRepository;
import com.school.management.service.exception.CustomServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Service
@Transactional
public class AdministratorService {

    private final AdministratorRepository administratorRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public AdministratorService(AdministratorRepository administratorRepository,
                                RoleRepository roleRepository,
                                PasswordEncoder passwordEncoder) {
        this.administratorRepository = administratorRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Optional<AdministratorEntity> findByUsername(String username) {
        return Optional.ofNullable(administratorRepository.findByUsername(username));
    }

    public boolean existsByUsername(String username) {
        return administratorRepository.findByUsername(username) != null;
    }

    public AdministratorEntity save(AdministratorEntity administrator) {
        return administratorRepository.save(administrator);
    }

    public AdministratorEntity createAdministrator(AdministratorEntity administrator) {
        if (existsByUsername(administrator.getUsername())) {
            throw new CustomServiceException("Username already exists: " + administrator.getUsername());
        }

        if (administrator.getEmail() != null &&
            administratorRepository.findAll().stream()
                .anyMatch(admin -> admin.getEmail().equals(administrator.getEmail()))) {
            throw new CustomServiceException("Email already exists: " + administrator.getEmail());
        }

        // Hash the password before saving
        administrator.setPassword(passwordEncoder.encode(administrator.getPassword()));
        administrator.setActive(true);

        // Assign default ADMIN role
        RoleEntity adminRole = roleRepository.findByName(RoleName.ROLE_ADMIN)
                .orElseThrow(() -> new CustomServiceException("Role ROLE_ADMIN not found. Please initialize roles first."));

        Set<RoleEntity> roles = new HashSet<>();
        roles.add(adminRole);
        administrator.setRoles(roles);

        return administratorRepository.save(administrator);
    }

    public Optional<AdministratorEntity> findById(Long id) {
        return administratorRepository.findById(id);
    }

    public void deleteById(Long id) {
        administratorRepository.deleteById(id);
    }
}
