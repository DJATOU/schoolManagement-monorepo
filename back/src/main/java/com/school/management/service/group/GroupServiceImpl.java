package com.school.management.service.group;

import com.school.management.dto.GroupDTO;
import com.school.management.dto.SessionSeriesDto;
import com.school.management.dto.StudentDTO;
import com.school.management.mapper.GroupMapper;
import com.school.management.mapper.StudentMapper;
import com.school.management.persistance.AttendanceEntity;
import com.school.management.persistance.GroupEntity;
import com.school.management.repository.*;
import com.school.management.infrastructure.storage.FileManagementService;
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

    // PHASE 1 REFACTORING: Repositories pour MappingContext
    private final GroupTypeRepository groupTypeRepository;
    private final LevelRepository levelRepository;
    private final SubjectRepository subjectRepository;
    private final PricingRepository pricingRepository;
    private final TeacherRepository teacherRepository;

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
            GroupTypeRepository groupTypeRepository,
            LevelRepository levelRepository,
            SubjectRepository subjectRepository,
            PricingRepository pricingRepository,
            TeacherRepository teacherRepository) {
        this.groupRepository = groupRepository;
        this.groupMapper = groupMapper;
        this.studentMapper = studentMapper;
        this.modelMapper = modelMapper;
        this.groupSearchService = groupSearchService;
        this.studentGroupRepository = studentGroupRepository;
        this.attendanceRepository = attendanceRepository;
        this.fileManagementService = fileManagementService;
        this.groupTypeRepository = groupTypeRepository;
        this.levelRepository = levelRepository;
        this.subjectRepository = subjectRepository;
        this.pricingRepository = pricingRepository;
        this.teacherRepository = teacherRepository;
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
                teacherRepository);
        LOGGER.debug("MappingContext initialized for GroupService");
    }

    /**
     * Retourne le MappingContext pour utilisation par les controllers
     */
    public MappingContext getMappingContext() {
        return mappingContext;
    }

    public List<GroupEntity> findByTeacherId(Long teacherId) {
        return groupRepository.findAll().stream()
                .filter(group -> group.getTeacher() != null && group.getTeacher().getId().equals(teacherId))
                .toList();
    }

    public List<GroupEntity> findByStudentId(Long studentId) {
        return groupRepository.findAll().stream()
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

    @Transactional(readOnly = true)
    public List<GroupEntity> findAll() {
        LOGGER.info("Fetching all groups...");
        return groupRepository.findAll();
    }

    @Transactional
    public GroupEntity save(GroupEntity group) {
        return groupRepository.save(Objects.requireNonNull(group));
    }

    @Transactional
    public void delete(Long id) {
        groupRepository.deleteById(Objects.requireNonNull(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<GroupDTO> searchGroupsByNameStartingWithDTO(String name) {
        List<GroupEntity> groupEntities = groupSearchService.searchGroupsByNameStartingWith(name);
        return groupEntities.stream()
                .map(groupMapper::groupToGroupDTO)
                .toList();
    }

    @Override
    public void desactivateGroup(Long id) {
        groupRepository.findById(Objects.requireNonNull(id)).ifPresent(group -> {
            group.setActive(false);
            groupRepository.save(group);
        });
    }

    @Transactional(readOnly = true)
    public List<SessionSeriesDto> getSeriesByGroupId(Long groupId) {
        GroupEntity group = groupRepository.findById(Objects.requireNonNull(groupId))
                .orElseThrow(() -> new CustomServiceException(GROUP_NOT_FOUND + groupId));

        return group.getSeries().stream().map(element -> modelMapper.map(element, SessionSeriesDto.class))
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