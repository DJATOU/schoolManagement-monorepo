package com.school.management.persistance;

/**
 * Portée d'application d'une réduction (discount).
 *
 * <p>Une réduction s'applique à un seul niveau de facturation à la fois. La
 * sélection de la réduction applicable se fait par ordre de spécificité :
 * {@link #SESSION} &gt; {@link #SERIES} &gt; {@link #GROUP}. Les portées ne sont
 * jamais cumulées.</p>
 */
public enum DiscountScope {

    /** Réduction appliquée au niveau du groupe. */
    GROUP,

    /** Réduction appliquée au niveau de la série. */
    SERIES,

    /** Réduction appliquée au niveau de la séance. */
    SESSION
}
