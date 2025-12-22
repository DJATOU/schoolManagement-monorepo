package com.school.management.config;

import com.school.management.persistance.RoleEntity;
import com.school.management.persistance.RoleEntity.RoleName;
import com.school.management.repository.RoleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);

    private final RoleRepository roleRepository;

    @Autowired
    public DataInitializer(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @Override
    public void run(String... args) {
        initializeRoles();
    }

    private void initializeRoles() {
        logger.info("Initializing roles...");

        createRoleIfNotExists(RoleName.ROLE_ADMIN, "Administrator - Full access to all features");
        createRoleIfNotExists(RoleName.ROLE_TEACHER, "Teacher - Manage courses, sessions, and grades");
        createRoleIfNotExists(RoleName.ROLE_STUDENT, "Student - View own information and courses");
        createRoleIfNotExists(RoleName.ROLE_PARENT, "Parent/Tutor - View children's information");

        logger.info("Roles initialized successfully");
    }

    private void createRoleIfNotExists(RoleName roleName, String description) {
        roleRepository.findByName(roleName).orElseGet(() -> {
            RoleEntity role = RoleEntity.builder()
                    .name(roleName)
                    .description(description)
                    .build();
            RoleEntity savedRole = roleRepository.save(role);
            logger.info("Created role: {}", roleName);
            return savedRole;
        });
    }
}
