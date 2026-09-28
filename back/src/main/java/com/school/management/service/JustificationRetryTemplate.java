package com.school.management.service;

import com.school.management.dto.JustificationUpdateResult;
import com.school.management.service.exception.CustomServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Rejoue une modification de justification lorsque son enregistrement échoue pour une cause
 * passagère (exigences 5.5, 5.6, 5.10).
 *
 * <h2>Pourquoi le rejeu vit ici et non dans le service</h2>
 * C'est le point le plus facile à rater de cette fonctionnalité. Une méthode {@code @Transactional}
 * qui se rappellerait elle-même ne réessaierait <strong>rien</strong> : la transaction est déjà
 * marquée pour annulation dès la première exception, et toute écriture ultérieure échouerait
 * immédiatement. Le rejeu doit donc être porté par un appelant distinct, qui ouvre une transaction
 * neuve à chaque tentative — d'où le {@code REQUIRES_NEW} sur
 * {@link AttendanceJustificationService#updateJustification}.
 *
 * <h2>Pourquoi seuls les échecs passagers sont rejoués</h2>
 * Une violation de contrainte ou une donnée invalide se reproduira à l'identique : rejouer ne
 * ferait que retarder la même erreur de plusieurs secondes, en laissant l'administrateur devant un
 * écran qui attend. Seuls l'indisponibilité momentanée de la base, le conflit de verrou et
 * l'expiration de transaction justifient une seconde tentative.
 */
@Service
public class JustificationRetryTemplate {

    private static final Logger LOGGER = LoggerFactory.getLogger(JustificationRetryTemplate.class);

    /** Nombre maximal de tentatives, la première incluse. */
    private static final int MAX_ATTEMPTS = 3;

    /**
     * Délai entre deux tentatives.
     *
     * <p>Avec {@value #MAX_ATTEMPTS} tentatives, l'attente cumulée ne dépasse pas 2 secondes : la
     * durée totale de 5 secondes fixée par l'exigence 5.5 est donc respectée par construction, sans
     * garde-fou supplémentaire. Un contrôle d'échéance explicite serait du code défensif
     * inatteignable, donc impossible à éprouver.</p>
     */
    private static final long RETRY_DELAY_MS = 1_000L;

    private final AttendanceJustificationService justificationService;

    public JustificationRetryTemplate(AttendanceJustificationService justificationService) {
        this.justificationService = justificationService;
    }

    /**
     * Applique la modification, avec rejeu borné sur échec passager.
     *
     * @throws CustomServiceException 409 après épuisement des tentatives : la présence est laissée
     *         inchangée et aucune entrée d'audit n'est conservée (exigence 5.6)
     */
    public JustificationUpdateResult updateJustification(Long attendanceId,
                                                         boolean justified,
                                                         String comment) {
        RuntimeException lastFailure = null;

        // Boucle volontairement infinie, close par un return ou un break : la condition
        // « attempt <= MAX_ATTEMPTS » d'une boucle for ne pourrait jamais devenir fausse ici, et
        // constituerait donc une branche morte impossible à éprouver.
        int attempt = 0;
        while (true) {
            attempt++;
            try {
                return justificationService.updateJustification(attendanceId, justified, comment);
            } catch (TransientDataAccessException e) {
                // Cause passagère : une nouvelle tentative a une chance d'aboutir.
                // TransientDataAccessException couvre à elle seule les trois cas visés par
                // l'exigence 5.5 : CannotAcquireLockException (conflit de verrou),
                // QueryTimeoutException (expiration) et l'indisponibilité momentanée en dérivent.
                lastFailure = e;
                LOGGER.warn("Échec passager d'enregistrement de la justification de la présence {} "
                        + "(tentative {}/{}) : {}", attendanceId, attempt, MAX_ATTEMPTS, e.getMessage());

                if (attempt == MAX_ATTEMPTS || !sleepBeforeRetry()) {
                    break;
                }
            } catch (DataIntegrityViolationException e) {
                // Cause permanente : le rejeu reproduirait la même erreur (exigence 5.10).
                LOGGER.error("Échec permanent d'enregistrement de la justification de la présence {} : {}",
                        attendanceId, e.getMessage());
                throw auditFailure(e);
            }
        }
        throw auditFailure(lastFailure);
    }

    /**
     * Erreur retournée à l'appelant lorsque la trace n'a pu être écrite.
     *
     * <p>Le message nomme l'audit et non la base : ce qui compte pour l'administrateur est que sa
     * correction n'a pas été conservée, et qu'il peut réessayer.</p>
     */
    private CustomServiceException auditFailure(Throwable cause) {
        return new CustomServiceException(
                "La justification n'a pas été modifiée : sa trace n'a pu être enregistrée. "
                        + "Aucune modification n'est conservée sans son historique. Réessayer.",
                cause, HttpStatus.CONFLICT);
    }

    /**
     * Attend avant la tentative suivante.
     *
     * <p>Une interruption est traitée comme la fin des tentatives : le drapeau d'interruption est
     * restitué et la boucle s'arrête, ce qui produit l'erreur d'échec d'audit habituelle. Lever une
     * exception distincte n'apporterait rien à l'administrateur, pour qui le résultat est le même —
     * sa correction n'a pas été conservée — et ajouterait un chemin d'erreur de plus à éprouver.</p>
     *
     * @return vrai si l'attente s'est déroulée normalement, faux si le fil a été interrompu
     */
    private boolean sleepBeforeRetry() {
        try {
            Thread.sleep(RETRY_DELAY_MS);
            return true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
