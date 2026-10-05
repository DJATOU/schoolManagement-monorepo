package com.school.management.service.payroll;

import com.school.management.dto.payroll.TeacherPayRateDTO;
import com.school.management.dto.payroll.TeacherPayRateRequest;
import com.school.management.persistance.TeacherPayRateEntity;
import com.school.management.repository.TeacherPayRateRepository;
import com.school.management.service.exception.CustomServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Catalogue des taux de rémunération des enseignants (spec teacher-payroll, exigence 1).
 *
 * <p>Entretenu comme les tarifs : créer, modifier, désactiver. Rien n'est supprimé, et rien de ce qui
 * se fait ici ne touche une paie déjà enregistrée : elle porte une copie figée du libellé et du
 * pourcentage (exigence 1.6).</p>
 */
@Service
public class TeacherPayRateService {

    /** Longueur maximale d'un libellé, alignée sur la colonne. */
    static final int MAX_LABEL_LENGTH = 100;

    /** Identifiant qu'aucun taux ne porte : à exclure lors d'une création. */
    private static final long NO_RATE = -1L;

    private final TeacherPayRateRepository rateRepository;

    public TeacherPayRateService(TeacherPayRateRepository rateRepository) {
        this.rateRepository = rateRepository;
    }

    /** Tout le catalogue : actifs d'abord, par pourcentage croissant. */
    @Transactional(readOnly = true)
    public List<TeacherPayRateDTO> list() {
        return rateRepository.findAllOrdered().stream().map(TeacherPayRateService::toDto).toList();
    }

    /**
     * Crée un taux.
     *
     * @throws CustomServiceException 400 si le libellé ou le pourcentage est invalide, 409 si un taux
     *                                actif porte déjà ce libellé
     */
    @Transactional
    public TeacherPayRateDTO create(TeacherPayRateRequest request) {
        Objects.requireNonNull(request, "request");
        String label = validLabel(request.label());
        BigDecimal percent = validPercent(request.teacherPercent());
        requireFreeLabel(label, NO_RATE);
        TeacherPayRateEntity rate = rateRepository.save(TeacherPayRateEntity.builder()
                .label(label)
                .teacherPercent(percent)
                .build());
        return toDto(rate);
    }

    /**
     * Modifie le libellé et le pourcentage d'un taux actif. Les paies passées n'en sont pas affectées.
     *
     * @throws CustomServiceException 404 si le taux est introuvable, 409 s'il est désactivé ou si son
     *                                nouveau libellé est déjà pris, 400 si une valeur est invalide
     */
    @Transactional
    public TeacherPayRateDTO update(Long id, TeacherPayRateRequest request) {
        Objects.requireNonNull(request, "request");
        TeacherPayRateEntity rate = found(id);
        if (!Boolean.TRUE.equals(rate.getActive())) {
            throw new CustomServiceException("Le taux « " + rate.getLabel()
                    + " » est désactivé : créez-en un nouveau plutôt que de le modifier.", HttpStatus.CONFLICT);
        }
        String label = validLabel(request.label());
        BigDecimal percent = validPercent(request.teacherPercent());
        requireFreeLabel(label, rate.getId());
        rate.setLabel(label);
        rate.setTeacherPercent(percent);
        return toDto(rateRepository.save(rate));
    }

    /**
     * Désactive un taux : il n'est plus proposé pour une nouvelle paie (exigence 1.5). Sans effet sur
     * un taux déjà désactivé.
     *
     * @throws CustomServiceException 404 si le taux est introuvable
     */
    @Transactional
    public TeacherPayRateDTO disable(Long id) {
        TeacherPayRateEntity rate = found(id);
        if (Boolean.TRUE.equals(rate.getActive())) {
            rate.setActive(false);
            rate = rateRepository.save(rate);
        }
        return toDto(rate);
    }

    /**
     * Taux utilisable pour une nouvelle paie.
     *
     * @throws CustomServiceException 404 s'il est introuvable, 409 s'il est désactivé
     */
    @Transactional(readOnly = true)
    public TeacherPayRateEntity requireActive(Long id) {
        TeacherPayRateEntity rate = found(id);
        if (!Boolean.TRUE.equals(rate.getActive())) {
            throw new CustomServiceException("Le taux « " + rate.getLabel()
                    + " » est désactivé : choisissez un taux actif.", HttpStatus.CONFLICT);
        }
        return rate;
    }

    // ------------------------------------------------------------------

    static TeacherPayRateDTO toDto(TeacherPayRateEntity rate) {
        BigDecimal teacher = rate.getTeacherPercent();
        return new TeacherPayRateDTO(rate.getId(), rate.getLabel(), teacher,
                PayoutCalculator.MAX_PERCENT.subtract(teacher).setScale(2),
                Boolean.TRUE.equals(rate.getActive()));
    }

    private TeacherPayRateEntity found(Long id) {
        return rateRepository.findById(Objects.requireNonNull(id, "id"))
                .orElseThrow(() -> new CustomServiceException(
                        "Taux de rémunération introuvable : " + id, HttpStatus.NOT_FOUND));
    }

    private static String validLabel(String raw) {
        String label = raw == null ? "" : raw.strip();
        if (label.isEmpty()) {
            throw new CustomServiceException("Le libellé du taux est obligatoire.", HttpStatus.BAD_REQUEST);
        }
        if (label.length() > MAX_LABEL_LENGTH) {
            throw new CustomServiceException("Le libellé du taux ne peut pas dépasser "
                    + MAX_LABEL_LENGTH + " caractères.", HttpStatus.BAD_REQUEST);
        }
        return label;
    }

    private static BigDecimal validPercent(BigDecimal raw) {
        if (raw == null) {
            throw new CustomServiceException("Le pourcentage de l'enseignant est obligatoire.",
                    HttpStatus.BAD_REQUEST);
        }
        try {
            return PayoutCalculator.requireValidPercent(raw);
        } catch (IllegalArgumentException e) {
            throw new CustomServiceException(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    private void requireFreeLabel(String label, Long excludedId) {
        if (rateRepository.existsActiveLabel(label, excludedId)) {
            throw new CustomServiceException("Un taux actif porte déjà le libellé « " + label + " ».",
                    HttpStatus.CONFLICT);
        }
    }
}
