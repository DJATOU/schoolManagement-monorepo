package com.school.management.service;

import com.school.management.dto.PaymentDetailDTO;
import com.school.management.persistance.PaymentDetailEntity;
import com.school.management.repository.PaymentDetailRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PaymentDetailService {

    private final PaymentDetailRepository paymentDetailRepository;

    @Autowired
    public PaymentDetailService(PaymentDetailRepository paymentDetailRepository) {
        this.paymentDetailRepository = paymentDetailRepository;
    }

    public List<PaymentDetailDTO> getPaymentDetailsForSessionsInSeries(Long studentId, Long sessionSeriesId) {
        List<PaymentDetailEntity> paymentDetails = paymentDetailRepository.findByPayment_StudentIdAndSession_SessionSeriesId(studentId, sessionSeriesId);
        return paymentDetails.stream()
                .map(this::convertToDto)
                .toList();
    }

    private PaymentDetailDTO convertToDto(PaymentDetailEntity detail) {
        double sessionPrice = detail.getSession().getGroup().getPrice().getPrice();
        double remainingBalance = sessionPrice - detail.getAmountPaid();

        return PaymentDetailDTO.builder()
                .paymentDetailId(detail.getId())
                .sessionId(detail.getSession().getId())
                .sessionName(detail.getSession().getTitle())  // Récupérer le titre de la session
                .amountPaid(detail.getAmountPaid())
                .remainingBalance(remainingBalance)
                .paymentDate(detail.getPaymentDate())  // Ajouter la date de paiement
                .build();
    }


}
