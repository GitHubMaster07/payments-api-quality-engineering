package com.sergevol.qe.payments.payment;

import java.math.BigDecimal;
import java.util.UUID;

public record CreatePaymentRequest(
        UUID accountId,
        BigDecimal amount,
        String currency,
        String merchantReference
) {
}
