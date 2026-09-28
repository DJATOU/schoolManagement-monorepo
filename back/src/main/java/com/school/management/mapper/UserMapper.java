package com.school.management.mapper;

import com.school.management.dto.security.UserResponseDTO;
import com.school.management.persistance.UserEntity;
import org.mapstruct.Mapper;

/**
 * Mapper MapStruct {@link UserEntity} → {@link UserResponseDTO}.
 *
 * <p>Direction unique (entité → DTO de réponse). Le {@link UserResponseDTO} ne comporte
 * <strong>aucun champ mot de passe</strong> : le mot de passe (haché) n'est donc jamais mappé
 * ni exposé. Le hachage est réalisé dans {@code UserAccountService} ; ce mapper ne manipule
 * jamais de mot de passe en clair.</p>
 */
@Mapper(componentModel = "spring")
public interface UserMapper {

    UserResponseDTO toResponse(UserEntity entity);
}
