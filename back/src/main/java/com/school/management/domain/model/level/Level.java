package com.school.management.domain.model.level;


public enum Level {
    FIRST("1st"),
    SECOND("2nd"),
    THIRD("3rd");
    // ...

    private final String label;

    Level(String label) {
        this.label = label;
    }

    public String getLabel() {
        return this.label;
    }
}