package com.school.management.persistance;

/**
 * Rôle d'un compte utilisateur, déterminant ses droits d'accès.
 *
 * <ul>
 *   <li>{@link #ADMIN} : accès complet (lecture + écriture : création, modification, suppression).</li>
 *   <li>{@link #VIEWER} : consultation uniquement (lecture seule ; écritures interdites).</li>
 * </ul>
 *
 * <p>Les autorités Spring Security sont dérivées par préfixe : {@code ROLE_ADMIN},
 * {@code ROLE_VIEWER}.</p>
 */
public enum Role {
    ADMIN,   // accès complet lecture + écriture
    VIEWER   // lecture seule
}
