package com.school.management.service.student;

import com.school.management.dto.GroupDTO;
import com.school.management.dto.LevelDto;
import com.school.management.dto.ParcoursDTO;
import com.school.management.dto.ParcoursYearDTO;
import com.school.management.mapper.GroupMapper;
import com.school.management.mapper.LeveLMapper;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.LevelEntity;
import com.school.management.persistance.SchoolYearEntity;
import com.school.management.persistance.StudentGroupEntity;
import com.school.management.repository.StudentGroupRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Service dérivant le parcours d'un étudiant à travers les années scolaires.
 * <p>
 * Le parcours est reconstruit à partir des inscriptions actives de l'étudiant :
 * pour chaque année scolaire dans laquelle il a suivi au moins un groupe, on
 * expose les niveaux distincts et les groupes fréquentés. Les années sans
 * inscription sont omises (Exigences 11.4, 14.2) et les entrées sont triées par
 * date de début d'année scolaire décroissante (Exigence 11.3).
 */
@Service
public class StudentParcoursService {

    private final StudentGroupRepository studentGroupRepository;
    private final GroupMapper groupMapper;
    private final LeveLMapper levelMapper;

    public StudentParcoursService(StudentGroupRepository studentGroupRepository,
                                  GroupMapper groupMapper,
                                  LeveLMapper levelMapper) {
        this.studentGroupRepository = studentGroupRepository;
        this.groupMapper = groupMapper;
        this.levelMapper = levelMapper;
    }

    /**
     * Construit le parcours d'un étudiant.
     *
     * @param studentId identifiant de l'étudiant
     * @return le parcours, avec une entrée par année scolaire fréquentée,
     * triée par date de début décroissante
     */
    @Transactional(readOnly = true)
    public ParcoursDTO getParcours(Long studentId) {
        Objects.requireNonNull(studentId, "L'identifiant de l'étudiant est obligatoire.");

        // 1) Charger les inscriptions actives de l'étudiant.
        List<StudentGroupEntity> enrollments =
                studentGroupRepository.findByStudentIdAndActiveTrue(studentId);

        // 2) Regrouper les groupes inscrits par année scolaire (on ignore les
        // inscriptions dont le groupe ou l'année scolaire est absent, car elles ne
        // peuvent contribuer à aucune année du parcours — Exigences 4.5, 11.4, 14.2).
        Map<Long, YearAccumulator> byYear = new LinkedHashMap<>();
        for (StudentGroupEntity enrollment : enrollments) {
            if (enrollment == null) {
                continue;
            }
            GroupEntity group = enrollment.getGroup();
            if (group == null) {
                continue;
            }
            SchoolYearEntity schoolYear = group.getSchoolYear();
            if (schoolYear == null || schoolYear.getId() == null) {
                continue;
            }
            byYear.computeIfAbsent(schoolYear.getId(), id -> new YearAccumulator(schoolYear))
                    .addGroup(group);
        }

        // 3) Construire une entrée par année scolaire fréquentée.
        List<ParcoursYearDTO> years = new ArrayList<>();
        for (YearAccumulator acc : byYear.values()) {
            years.add(acc.toDto());
        }

        // 4) Trier par date de début d'année scolaire décroissante (Exigence 11.3).
        // Les dates nulles sont placées en dernier pour rester déterministe.
        years.sort(Comparator.comparing(
                y -> byYear.get(y.getSchoolYearId()).schoolYear.getStartDate(),
                Comparator.nullsLast(Comparator.reverseOrder())));

        return ParcoursDTO.builder()
                .studentId(studentId)
                .years(years)
                .build();
    }

    /**
     * Accumulateur interne regroupant, pour une année scolaire, les groupes
     * fréquentés (dédupliqués par identifiant) tout en préservant l'ordre de
     * rencontre.
     */
    private final class YearAccumulator {
        private final SchoolYearEntity schoolYear;
        // Groupes dédupliqués par identifiant, ordre d'insertion préservé.
        private final Map<Long, GroupEntity> groups = new LinkedHashMap<>();

        private YearAccumulator(SchoolYearEntity schoolYear) {
            this.schoolYear = schoolYear;
        }

        private void addGroup(GroupEntity group) {
            if (group != null && group.getId() != null) {
                groups.putIfAbsent(group.getId(), group);
            }
        }

        private ParcoursYearDTO toDto() {
            List<GroupEntity> groupEntities = new ArrayList<>(groups.values());

            // Niveaux distincts (par identifiant) des groupes fréquentés
            // (Exigence 4.3 : un seul niveau ; Exigence 4.4 : plusieurs niveaux).
            Map<Long, LevelEntity> distinctLevels = new LinkedHashMap<>();
            for (GroupEntity group : groupEntities) {
                LevelEntity level = group.getLevel();
                if (level != null && level.getId() != null) {
                    distinctLevels.putIfAbsent(level.getId(), level);
                }
            }

            List<LevelDto> levelDtos = new ArrayList<>();
            for (LevelEntity level : distinctLevels.values()) {
                levelDtos.add(levelMapper.toDto(level));
            }

            List<GroupDTO> groupDtos = new ArrayList<>();
            for (GroupEntity group : groupEntities) {
                groupDtos.add(groupMapper.groupToGroupDTO(group));
            }

            return ParcoursYearDTO.builder()
                    .schoolYearId(schoolYear.getId())
                    .schoolYearLabel(schoolYear.getLabel())
                    .levels(levelDtos)
                    .groups(groupDtos)
                    .build();
        }
    }
}
