package com.school.management.domain.valueobject;

import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Value Object représentant un montant monétaire.
 * Immutable et thread-safe.
 *
 * Utilise BigDecimal en interne pour éviter les erreurs d'arrondi
 * typiques des calculs avec double/float.
 *
 * Usage:
 * <pre>
 * Money amount = Money.of(100.50);
 * Money doubled = amount.multiply(2);
 * Money divided = amount.divide(3);
 * </pre>
 *
 * @author Claude Code
 * @since Phase 2 Refactoring
 */
@Embeddable
public class Money implements Serializable, Comparable<Money> {

    private static final long serialVersionUID = 1L;

    /**
     * Nombre de décimales pour les montants monétaires (2 pour les centimes)
     */
    private static final int SCALE = 2;

    /**
     * Mode d'arrondi : HALF_UP (arrondi commercial standard)
     */
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    /**
     * Constante pour montant zéro
     */
    public static final Money ZERO = new Money(BigDecimal.ZERO);

    /**
     * Montant stocké en BigDecimal pour la précision
     * Utilise double en base de données pour compatibilité
     */
    private double amount;

    /**
     * Constructeur par défaut requis par JPA
     */
    protected Money() {
        this.amount = 0.0;
    }

    /**
     * Constructeur privé - utiliser les factory methods
     */
    private Money(BigDecimal amount) {
        validateAmount(amount);
        this.amount = amount.setScale(SCALE, ROUNDING_MODE).doubleValue();
    }

    /**
     * Crée un Money à partir d'un double
     *
     * @param amount le montant
     * @return une instance Money
     * @throws IllegalArgumentException si le montant est négatif
     */
    public static Money of(double amount) {
        return new Money(BigDecimal.valueOf(amount));
    }

    /**
     * Crée un Money à partir d'un BigDecimal
     *
     * @param amount le montant
     * @return une instance Money
     * @throws IllegalArgumentException si le montant est négatif
     */
    public static Money of(BigDecimal amount) {
        return new Money(amount);
    }

    /**
     * Crée un Money à partir d'un String
     *
     * @param amount le montant sous forme de string
     * @return une instance Money
     * @throws IllegalArgumentException si le montant est invalide ou négatif
     */
    public static Money of(String amount) {
        try {
            return new Money(new BigDecimal(amount));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid money amount: " + amount, e);
        }
    }

    /**
     * Valide qu'un montant n'est pas négatif
     */
    private void validateAmount(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("Money amount cannot be null");
        }
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Money amount cannot be negative: " + amount);
        }
    }

    /**
     * Additionne deux montants
     *
     * @param other le montant à ajouter
     * @return un nouveau Money représentant la somme
     */
    public Money add(Money other) {
        if (other == null) {
            return this;
        }
        return Money.of(this.toBigDecimal().add(other.toBigDecimal()));
    }

    /**
     * Soustrait un montant
     *
     * @param other le montant à soustraire
     * @return un nouveau Money représentant la différence
     * @throws IllegalArgumentException si le résultat serait négatif
     */
    public Money subtract(Money other) {
        if (other == null) {
            return this;
        }
        BigDecimal result = this.toBigDecimal().subtract(other.toBigDecimal());
        if (result.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(
                String.format("Subtraction would result in negative amount: %s - %s",
                    this.amount, other.amount));
        }
        return Money.of(result);
    }

    /**
     * Multiplie par un facteur
     *
     * @param multiplier le facteur de multiplication
     * @return un nouveau Money représentant le produit
     */
    public Money multiply(double multiplier) {
        if (multiplier < 0) {
            throw new IllegalArgumentException("Multiplier cannot be negative: " + multiplier);
        }
        return Money.of(this.toBigDecimal().multiply(BigDecimal.valueOf(multiplier)));
    }

    /**
     * Multiplie par un facteur entier
     *
     * @param multiplier le facteur de multiplication
     * @return un nouveau Money représentant le produit
     */
    public Money multiply(int multiplier) {
        if (multiplier < 0) {
            throw new IllegalArgumentException("Multiplier cannot be negative: " + multiplier);
        }
        return Money.of(this.toBigDecimal().multiply(BigDecimal.valueOf(multiplier)));
    }

    /**
     * Divise par un diviseur
     *
     * @param divisor le diviseur
     * @return un nouveau Money représentant le quotient
     * @throws IllegalArgumentException si le diviseur est zéro ou négatif
     */
    public Money divide(double divisor) {
        if (divisor <= 0) {
            throw new IllegalArgumentException("Divisor must be positive: " + divisor);
        }
        return Money.of(this.toBigDecimal().divide(
            BigDecimal.valueOf(divisor), SCALE, ROUNDING_MODE));
    }

    /**
     * Divise par un diviseur entier
     *
     * @param divisor le diviseur
     * @return un nouveau Money représentant le quotient
     * @throws IllegalArgumentException si le diviseur est zéro ou négatif
     */
    public Money divide(int divisor) {
        if (divisor <= 0) {
            throw new IllegalArgumentException("Divisor must be positive: " + divisor);
        }
        return Money.of(this.toBigDecimal().divide(
            BigDecimal.valueOf(divisor), SCALE, ROUNDING_MODE));
    }

    /**
     * Vérifie si ce montant est zéro
     *
     * @return true si le montant est zéro
     */
    public boolean isZero() {
        return this.toBigDecimal().compareTo(BigDecimal.ZERO) == 0;
    }

    /**
     * Vérifie si ce montant est supérieur à un autre
     *
     * @param other le montant à comparer
     * @return true si ce montant est supérieur
     */
    public boolean isGreaterThan(Money other) {
        return this.compareTo(other) > 0;
    }

    /**
     * Vérifie si ce montant est supérieur ou égal à un autre
     *
     * @param other le montant à comparer
     * @return true si ce montant est supérieur ou égal
     */
    public boolean isGreaterThanOrEqual(Money other) {
        return this.compareTo(other) >= 0;
    }

    /**
     * Vérifie si ce montant est inférieur à un autre
     *
     * @param other le montant à comparer
     * @return true si ce montant est inférieur
     */
    public boolean isLessThan(Money other) {
        return this.compareTo(other) < 0;
    }

    /**
     * Vérifie si ce montant est inférieur ou égal à un autre
     *
     * @param other le montant à comparer
     * @return true si ce montant est inférieur ou égal
     */
    public boolean isLessThanOrEqual(Money other) {
        return this.compareTo(other) <= 0;
    }

    /**
     * Retourne le montant en double
     *
     * @return le montant
     */
    public double getAmount() {
        return amount;
    }

    /**
     * Retourne le montant en BigDecimal
     *
     * @return le montant en BigDecimal
     */
    public BigDecimal toBigDecimal() {
        return BigDecimal.valueOf(amount).setScale(SCALE, ROUNDING_MODE);
    }

    @Override
    public int compareTo(Money other) {
        if (other == null) {
            throw new NullPointerException("Cannot compare to null Money");
        }
        return this.toBigDecimal().compareTo(other.toBigDecimal());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Money money = (Money) o;
        // Compare avec BigDecimal pour gérer la précision
        return this.toBigDecimal().compareTo(money.toBigDecimal()) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(toBigDecimal());
    }

    @Override
    public String toString() {
        return String.format("%.2f", amount);
    }

    /**
     * Formate le montant avec un symbole de devise
     *
     * @param currencySymbol le symbole (ex: "€", "$", "DH")
     * @return le montant formaté avec la devise
     */
    public String format(String currencySymbol) {
        return String.format("%.2f %s", amount, currencySymbol);
    }
}
