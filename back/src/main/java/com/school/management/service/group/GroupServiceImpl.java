package com.school.management.service.group;

import com.school.management.dto.GroupDTO;
import com.school.management.dto.SessionSeriesDto;
import com.school.management.dto.StudentDTO;
import com.school.management.mapper.GroupMapper;
import com.school.management.mapper.StudentMapper;
import com.school.management.persistance.AttendanceEntity;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.repository.*;
import com.school.management.infrastructure.storage.FileManagementService;
import com.school.management.service.CurrentSchoolYearService;
import com.school.management.service.ReadOnlyYearGuard;
import com.school.management.service.exception.CustomServiceException;
import com.school.management.service.interfaces.GroupService;
import com.school.management.shared.mapper.MappingContext;
import org.modelmapper.ModelMapper;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;

import java.io.IOException;
import java.util.*;

@Service
public class GroupServiceImpl implements GroupService {
    private static final Logger LOGGER = LoggerFactory.getLogger(GroupServiceImpl.class);
    private static final String GROUP_NOT_FOUND = "Group not found with id: ";
    private final GroupRepository groupRepository;
    private final GroupMapper groupMapper;
    private final StudentMapper studentMapper;
    private final ModelMapper modelMapper;
    private final GroupSearchService groupSearchService;
    private final AttendanceRepository attendanceRepository;
    private final StudentGroupRepository studentGroupRepository;
    private final FileManagementService fileManagementService;

    // SCHOOL YEAR: assignation de l'année scolaire et garde lecture seule
    private final CurrentSchoolYearService currentSchoolYearService;
    private final ReadOnlyYearGuard readOnlyYearGuard;

    // PHASE 1 REFACTORING: Repositories pour MappingContext
    private final GroupTypeRepository groupTypeRepository;
    private final LevelRepository levelRepository;
    private final SubjectRepository subjectRepository;
    private final PricingRepository pricingRepository;
    private final TeacherRepository teacherRepository;
    private final SchoolYearRepository schoolYearRepository;

    // MappingContext pour GroupMapper
    private MappingContext mappingContext;

    @Autowired
    public GroupServiceImpl(GroupRepository groupRepository,
            GroupMapper groupMapper,
            StudentMapper studentMapper,
            ModelMapper modelMapper,
            GroupSearchService groupSearchService,
            StudentGroupRepository studentGroupRepository,
            AttendanceRepository attendanceRepository,
            FileManagementService fileManagementService,
            CurrentSchoolYearService currentSchoolYearService,
            ReadOnlyYearGuard readOnlyYearGuard,
            GroupTypeRepository groupTypeRepository,
            LevelRepository levelRepository,
            SubjectRepository subjectRepository,
            PricingRepository pricingRepository,
            TeacherRepository teacherRepository,
            SchoolYearRepository schoolYearRepository) {
        this.groupRepository = groupRepository;
        this.groupMapper = groupMapper;
        this.studentMapper = studentMapper;
        this.modelMapper = modelMapper;
        this.groupSearchService = groupSearchService;
        this.studentGroupRepository = studentGroupRepository;
        this.attendanceRepository = attendanceRepository;
        this.fileManagementService = fileManagementService;
        this.currentSchoolYearService = currentSchoolYearService;
        this.readOnlyYearGuard = readOnlyYearGuard;
        this.groupTypeRepository = groupTypeRepository;
        this.levelRepository = levelRepository;
        this.subjectRepository = subjectRepository;
        this.pricingRepository = pricingRepository;
        this.teacherRepository = teacherRepository;
        this.schoolYearRepository = schoolYearRepository;
    }

    /**
     * PHASE 1 REFACTORING: Initialise le MappingContext après injection des
     * dépendances
     */
    @PostConstruct
    private void initMappingContext() {
        this.mappingContext = MappingContext.forGroup(
                groupTypeRepository,
                levelRepository,
                subjectRepository,
                pricingRepository,
                teacherRepository,
                schoolYearRepository);
        LOGGER.debug("MappingContext initialized for GroupService");
    }

