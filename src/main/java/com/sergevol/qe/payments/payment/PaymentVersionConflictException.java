package com.sergevol.qe.payments.payment;

import java.util.UUID;

public class PaymentVersionConflictException extends RuntimeException {

    public PaymentVersionConflictException(UUID paymentId) {
        super("Payment version conflict: " + paymentId);
    }
}
