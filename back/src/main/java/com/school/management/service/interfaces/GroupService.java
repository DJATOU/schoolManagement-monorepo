package com.school.management.service.interfaces;

import com.school.management.dto.GroupDTO;
import com.school.management.persistance.GroupEntity;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface GroupService {
    List<GroupDTO> searchGroupsByNameStartingWithDTO(String name);
    void desactivateGroup(Long id);

    // updateGroupPartially(Long id, Map<String, Object> updates);

    /**
     * PHASE 3A: Upload photo pour un groupe
     */
    String uploadPhoto(Long groupId, MultipartFile file) throws IOException;

    /**
     * PHASE 3A: Récupère la photo d'un groupe
     */
    Resource getPhoto(Long groupId) throws IOException;
}