    /**
     * Retourne le MappingContext pour utilisation par les controllers
     */
    public MappingContext getMappingContext() {
        return mappingContext;
    }

    public List<GroupEntity> findByTeacherId(Long teacherId) {
        return groupRepository.findAllActive().stream()
                .filter(group -> group.getTeacher() != null && group.getTeacher().getId().equals(teacherId))
                .toList();
    }

    public List<GroupEntity> findByStudentId(Long studentId) {
        return groupRepository.findAllActive().stream()
                .filter(group -> group.getStudents().stream().anyMatch(student -> student.getId().equals(studentId)))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<GroupEntity> findById(Long id) {
        try {
            return groupRepository.findById(Objects.requireNonNull(id));
        } catch (DataAccessException e) {
            String errorMessage = "Error fetching group with ID " + id;
            throw new CustomServiceException(errorMessage, e);
        }
    }

    /**
     * Liste les groupes, hors groupes désactivés.
     *
     * <p>La suppression d'un groupe est logique ({@code active = false}) : sans ce filtre, un
     * groupe désactivé continuerait d'apparaître dans les listes.</p>
     */
    @Transactional(readOnly = true)
    public List<GroupEntity> findAll() {
        LOGGER.info("Fetching all groups...");
        return groupRepository.findAllActive();
    }

    /**
     * Liste les groupes d'une année scolaire, en revenant à l'année courante par défaut.
     *
     * <p>Lorsqu'un identifiant d'année scolaire est explicitement fourni, seuls les groupes
     * rattachés à cette année sont retournés via {@link GroupRepository#findBySchoolYearId(Long)}
     * (Exigences 10.4, 10.5). Lorsqu'aucun identifiant n'est fourni, la liste est filtrée sur
     * l'année scolaire courante (Exigence 10.4).</p>
     *
     * <p>Cas limite : s'il n'existe aucune année courante (par exemple avant la migration ou la
     * désignation d'une année), on retombe sur l'ensemble des groupes. La consultation étant une
     * lecture, elle reste permise quelle que soit l'année (Exigence 9.3) plutôt que de rejeter la
     * requête.</p>
     *
     * @param schoolYearId l'identifiant de l'année scolaire à filtrer, ou {@code null} pour
     *                     l'année courante
     * @return les groupes rattachés à l'année demandée (ou courante)
     */
    @Override
    @Transactional(readOnly = true)
    public List<GroupEntity> findGroupsBySchoolYear(Long schoolYearId) {
        if (schoolYearId != null) {
            // Filtrage explicite par l'année scolaire demandée (Exigences 10.4, 10.5).
            return groupRepository.findActiveBySchoolYearId(schoolYearId);
        }
        // Aucune année fournie : filtrage par défaut sur l'année courante (Exigence 10.4).
        return currentSchoolYearService.findCurrent()
                .map(current -> groupRepository.findActiveBySchoolYearId(current.getId()))
                // Aucune année courante définie : la lecture reste permise (Exigence 9.3).
                .orElseGet(groupRepository::findAllActive);
    }

    @Transactional
    public GroupEntity save(GroupEntity group) {
        return groupRepository.save(Objects.requireNonNull(group));
    }

    /**
     * Crée un groupe en lui rattachant son année scolaire.
     *
     * <p>Si aucune année scolaire n'est explicitement fournie sur l'entité, le groupe est
     * rattaché à l'année scolaire courante par défaut (Exigence 3.2). L'appel à
     * {@link CurrentSchoolYearService#requireCurrent()} bloque la création tant qu'aucune
     * année courante n'est désignée (Exigence 13.3). Lorsqu'une année scolaire est fournie,
     * elle est conservée telle quelle (Exigence 3.3).</p>
     *
     * @param group le groupe à créer (non nul)
     * @return le groupe créé et persisté
     */
    @Transactional
    public GroupEntity createGroup(GroupEntity group) {
        Objects.requireNonNull(group);
        if (group.getSchoolYear() == null) {
            // Aucune année fournie : rattachement à l'année courante (Exigences 3.2, 13.3).
            group.setSchoolYear(currentSchoolYearService.requireCurrent());
        }
        // Sinon, l'année explicitement fournie est conservée (Exigence 3.3).
        return groupRepository.save(group);
    }

    /**
     * Met à jour un groupe existant après avoir vérifié qu'il appartient à l'année courante.
     *
     * <p>Le garde lecture seule rejette la modification d'un groupe rattaché à une année
     * scolaire passée (Exigence 9.2). L'année scolaire existante est préservée si la mise à
     * jour n'en fournit pas.</p>
     *
     * @param id           l'identifiant du groupe à mettre à jour
     * @param updatedGroup l'entité contenant les nouvelles valeurs
     * @return le groupe mis à jour et persisté
     */
    @Transactional
    public GroupEntity updateGroup(Long id, GroupEntity updatedGroup) {
        Objects.requireNonNull(id);
        Objects.requireNonNull(updatedGroup);
        GroupEntity existing = groupRepository.findById(id)
                .orElseThrow(() -> new CustomServiceException(GROUP_NOT_FOUND + id));
        // Refuse la modification d'un groupe d'une année passée (Exigence 9.2).
        readOnlyYearGuard.assertGroupMutable(existing);
        updatedGroup.setId(id);
        // Préserve l'année scolaire existante si la mise à jour n'en précise pas.
        if (updatedGroup.getSchoolYear() == null) {
            updatedGroup.setSchoolYear(existing.getSchoolYear());
        }
        return groupRepository.save(updatedGroup);
    }

    @Transactional
    public void delete(Long id) {
        Objects.requireNonNull(id);
        // Refuse la suppression d'un groupe d'une année passée (Exigence 9.2).
        groupRepository.findById(id).ifPresent(readOnlyYearGuard::assertGroupMutable);
        groupRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GroupDTO> searchGroupsByNameStartingWithDTO(String name) {
        List<GroupEntity> groupEntities = groupSearchService.searchGroupsByNameStartingWith(name);
        return groupEntities.stream()
                .map(groupMapper::groupToGroupDTO)
                .toList();
    }

    /**
     * Désactive un groupe (suppression logique) : il disparaît des listes et des statistiques
     * mais son historique (séances, présences, paiements) est conservé.
     *
     * <p>Comme {@link #delete(Long)}, l'opération est refusée sur un groupe d'une année passée :
     * l'historique est en lecture seule (Exigence 9.2).</p>
     */
    @Override
    @Transactional
    public void desactivateGroup(Long id) {
        groupRepository.findById(Objects.requireNonNull(id)).ifPresent(group -> {
            readOnlyYearGuard.assertGroupMutable(group);
            group.setActive(false);
            groupRepository.save(group);
        });
    }

    @Transactional(readOnly = true)
    public List<SessionSeriesDto> getSeriesByGroupId(Long groupId) {
        GroupEntity group = groupRepository.findById(Objects.requireNonNull(groupId))
                .orElseThrow(() -> new CustomServiceException(GROUP_NOT_FOUND + groupId));

        // group.getSeries() est un Set : son itération ne garantit aucun ordre, et la fiche
        // groupe affichait donc les séries mélangées. On les trie par identifiant croissant,
        // c'est-à-dire par ordre d'ajout, comme le fait la lecture par dépôt.
        return group.getSeries().stream()
                .sorted(java.util.Comparator.comparing(SessionSeriesEntity::getId,
                        java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                .map(element -> modelMapper.map(element, SessionSeriesDto.class))
                .toList();
    }

    @Transactional(readOnly = true)
    public Long countStudentsInGroup(Long groupId) {
        GroupEntity group = groupRepository.findById(Objects.requireNonNull(groupId))
                .orElseThrow(() -> new CustomServiceException(GROUP_NOT_FOUND + groupId));
        return (long) group.getStudents().size();
    }

    public GroupEntity getGroupWithDetails(Long groupId) {
        return groupRepository.findGroupWithDetailsById(groupId)
                .orElseThrow(() -> new RuntimeException(GROUP_NOT_FOUND + groupId));
    }

    @Transactional(readOnly = true)
    public List<StudentDTO> getActiveStudentsByGroupId(Long groupId) {
        groupRepository.findById(Objects.requireNonNull(groupId))
                .orElseThrow(() -> new CustomServiceException(GROUP_NOT_FOUND + groupId));
        return studentGroupRepository.findByGroupIdAndActiveTrue(groupId).stream()
                .map(studentGroup -> studentMapper.studentToStudentDTO(studentGroup.getStudent()))
                .toList();
    }

    public List<GroupDTO> getGroupsForPaymentDto(Long studentId) {
        // 1) Récupérer les GroupEntity (fixe + rattrapage)
        List<GroupEntity> groups = this.getGroupsForPayment(studentId); // ta méthode existante

        // 2) Construire la liste de DTO
        return groups.stream()
                .map(g -> {
                    GroupDTO dto = groupMapper.groupToGroupDTO(g);

                    // 3) Vérifier si isCatchUp
                    boolean isCatchUp = attendanceRepository
                            .existsByGroupIdAndStudentIdAndIsCatchUp(g.getId(), studentId, true);
                    dto.setCatchUp(isCatchUp);

                    return dto;
                })
                .toList();
    }

    public List<GroupEntity> getGroupsForPayment(Long studentId) {
        // (Ton code actuel)
        List<GroupEntity> fixedGroups = groupRepository.findByStudents_Id(studentId);
        List<GroupEntity> catchUpGroups = attendanceRepository
                .findByStudentIdAndIsCatchUp(studentId, true)
                .stream()
                .map(AttendanceEntity::getGroup)
                .distinct()
                .toList();

        Set<GroupEntity> unionSet = new HashSet<>(fixedGroups);
        unionSet.addAll(catchUpGroups);

        return new ArrayList<>(unionSet);
    }

    /**
     * PHASE 3A: Upload photo pour un groupe
     * 
     * @param groupId ID du groupe
     * @param file    Fichier photo à uploader
     * @return Le nom du fichier uploadé
     * @throws IOException Si erreur d'upload
     */
    @Transactional
    public String uploadPhoto(Long groupId, MultipartFile file) throws IOException {
        LOGGER.info("Uploading photo for group ID: {}", groupId);

        GroupEntity group = groupRepository.findById(Objects.requireNonNull(groupId))
                .orElseThrow(() -> new CustomServiceException(GROUP_NOT_FOUND + groupId));

        // Supprimer l'ancienne photo si elle existe
        if (group.getPhoto() != null && !group.getPhoto().isEmpty()) {
            try {
                fileManagementService.deleteFile(group.getPhoto());
                LOGGER.debug("Deleted old photo: {}", group.getPhoto());
            } catch (IOException e) {
                LOGGER.warn("Failed to delete old photo: {}", group.getPhoto(), e);
                // Continue malgré l'erreur - on veut quand même uploader la nouvelle photo
            }
        }

        // Upload la nouvelle photo avec rollback automatique
        FileManagementService.FileUploadResult result = fileManagementService.uploadWithRollback(file);

        if (!result.isSuccess()) {
            throw new IOException("Photo upload failed: " + result.getErrorMessage());
        }

        // Mettre à jour l'entité avec le nom du fichier
        group.setPhoto(result.getFilename());
        groupRepository.save(group);

        LOGGER.info("Photo uploaded successfully for group ID {}: {}", groupId, result.getFilename());
        return result.getFilename();
    }

    /**
     * PHASE 3A: Récupère la photo d'un groupe
     * 
     * @param groupId ID du groupe
     * @return Resource contenant la photo
     * @throws IOException Si erreur de lecture
     */
    @Transactional(readOnly = true)
    public Resource getPhoto(Long groupId) throws IOException {
        LOGGER.debug("Fetching photo for group ID: {}", groupId);

        GroupEntity group = groupRepository.findById(Objects.requireNonNull(groupId))
                .orElseThrow(() -> new CustomServiceException(GROUP_NOT_FOUND + groupId));

        if (group.getPhoto() == null || group.getPhoto().isEmpty()) {
            throw new CustomServiceException("Group " + groupId + " has no photo");
        }

        return fileManagementService.getFile(group.getPhoto());
    }

}