package com.school.management.service.student;

import com.school.management.dto.group.GroupHistoryDTO;
import com.school.management.dto.serie.SeriesHistoryDTO;
import com.school.management.dto.session.SessionHistoryDTO;
import com.school.management.dto.student.StudentFullHistoryDTO;
import com.school.management.persistance.*;
import com.school.management.repository.AttendanceRepository;
import com.school.management.repository.StudentGroupRepository;
import com.school.management.repository.StudentRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class StudentHistoryService {

    private final StudentRepository studentRepository;
    private final AttendanceRepository attendanceRepository;
    private final StudentGroupRepository studentGroupRepository;

    public StudentHistoryService(StudentRepository studentRepository,
                                AttendanceRepository attendanceRepository,
                                StudentGroupRepository studentGroupRepository) {
        this.studentRepository = studentRepository;
        this.attendanceRepository = attendanceRepository;
        this.studentGroupRepository = studentGroupRepository;
    }

    public StudentFullHistoryDTO getStudentFullHistory(Long studentId) {
        // Récupérer l'étudiant
        StudentEntity student = studentRepository.findById(studentId)
                .orElseThrow(() -> new EntityNotFoundException("Étudiant non trouvé"));

        return mapStudentEntityToDTO(student);
    }

    // ===================== mapStudentEntityToDTO ======================
    private StudentFullHistoryDTO mapStudentEntityToDTO(StudentEntity student) {
        StudentFullHistoryDTO dto = new StudentFullHistoryDTO();
        dto.setStudentId(student.getId());
        dto.setStudentName(student.getFirstName() + " " + student.getLastName());

        // 1) Groupes fixes : ceux où l'étudiant est officiellement inscrit
        List<GroupEntity> fixedGroups = new ArrayList<>(student.getGroups());

        // 2) Groupes "rattrapage" (où attendance.isCatchUp = true pour l'étudiant)
        List<GroupEntity> catchUpGroups = attendanceRepository
                .findByStudentIdAndIsCatchUp(student.getId(), true)
                .stream()
                .map(AttendanceEntity::getGroup)
                .distinct()
                .toList();

        // 3) Union des deux
        Set<GroupEntity> unionSet = new HashSet<>(fixedGroups);
        unionSet.addAll(catchUpGroups);

        // 4) Construire la liste de GroupHistoryDTO
        List<GroupHistoryDTO> groupDTOs = unionSet.stream()
                .map(group -> mapGroupEntityToDTO(group, student))
                .toList();

        dto.setGroups(groupDTOs);
        return dto;
    }

    // ===================== mapGroupEntityToDTO ======================
    private GroupHistoryDTO mapGroupEntityToDTO(GroupEntity group, StudentEntity student) {
        GroupHistoryDTO dto = new GroupHistoryDTO();
        dto.setGroupId(group.getId());
        dto.setGroupName(group.getName());

        // Savoir si l'étudiant est inscrit officiellement à ce groupe
        boolean isOfficial = student.getGroups().contains(group);

        // Convertir les séries en SeriesHistoryDTO, en filtrant si besoin
        List<SeriesHistoryDTO> seriesDTOs = group.getSeries().stream()
                .map(series -> mapSeriesEntityToDTO(series, student, group, isOfficial))
                .filter(Objects::nonNull)  // on enlève les séries qui n'ont aucune session pertinente
                .toList();

        dto.setSeries(seriesDTOs);
        return dto;
    }

    // ===================== mapSeriesEntityToDTO ======================
    private SeriesHistoryDTO mapSeriesEntityToDTO(SessionSeriesEntity series,
                                                  StudentEntity student,
                                                  GroupEntity group,
                                                  boolean isOfficial) {
        SeriesHistoryDTO dto = new SeriesHistoryDTO();
        dto.setSeriesId(series.getId());
        dto.setSeriesName(series.getName());

        // NOUVEAU: Récupérer la date d'inscription de l'étudiant au groupe
        Date enrollmentDate = isOfficial ? getStudentEnrollmentDate(student, group) : null;

        // Récupérer toutes les sessions de la série
        List<SessionEntity> allSessions = series.getSessions().stream()
                .sorted(Comparator.comparing(SessionEntity::getSessionTimeStart))
                .toList();

        // NOUVEAU: Filtrer d'abord les sessions selon la date d'inscription
        List<SessionEntity> eligibleSessions = filterSessionsAfterEnrollment(allSessions, enrollmentDate);

        // 1) Filtre sessions où l'étudiant a AU MOINS un attendance OU un paiement
        List<SessionEntity> relevantSessions = eligibleSessions.stream()
                .filter(session -> {
                    boolean hasAttendance = session.getAttendances().stream()
                            .anyMatch(a -> a.getStudent().getId().equals(student.getId()) && a.isActive());
                    boolean hasPayment = session.getPaymentDetails().stream()
                            .anyMatch(pd -> pd.getPayment().getStudent().getId().equals(student.getId()));
                    return hasAttendance || hasPayment;
                })
                .toList();

        // 2) Si le groupe est "officiel", on garde le relevantSessions
        //    Sinon, on re-filtre pour ne conserver que ceux où l'étudiant a réellement un attendance (rattrapage)
        List<SessionEntity> filteredSessions;
        if (isOfficial) {
            filteredSessions = relevantSessions;
        } else {
            filteredSessions = relevantSessions.stream()
                    .filter(session -> session.getAttendances().stream()
                            .anyMatch(a -> a.getStudent().getId().equals(student.getId()) && a.isActive()))
                    .toList();
        }

        // 3) Si le résultat est VIDE => on ne retourne pas cette série (on renvoie null)
        if (filteredSessions.isEmpty()) {
            return null;  // => la série n'apparaîtra pas dans le PDF
        }

        // NOUVEAU: Calculer le coût total uniquement pour les sessions éligibles (après inscription)
        double totalCostForStudent = calculateTotalCostForStudent(group, eligibleSessions);
        double totalPaidForSeries = calculateTotalPaidForSeries(series, student);

        // NOUVEAU: Statut de paiement basé sur les sessions auxquelles l'étudiant a droit
        // Si l'étudiant a payé >= au coût des sessions éligibles, le paiement est complet
        dto.setPaymentStatus(totalPaidForSeries >= totalCostForStudent ? "Complet" : "Partiel");
        dto.setTotalAmountPaid(totalPaidForSeries);
        dto.setTotalCost(totalCostForStudent);

        // 4) Construire la liste finale de SessionHistoryDTO
        List<SessionHistoryDTO> sessionDTOs = filteredSessions.stream()
                .map(session -> mapSessionEntityToDTO(session, student))
                .toList();

        dto.setSessions(sessionDTOs);
        return dto;
    }

    // ===================== mapSessionEntityToDTO ======================
    private SessionHistoryDTO mapSessionEntityToDTO(SessionEntity session, StudentEntity student) {
        SessionHistoryDTO dto = new SessionHistoryDTO();

        // Si la session n’est plus active (= dévalidée)
        if (Boolean.FALSE.equals(session.getActive())) {
            dto.setSessionId(session.getId());
            dto.setSessionName(session.getTitle());
            dto.setAttendanceStatus("Non renseigné");
            dto.setIsJustified(false);
            dto.setPaymentStatus("Non payé");
            dto.setAmountPaid(0.0);
            return dto;
        }

        // Sinon, la session est valide, on applique la logique habituelle
        dto.setSessionId(session.getId());
        dto.setSessionName(session.getTitle());
        dto.setSessionDate(session.getSessionTimeStart());

        // Récupérer l'attendance
        AttendanceEntity attendance = session.getAttendances().stream()
                .filter(a -> a.getStudent().getId().equals(student.getId()))
                .filter(AttendanceEntity::isActive)
                .findFirst()
                .orElse(null);

        if (attendance != null) {
            if (!attendance.isActive()) {
                // attendance inactive
                dto.setAttendanceStatus("Non renseigné");
                dto.setIsJustified(false);
            } else {
                // Présent ou Absent
                dto.setAttendanceStatus(Boolean.TRUE.equals(attendance.getIsPresent()) ? "Présent" : "Absent");
                dto.setIsJustified(attendance.getIsJustified());

                // catchUpSession => si attendance.isCatchUp = true
                dto.setCatchUpSession(Boolean.TRUE.equals(attendance.getIsCatchUp()));
            }
        } else {
            dto.setAttendanceStatus("Non renseigné");
            // Pas d'attendance => catchUpSession = false
            dto.setCatchUpSession(false);
        }

        // Gérer le paiement
        PaymentDetailEntity paymentDetail = session.getPaymentDetails().stream()
                .filter(pd -> pd.getPayment().getStudent().getId().equals(student.getId()))
                .findFirst()
                .orElse(null);

        if (paymentDetail != null) {
            dto.setPaymentStatus(paymentDetail.getPayment().getStatus());
            dto.setAmountPaid(paymentDetail.getAmountPaid());
            dto.setPaymentDate(paymentDetail.getPaymentDate());
        } else {
            dto.setPaymentStatus("Non payé");
            dto.setAmountPaid(0.0);
            dto.setPaymentDate(null);
        }

        return dto;
    }

    // ===================== Helper methods ======================

    /**
     * Récupère la date d'inscription d'un étudiant à un groupe
     * Retourne null si l'étudiant n'est pas inscrit officiellement
     */
    private Date getStudentEnrollmentDate(StudentEntity student, GroupEntity group) {
        return studentGroupRepository.findByGroupIdAndStudentIdAndActiveTrue(group.getId(), student.getId())
                .map(StudentGroupEntity::getDateAssigned)
                .orElse(null);
    }

    /**
     * Filtre les sessions qui sont après la date d'inscription de l'étudiant
     */
    private List<SessionEntity> filterSessionsAfterEnrollment(List<SessionEntity> sessions, Date enrollmentDate) {
        if (enrollmentDate == null) {
            return sessions; // Pas de date d'inscription = toutes les sessions
        }

        return sessions.stream()
                .filter(session -> {
                    Date sessionDate = session.getSessionTimeStart();
                    return sessionDate != null && !sessionDate.before(enrollmentDate);
                })
                .toList();
    }

    /**
     * Calcule le coût total pour les sessions auxquelles l'étudiant a droit (après inscription)
     */
    private double calculateTotalCostForStudent(GroupEntity group, List<SessionEntity> eligibleSessions) {
        double pricePerSession = group.getPrice().getPrice();
        return pricePerSession * eligibleSessions.size();
    }

    private double calculateTotalPaidForSeries(SessionSeriesEntity series, StudentEntity student) {
        return series.getSessions().stream()
                .flatMap(session -> session.getPaymentDetails().stream())
                .filter(pd -> pd.getPayment().getStudent().getId().equals(student.getId()))
                .mapToDouble(PaymentDetailEntity::getAmountPaid)
                .sum();
    }

    private double calculateTotalCostOfSeries(GroupEntity group) {
        double pricePerSession = group.getPrice().getPrice();
        int sessionNumberPerSerie = group.getSessionNumberPerSerie();
        return pricePerSession * sessionNumberPerSerie;
    }
}
