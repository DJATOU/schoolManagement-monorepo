package com.school.management.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.school.management.dto.security.UserResponseDTO;
import com.school.management.persistance.Role;
import com.school.management.persistance.UserEntity;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test de propriété (jqwik) : le mot de passe est exclu des réponses de l'API.
 *
 * <p>Pour tout compte, le DTO produit par {@link UserMapper} puis sérialisé en JSON ne contient
 * ni le champ mot de passe ni sa valeur (hachée).</p>
 */
class UserMapperPropertyTest {

    private final UserMapper mapper = new UserMapperImpl();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Provide
    Arbitrary<String> texts() {
        return Arbitraries.strings().alpha().numeric().ofMinLength(1).ofMaxLength(30);
    }

    @Provide
    Arbitrary<Role> roles() {
        return Arbitraries.of(Role.ADMIN, Role.VIEWER);
    }

    // Feature: authentication-authorization, Property 7: Le mot de passe est exclu des réponses de l'API
    @Property(tries = 100)
    void passwordExcludedFromApiResponse(@ForAll("texts") String username,
                                         @ForAll("texts") String rawPassword,
                                         @ForAll("roles") Role role) throws Exception {
        // Valeur de mot de passe (hachée) distinctive pour la rechercher dans le JSON.
        String storedPassword = "HASH_" + rawPassword + "_HASH";
        UserEntity user = UserEntity.builder()
                .id(1L)
                .username(username)
                .password(storedPassword)
                .role(role)
                .enabled(true)
                .build();

        UserResponseDTO dto = mapper.toResponse(user);
        String json = objectMapper.writeValueAsString(dto);

        // Ni la clé "password" ni la valeur du mot de passe stocké n'apparaissent.
        assertThat(json).doesNotContain("password");
        assertThat(json).doesNotContain(storedPassword);
        // Le DTO reste correct par ailleurs.
        assertThat(dto.username()).isEqualTo(username);
        assertThat(dto.role()).isEqualTo(role);
    }
}
