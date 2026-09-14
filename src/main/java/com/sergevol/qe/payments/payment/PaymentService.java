package com.sergevol.qe.payments.payment;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;

    public PaymentService(
            PaymentRepository paymentRepository
    ) {
        this.paymentRepository = paymentRepository;
    }

    public Payment createPayment(
            UUID accountId,
            BigDecimal amount,
            String currency,
            String merchantReference,
            UUID correlationId
    ) {
        OffsetDateTime now =
                OffsetDateTime.now(ZoneOffset.UTC);

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

    public Optional<Payment> findById(
            UUID paymentId
    ) {
        return paymentRepository.findById(paymentId);
    }

    @Transactional
    public Payment transitionStatus(
            UUID paymentId,
            PaymentStatus targetStatus
    ) {
        Payment currentPayment =
                paymentRepository.findById(paymentId)
                        .orElseThrow(
                                () -> new PaymentNotFoundException(
                                        paymentId
                                )
                        );

        if (!PaymentStateTransitions.canTransition(
                currentPayment.status(),
                targetStatus
        )) {
            throw new InvalidPaymentStateTransitionException(
                    currentPayment.status(),
                    targetStatus
            );
        }

        OffsetDateTime now =
                OffsetDateTime.now(ZoneOffset.UTC);

        int updatedRows =
                paymentRepository.updateStatus(
                        paymentId,
                        targetStatus,
                        currentPayment.version(),
                        now
                );

        if (updatedRows != 1) {
            throw new PaymentVersionConflictException(
                    paymentId
            );
        }

        paymentRepository.insertAudit(
                UUID.randomUUID(),
                paymentId,
                currentPayment.status(),
                targetStatus,
                now
        );

        return paymentRepository.findById(paymentId)
                .orElseThrow(
                        () -> new PaymentNotFoundException(
                                paymentId
                        )
                );
    }
}
