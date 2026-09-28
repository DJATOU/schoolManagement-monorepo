package com.school.management.persistance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

/**
 * Réduction (discount) accordée à un étudiant sur un niveau de facturation précis.
 *
 * <p>Une réduction s'applique à une seule portée à la fois ({@link DiscountScope}) :
 * groupe, série ou séance. Exactement un des identifiants
 * {@link #groupId} / {@link #seriesId} / {@link #sessionId} doit être renseigné et
 * doit correspondre à la portée déclarée dans {@link #scope}.</p>
 *
 * <p>Le taux ({@link #rate}) est exprimé dans l'intervalle {@code [0.00, 1.00]}
 * (par exemple {@code 0.25} pour une réduction de 25 %).</p>
 */
@Entity
@Table(name = "discount")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class DiscountEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // L'étudiant bénéficiaire de la réduction
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id")
    private StudentEntity student;

    // Portée d'application de la réduction (GROUP, SERIES, SESSION)
    @Enumerated(EnumType.STRING)
    @Column(name = "scope")
    private DiscountScope scope;

    // Identifiant du groupe visé (renseigné uniquement si scope == GROUP)
    @Column(name = "group_id")
    private Long groupId;

    // Identifiant de la série visée (renseigné uniquement si scope == SERIES)
    @Column(name = "series_id")
    private Long seriesId;

    // Identifiant de la séance visée (renseigné uniquement si scope == SESSION)
    @Column(name = "session_id")
    private Long sessionId;

    // Taux de réduction dans l'intervalle [0.00, 1.00]
    @Column(name = "rate", precision = 3, scale = 2)
    private BigDecimal rate;

    /**
     * Invariant de défense en profondeur : lors de la persistance, exactement un des
     * identifiants de portée doit être renseigné et doit correspondre au scope déclaré ;
     * les deux autres doivent être nuls. La validation principale est assurée par la
     * couche service.
     */
    @Override
    protected void onCreate() {
        super.onCreate();
        validateScopeInvariant();
    }

    /**
     * Vérifie qu'exactement un identifiant de portée est renseigné et qu'il correspond
     * au {@link #scope} déclaré.
     *
     * @throws IllegalStateException si l'invariant de portée est violé
     */
    private void validateScopeInvariant() {
        boolean valid = scope != null && switch (scope) {
            case GROUP -> groupId != null && seriesId == null && sessionId == null;
            case SERIES -> seriesId != null && groupId == null && sessionId == null;
            case SESSION -> sessionId != null && groupId == null && seriesId == null;
        };

        if (!valid) {
            throw new IllegalStateException(
                    "Un discount doit avoir exactement une portée correspondant à son scope.");
        }
    }
}
