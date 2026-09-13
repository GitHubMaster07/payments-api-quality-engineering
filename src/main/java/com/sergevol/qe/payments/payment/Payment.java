package com.sergevol.qe.payments.payment;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record Payment(
        UUID paymentId,
        UUID accountId,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        String merchantReference,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        long version,
        UUID correlationId
) {
}
