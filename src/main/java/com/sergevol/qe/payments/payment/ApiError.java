package com.sergevol.qe.payments.payment;

public record ApiError(
        String code,
        String message,
        String correlationId
) {
}
