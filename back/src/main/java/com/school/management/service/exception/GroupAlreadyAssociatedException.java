package com.school.management.service.exception;

import lombok.Getter;

import java.util.List;

@Getter
public class GroupAlreadyAssociatedException extends RuntimeException {
    private List<String> groupNames;

    public GroupAlreadyAssociatedException(String message, List<String> groupNames) {
        super(message);
        this.groupNames = groupNames;
    }

}
