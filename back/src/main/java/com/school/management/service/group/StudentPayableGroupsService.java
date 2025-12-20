package com.school.management.service.group;

import com.school.management.persistance.AttendanceEntity;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.StudentGroupEntity;
import com.school.management.repository.AttendanceRepository;
import com.school.management.repository.GroupRepository;
import com.school.management.repository.StudentGroupRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service pour récupérer les groupes "payables" d'un étudiant.
 *
 * Logique :
 * 1. Groupes FIXES (dans student_groups) : TOUJOURS affichés
 * 2. Groupes RATTRAPAGE (attendances sans être dans student_groups) :
 * Affichés UNIQUEMENT si au moins 1 attendance.active = true
 */
@Service
public class StudentPayableGroupsService {

        private static final Logger LOGGER = LoggerFactory.getLogger(StudentPayableGroupsService.class);

        private final StudentGroupRepository studentGroupRepository;
        private final AttendanceRepository attendanceRepository;
        private final GroupRepository groupRepository;

        public StudentPayableGroupsService(
                        StudentGroupRepository studentGroupRepository,
                        AttendanceRepository attendanceRepository,
                        GroupRepository groupRepository) {
                this.studentGroupRepository = studentGroupRepository;
                this.attendanceRepository = attendanceRepository;
                this.groupRepository = groupRepository;
        }

        /**
         * Récupère tous les groupes pour lesquels l'étudiant peut effectuer un
         * paiement.
         *
         * @param studentId l'ID de l'étudiant
         * @return la liste des groupes payables
         */
        public List<GroupEntity> getPayableGroupsForStudent(Long studentId) {
                LOGGER.info("Getting payable groups for student: {}", studentId);

                Set<Long> payableGroupIds = new HashSet<>();

                // 1. GROUPES FIXES : Récupérer tous les groupes ACTIFS de student_groups
                List<StudentGroupEntity> studentGroups = studentGroupRepository.findByStudentIdAndActiveTrue(studentId);
                Set<Long> fixedGroupIds = studentGroups.stream()
                                .map(sg -> sg.getGroup().getId())
                                .collect(Collectors.toSet());

                payableGroupIds.addAll(fixedGroupIds);
                LOGGER.debug("Student {} has {} fixed groups", studentId, fixedGroupIds.size());

                // 2. GROUPES RATTRAPAGE : Groupes avec attendances ACTIVES HORS student_groups
                List<AttendanceEntity> activeAttendances = attendanceRepository
                                .findByStudentIdAndActiveTrue(studentId);

                Set<Long> catchUpGroupIds = activeAttendances.stream()
                                .map(a -> a.getSession().getGroup().getId())
                                .filter(groupId -> !fixedGroupIds.contains(groupId)) // Exclure les groupes fixes
                                .collect(Collectors.toSet());

                payableGroupIds.addAll(catchUpGroupIds);
                LOGGER.debug("Student {} has {} catch-up groups with active attendances",
                                studentId, catchUpGroupIds.size());

                // 3. Récupérer les entités GroupEntity
                List<GroupEntity> payableGroups = new ArrayList<>();
                for (Long groupId : payableGroupIds) {
                        groupRepository.findById(Objects.requireNonNull(groupId)).ifPresent(payableGroups::add);
                }

                LOGGER.info("Student {} has {} total payable groups ({} fixed + {} catch-up)",
                                studentId, payableGroups.size(), fixedGroupIds.size(), catchUpGroupIds.size());

                return payableGroups;
        }
}
