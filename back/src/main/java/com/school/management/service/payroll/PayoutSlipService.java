package com.school.management.service.payroll;

import com.school.management.dto.payroll.PayoutSlipDTO;
import com.school.management.persistance.PayoutSlipIssuanceEntity;
import com.school.management.persistance.TeacherPayoutEntity;
import com.school.management.repository.PayoutSlipIssuanceRepository;
import com.school.management.repository.TeacherPayoutRepository;
import com.school.management.service.exception.CustomServiceException;
import org.springframework.data.domain.AuditorAware;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;

/**
 * Bordereaux de paie : chaque impression est enregistrée, pour que les réimpressions portent
 * « DUPLICATA » (spec teacher-payroll, exigence 5.3). Même modèle que le reçu de remboursement.
 *
 * <p>Une paie annulée reste imprimable : son bordereau porte « ANNULÉE » et la paie qui la remplace,
 * ce qui permet de reprendre un bordereau remis par erreur (exigence 5.4). Le document lui-même est
 * rendu côté client.</p>
 */
@Service
public class PayoutSlipService {

    private static final String SYSTEM = "system";
    private static final int MAX_FILE_NAME_LENGTH = 150;

    private final TeacherPayoutRepository payoutRepository;
    private final PayoutSlipIssuanceRepository issuanceRepository;
    private final AuditorAware<String> auditorAware;

    public PayoutSlipService(TeacherPayoutRepository payoutRepository,
                             PayoutSlipIssuanceRepository issuanceRepository,
                             AuditorAware<String> auditorAware) {
        this.payoutRepository = payoutRepository;
        this.issuanceRepository = issuanceRepository;
        this.auditorAware = auditorAware;
    }

    /**
     * Enregistre une impression du bordereau d'une paie et en retourne les données.
     *
     * @throws CustomServiceException 404 si la paie est introuvable
     */
    @Transactional
    public PayoutSlipDTO issue(Long payoutId) {
        TeacherPayoutEntity payout = payoutRepository.findById(Objects.requireNonNull(payoutId, "payoutId"))
                .orElseThrow(() -> new CustomServiceException("Paie introuvable : " + payoutId, HttpStatus.NOT_FOUND));

        int rank = issuanceRepository.findMaxRank(payoutId) + 1;
        LocalDateTime issuedAt = LocalDateTime.now();
        String issuedBy = auditorAware.getCurrentAuditor().filter(name -> !name.isBlank()).orElse(SYSTEM);
        issuanceRepository.save(PayoutSlipIssuanceEntity.builder()
                .payout(payout).rank(rank).issuedAt(issuedAt).issuedBy(issuedBy).build());

        return new PayoutSlipDTO(TeacherPayoutService.toDto(payout), rank, issuedAt, issuedBy, fileName(payout));
    }

    /**
     * {@code paie-2030-0001_nadia_ait_ahmed.pdf} : numéro et enseignant, sans accents ni ponctuation.
     * Identique d'une impression à l'autre, pour qu'un duplicata se classe à côté de l'original.
     */
    static String fileName(TeacherPayoutEntity payout) {
        String teacher = TeacherPayoutService.fullName(payout.getTeacher());
        String slug = Normalizer.normalize(teacher, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("\\s+", "_")
                .replaceAll("[^A-Za-z0-9_-]", "")
                .replaceAll("^_+|_+$", "");
        if (slug.isBlank()) {
            slug = "enseignant";
        }
        String base = payout.getPayoutNumber() + "_" + slug;
        if (base.length() > MAX_FILE_NAME_LENGTH) {
            base = base.substring(0, MAX_FILE_NAME_LENGTH);
        }
        return base.toLowerCase(Locale.ROOT) + ".pdf";
    }
}
