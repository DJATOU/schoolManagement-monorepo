package com.school.management.service.correction;

/**
 * Une correction, exécutable par le {@link CorrectionRunner} en Aperçu comme en confirmation
 * (spec admin-corrections, D7).
 *
 * <p>Une seule implémentation sert les deux modes : c'est ce qui garantit que l'Aperçu annonce
 * exactement ce que la confirmation écrira. Les trois méthodes sont appelées dans la transaction
 * du {@link CorrectionRunner}, dans l'ordre {@link #scope()}, puis {@link #execute()} ;
 * {@link #fingerprint()} peut l'être à tout moment.</p>
 *
 * <p>Une correction refusée lève une {@code CustomServiceException} : la transaction est annulée,
 * rien n'est écrit, et le motif remonte tel quel.</p>
 *
 * @param <T> type du résultat rendu à la confirmation
 */
public interface CorrectionCommand<T> {

    /**
     * Description canonique de la commande et de ses paramètres, par exemple
     * {@code "ENCASHMENT_CANCEL|42|WRONG_AMOUNT|"}.
     *
     * <p>Elle entre dans l'empreinte de l'Aperçu : un jeton obtenu pour une commande ne confirme
     * pas une autre commande dont l'Aperçu serait identique.</p>
     */
    String fingerprint();

    /** Séries susceptibles d'être touchées, déterminées avant toute écriture. */
    CorrectionScope scope();

    /** Exécute la correction et rapporte ses effets. */
    CorrectionExecution<T> execute();
}
