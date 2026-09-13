package com.sergevol.qe.payments.payment;

import java.util.UUID;

public record CreatePaymentResponse(
        UUID paymentId,
        PaymentStatus status
) {
}
