package com.school.management.service;

import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class PatchService {

    private final ModelMapper modelMapper;

    public PatchService(ModelMapper modelMapper) {
        this.modelMapper = modelMapper;
    }

    public <T> void applyPatch(T entity, Map<String, Object> updates) {
        modelMapper.map(updates, entity);
    }
}
