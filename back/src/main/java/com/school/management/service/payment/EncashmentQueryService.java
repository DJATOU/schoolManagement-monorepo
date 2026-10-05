package com.school.management.service.payment;

import com.school.management.dto.payment.EncashmentDTO;
import com.school.management.persistance.EncashmentAllocationEntity;
import com.school.management.persistance.EncashmentEntity;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.repository.EncashmentAllocationRepository;
import com.school.management.repository.EncashmentRepository;
import com.school.management.repository.StudentRepository;
import com.school.management.service.exception.CustomServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * Lecture des Encaissements : reçu, réimpression, historique de l'élève (spec admin-corrections,
 * A.8 et A.9). Aucune écriture ici.
 *
 * <p>Un Encaissement annulé reste lisible et listé : il a eu lieu, et sa trace d'annulation est
 * ce qui répond à un parent qui présente le reçu.</p>
 */
@Service
public class EncashmentQueryService {

    private final EncashmentRepository encashmentRepository;
    private final EncashmentAllocationRepository allocationRepository;
    private final StudentRepository studentRepository;

    public EncashmentQueryService(EncashmentRepository encashmentRepository,
                                  EncashmentAllocationRepository allocationRepository,
                                  StudentRepository studentRepository) {
        this.encashmentRepository = encashmentRepository;
        this.allocationRepository = allocationRepository;
        this.studentRepository = studentRepository;
    }

    /**
     * Un Encaissement, avec sa répartition.
     *
     * @throws CustomServiceException 404 s'il est introuvable
     */
    @Transactional(readOnly = true)
    public EncashmentDTO get(Long id) {
        EncashmentEntity encashment = encashmentRepository.findById(Objects.requireNonNull(id, "id"))
                .orElseThrow(() -> new CustomServiceException(
                        "Encaissement introuvable : " + id, HttpStatus.NOT_FOUND));
        return toDto(encashment);
    }

    /**
     * Encaissements d'un étudiant, le plus récent d'abord, annulés compris.
     *
     * @throws CustomServiceException 404 si l'étudiant est introuvable
     */
    @Transactional(readOnly = true)
    public List<EncashmentDTO> forStudent(Long studentId) {
        if (!studentRepository.existsById(Objects.requireNonNull(studentId, "studentId"))) {
            throw new CustomServiceException("Étudiant introuvable : " + studentId, HttpStatus.NOT_FOUND);
        }
        return encashmentRepository.findByStudentIdOrderByReceivedAtDescIdDesc(studentId).stream()
                .map(this::toDto)
                .toList();
    }

    private EncashmentDTO toDto(EncashmentEntity encashment) {
        StudentEntity student = encashment.getStudent();
        GroupEntity group = encashment.getGroup();
        SessionSeriesEntity target = encashment.getTargetSeries();

        List<EncashmentDTO.AllocationDTO> allocations =
                allocationRepository.findByEncashmentIdOrderByIdAsc(encashment.getId()).stream()
                        .map(EncashmentQueryService::toDto)
                        .toList();

        return new EncashmentDTO(
                encashment.getId(),
                encashment.getReceiptNumber(),
                encashment.getStatus() == null ? null : encashment.getStatus().name(),
                encashment.getKind() == null ? null : encashment.getKind().name(),
                encashment.getAmountReceived(),
                encashment.getPaymentMethod(),
                encashment.getNotes(),
                encashment.getReceivedAt(),
                encashment.getReceivedBy(),
                student == null ? null : student.getId(),
                student == null ? null : fullName(student),
                group == null ? null : group.getId(),
                group == null ? null : group.getName(),
                target == null ? null : target.getId(),
                target == null ? null : target.getName(),
                allocations,
                encashment.getCancelledAt(),
                encashment.getCancelledBy(),
                encashment.getCancelReasonType() == null ? null : encashment.getCancelReasonType().name(),
                encashment.getCancelReasonText(),
                encashment.getReplaces() == null ? null : encashment.getReplaces().getReceiptNumber(),
                encashment.getReplacedBy() == null ? null : encashment.getReplacedBy().getReceiptNumber());
    }

    private static EncashmentDTO.AllocationDTO toDto(EncashmentAllocationEntity allocation) {
        SessionSeriesEntity series = allocation.getSeries();
        return new EncashmentDTO.AllocationDTO(
                series == null ? null : series.getId(),
                series == null ? null : series.getName(),
                allocation.getAmount(),
                Boolean.TRUE.equals(allocation.getCarriedOver()),
                Boolean.TRUE.equals(allocation.getActive()));
    }

    private static String fullName(StudentEntity student) {
        String first = student.getFirstName() == null ? "" : student.getFirstName().trim();
        String last = student.getLastName() == null ? "" : student.getLastName().trim();
        return (first + " " + last).trim();
    }
}
