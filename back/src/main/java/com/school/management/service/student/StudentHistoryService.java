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
    private final com.school.management.repository.PaymentDetailRepository paymentDetailRepository;

    public StudentHistoryService(StudentRepository studentRepository,
            AttendanceRepository attendanceRepository,
            StudentGroupRepository studentGroupRepository,
            com.school.management.repository.PaymentDetailRepository paymentDetailRepository) {
        this.studentRepository = studentRepository;
        this.attendanceRepository = attendanceRepository;
        this.studentGroupRepository = studentGroupRepository;
        this.paymentDetailRepository = paymentDetailRepository;
    }

    public StudentFullHistoryDTO getStudentFullHistory(Long studentId) {
        // Récupérer l'étudiant
        StudentEntity student = studentRepository.findById(Objects.requireNonNull(studentId))
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
                .filter(Objects::nonNull) // on enlève les séries qui n'ont aucune session pertinente
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

        // IMPORTANT: Charger tous les payment details ACTIFS (non CANCELLED) pour cette
        // série et cet étudiant en une seule requête
        // Cela évite le problème de lazy loading et améliore les performances
        List<PaymentDetailEntity> paymentDetailsForSeries = paymentDetailRepository
                .findByPayment_StudentIdAndSession_SessionSeriesId(student.getId(), series.getId());

        // FILTRER les paiements CANCELLED
        List<PaymentDetailEntity> activePaymentDetails = paymentDetailsForSeries.stream()
                .filter(pd -> pd.getActive() != null && pd.getActive())
                .filter(pd -> !"CANCELLED".equals(pd.getPayment().getStatus()))
                .toList();

        // Créer une map sessionId -> PaymentDetail pour un accès rapide
        Map<Long, PaymentDetailEntity> paymentDetailMap = activePaymentDetails.stream()
                .collect(Collectors.toMap(
                        pd -> pd.getSession().getId(),
                        pd -> pd,
                        (existing, replacement) -> existing // En cas de doublon, garder le premier
                ));

        // NOUVEAU: Récupérer la date d'inscription de l'étudiant au groupe
        Date enrollmentDate = isOfficial ? getStudentEnrollmentDate(student, group) : null;

        // Récupérer toutes les sessions de la série
        List<SessionEntity> allSessions = series.getSessions().stream()
                .sorted(Comparator.comparing(SessionEntity::getSessionTimeStart))
                .toList();

        // CORRECTION: Pour l'HISTORIQUE COMPLET, afficher toutes les sessions du groupe
        // Le filtrage par date d'inscription s'applique UNIQUEMENT pour le calcul du
        // coût
        List<SessionEntity> filteredSessions;
        if (isOfficial) {
            // Étudiant inscrit au groupe => afficher TOUTES les sessions de la série
            // (même celles avant l'inscription, pour voir l'historique complet)
            filteredSessions = allSessions;
        } else {
            // Rattrapage => afficher uniquement les sessions où l'étudiant a une attendance
            // active
            filteredSessions = allSessions.stream()
                    .filter(session -> session.getAttendances().stream()
                            .anyMatch(a -> a.getStudent().getId().equals(student.getId()) && a.isActive()))
                    .toList();
        }

        // Si le résultat est VIDE => on ne retourne pas cette série (on renvoie null)
        if (filteredSessions.isEmpty()) {
            return null; // => la série n'apparaîtra pas dans le PDF
        }

        // NOUVEAU: Pour le CALCUL DU COÛT, filtrer par date d'inscription
        List<SessionEntity> eligibleSessionsForCost = filterSessionsAfterEnrollment(allSessions, enrollmentDate);

        // NOUVEAU: Calculer le coût total en fonction du type de groupe
        double totalCostForStudent;
        if (isOfficial) {
            // Pour les groupes officiels : coût basé sur les sessions après inscription
            totalCostForStudent = calculateTotalCostForStudent(group, eligibleSessionsForCost);
        } else {
            // Pour les sessions de rattrapage : coût basé UNIQUEMENT sur les sessions
            // où l'étudiant est PRÉSENT avec paiement COMPLÉTÉ
            totalCostForStudent = calculateTotalCostForCatchUpSessions(group, filteredSessions, student);
        }

        double totalPaidForSeries = calculateTotalPaidForSeries(series, student);

        // NOUVEAU: Statut de paiement basé sur les sessions auxquelles l'étudiant a
        // droit
        // Si l'étudiant a payé >= au coût des sessions éligibles, le paiement est
        // complet
        dto.setPaymentStatus(totalPaidForSeries >= totalCostForStudent ? "Complet" : "Partiel");
        dto.setTotalAmountPaid(totalPaidForSeries);
        dto.setTotalCost(totalCostForStudent);

        // 4) Construire la liste finale de SessionHistoryDTO
        List<SessionHistoryDTO> sessionDTOs = filteredSessions.stream()
                .map(session -> mapSessionEntityToDTO(session, student, paymentDetailMap))
                .toList();

        dto.setSessions(sessionDTOs);
        return dto;
    }

    // ===================== mapSessionEntityToDTO ======================
    private SessionHistoryDTO mapSessionEntityToDTO(SessionEntity session, StudentEntity student,
            Map<Long, PaymentDetailEntity> paymentDetailMap) {
        SessionHistoryDTO dto = new SessionHistoryDTO();

        // Si la session n'est plus active (= dévalidée)
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

        // CORRECTION: Utiliser la map pré-chargée au lieu du lazy loading
        // Cela résout le problème où les paiements n'apparaissaient pas pour les
        // sessions de rattrapage
        PaymentDetailEntity paymentDetail = paymentDetailMap.get(session.getId());

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
     * Calcule le coût total pour les sessions auxquelles l'étudiant a droit (après
     * inscription)
     */
    private double calculateTotalCostForStudent(GroupEntity group, List<SessionEntity> eligibleSessions) {
        double pricePerSession = group.getPrice().getPrice();
        return pricePerSession * eligibleSessions.size();
    }

    /**
     * Calcule le coût total pour les sessions de rattrapage
     * Ne compte QUE les sessions où l'étudiant est PRÉSENT avec paiement COMPLÉTÉ
     */
    private double calculateTotalCostForCatchUpSessions(GroupEntity group, List<SessionEntity> sessions,
            StudentEntity student) {
        double pricePerSession = group.getPrice().getPrice();

        // Compter uniquement les sessions de rattrapage EFFECTIVEMENT SUIVIES par
        // l'étudiant (présent)
        long attendedCatchUpSessions = sessions.stream()
                .filter(session -> session.getAttendances().stream()
                        .anyMatch(a -> a.getStudent().getId().equals(student.getId())
                                && a.isActive()
                                && Boolean.TRUE.equals(a.getIsPresent())))
                .count();

        return pricePerSession * attendedCatchUpSessions;
    }

    private double calculateTotalPaidForSeries(SessionSeriesEntity series, StudentEntity student) {
        return series.getSessions().stream()
                .flatMap(session -> session.getPaymentDetails().stream())
                .filter(pd -> pd.getPayment().getStudent().getId().equals(student.getId()))
                .filter(pd -> pd.getActive() != null && pd.getActive())
                .filter(pd -> !"CANCELLED".equals(pd.getPayment().getStatus()))
                .mapToDouble(PaymentDetailEntity::getAmountPaid)
                .sum();
    }
}
