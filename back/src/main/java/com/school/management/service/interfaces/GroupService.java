package com.school.management.service.interfaces;

import com.school.management.dto.GroupDTO;
import com.school.management.persistance.GroupEntity;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface GroupService {
    List<GroupDTO> searchGroupsByNameStartingWithDTO(String name);

    void desactivateGroup(Long id);

    /**
     * Liste les groupes d'une année scolaire, en revenant à l'année courante par défaut.
     *
     * <p>Lorsqu'un identifiant d'année scolaire est fourni, seuls les groupes de cette année
     * sont retournés (Exigences 10.4, 10.5). Sinon, la liste est filtrée sur l'année scolaire
     * courante (Exigence 10.4).</p>
     *
     * @param schoolYearId l'identifiant de l'année scolaire à filtrer, ou {@code null} pour
     *                     l'année courante
     * @return les groupes rattachés à l'année demandée (ou courante)
     */
    List<GroupEntity> findGroupsBySchoolYear(Long schoolYearId);

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
