package com.school.management.service.util;

import jakarta.persistence.criteria.Path;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

public class CommonSpecifications {

    private CommonSpecifications() {
    }

    public static <T> Specification<T> equalsIfNotNull(String attributeName, Object value) {
        return (root, query, cb) -> {
            if (value == null) return null;
            return cb.equal(root.get(attributeName), value);
        };
    }

    public static <T> Specification<T> greaterThanOrEqualToIfNotNull(String attributeName, Comparable<?> value) {
        return (root, query, cb) -> {
            if (value == null) return null;
            switch (value) {
                case Integer integer -> {
                    Path<Integer> integerPath = root.get(attributeName);
                    return cb.greaterThanOrEqualTo(integerPath, integer);
                }
                case LocalDate localDate -> {
                    Path<LocalDate> datePath = root.get(attributeName);
                    return cb.greaterThanOrEqualTo(datePath, localDate);
                }
                case String string -> {
                    Path<String> stringPath = root.get(attributeName);
                    return cb.greaterThanOrEqualTo(stringPath, string);
                }
                default -> throw new IllegalStateException("Unexpected value: " + value);
            }
        };
    }

    public static <T> Specification<T> lessThanOrEqualToIfNotNull(String attributeName, Comparable<?> value) {
        return (root, query, cb) -> {
            if (value == null) return null;
            if (value instanceof Integer integer) {
                Path<Integer> integerPath = root.get(attributeName);
                return cb.lessThanOrEqualTo(integerPath, integer);
            }
            else if (value instanceof LocalDate localDate) {
                Path<LocalDate> datePath = root.get(attributeName);
                return cb.lessThanOrEqualTo(datePath, localDate);
            }
            else if (value instanceof String string) {
                Path<String> stringPath = root.get(attributeName);
                return cb.lessThanOrEqualTo(stringPath, string);
            }
            return null;
        };
    }

    public static <T> Specification<T> likeIfNotNull(String attributeName, String value) {
            return (root, query, cb) -> {
                if (value == null || value.isEmpty()) return null;
                return cb.like(cb.lower(root.get(attributeName)), "%" + value.toLowerCase() + "%");
            };
    }


}
