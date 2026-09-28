import { SessionHistoryDTO } from "../session/SessionHistoryDTO";

export interface SeriesHistoryDTO {
    seriesId: number;
    seriesName: string;
    sessions: SessionHistoryDTO[];
    paymentStatus: string;
    totalAmountPaid: number;
    /**
     * Coût de la série au prorata : séances facturables × prix net, et non
     * `total_sessions × prix` (exigences 11.1, 11.6). Un étudiant arrivé en cours de série
     * paie donc moins que le coût nominal, sans être en retard pour autant.
     */
    totalCost: number;
    /**
     * Part du montant versé réellement affectée à des séances. Différente de
     * `totalAmountPaid` en cas de trop-perçu : c'est ce total-là que le détail des séances
     * justifie ligne par ligne.
     */
    totalAllocated?: number;
    /** Trop-perçu : part versée au-delà du coût de la série. */
    totalOverpaid?: number;
    // Exemption : vrai lorsque l'étudiant est exempté (réduction 100 %) pour cette série ;
    // pilote la légende « Présent et exempté ».
    isExempted?: boolean;
    // Total remboursé sur la série
    totalRefunded?: number;
    /**
     * Nombre de séances facturables retenues dans `totalCost` (exigence 11.6).
     *
     * Optionnel : les réponses d'une version antérieure du serveur ne le portent pas. Quand il
     * est absent, le décompte est déduit des séances affichées (voir
     * `shared/session-billing.ts`).
     */
    billableSessions?: number;
    /**
     * Prix d'une séance après réduction, tel que retenu pour facturer cet étudiant.
     *
     * Sert à énoncer le coût en clair : « 2 séances × 6 000 DA = 12 000 DA ».
     * Optionnel : les réponses d'une version antérieure du serveur ne le portent pas.
     */
    unitPriceNet?: number;
    /**
     * Tarif catalogue d'une séance, avant réduction.
     *
     * N'entre dans aucun calcul : il n'est affiché, barré, que lorsqu'une réduction s'applique,
     * pour que le prix net réduit ne paraisse pas arbitraire.
     */
    unitPriceGross?: number;
  }
