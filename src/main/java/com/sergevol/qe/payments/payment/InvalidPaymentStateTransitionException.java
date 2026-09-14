package com.sergevol.qe.payments.payment;

public class InvalidPaymentStateTransitionException extends RuntimeException {

    public InvalidPaymentStateTransitionException(
            PaymentStatus currentStatus,
            PaymentStatus targetStatus
    ) {
        super(
                "Payment state transition is not allowed: "
                        + currentStatus
                        + " -> "
                        + targetStatus
        );
    }
}
