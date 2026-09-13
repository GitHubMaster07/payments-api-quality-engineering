package com.sergevol.qe.payments.payment;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;

    public PaymentService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    public Payment createPayment(
            UUID accountId,
            BigDecimal amount,
            String currency,
            String merchantReference,
            UUID correlationId
    ) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        Payment payment = new Payment(
                UUID.randomUUID(),
                accountId,
                amount,
                currency,
                PaymentStatus.PENDING,
                merchantReference,
                now,
                now,
                0L,
                correlationId
        );

        paymentRepository.insert(payment);

        return payment;
    }

    public Optional<Payment> findById(UUID paymentId) {
        return paymentRepository.findById(paymentId);
    }
}
