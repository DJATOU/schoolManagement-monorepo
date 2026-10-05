package com.school.management.util;

import com.school.management.service.correction.RefundFloorException;
import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * Corps du 409 d'une correction qui ferait passer un versé sous le remboursé (spec
 * admin-corrections, exigence 2.4).
 *
 * @param status          {@code CONFLICT}
 * @param message         motif du refus, remboursements nommés
 * @param errorCode       {@code REFUND_FLOOR}
 * @param blockingRefunds remboursements en cause
 */
public record RefundFloorErrorResponse(HttpStatus status, String message, String errorCode,
                                       List<RefundFloorException.BlockingRefund> blockingRefunds) {
}
