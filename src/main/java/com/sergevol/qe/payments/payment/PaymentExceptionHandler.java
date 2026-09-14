package com.sergevol.qe.payments.payment;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class PaymentExceptionHandler {

    private static final String CORRELATION_ID_HEADER =
            "X-Correlation-ID";

    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<ApiError> handlePaymentNotFound(
            PaymentNotFoundException exception,
            HttpServletRequest request
    ) {
        return error(
                HttpStatus.NOT_FOUND,
                "PAYMENT_NOT_FOUND",
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(
            InvalidPaymentStateTransitionException.class
    )
    public ResponseEntity<ApiError> handleInvalidStateTransition(
            InvalidPaymentStateTransitionException exception,
            HttpServletRequest request
    ) {
        return error(
                HttpStatus.CONFLICT,
                "INVALID_PAYMENT_STATE_TRANSITION",
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(PaymentVersionConflictException.class)
    public ResponseEntity<ApiError> handleVersionConflict(
            PaymentVersionConflictException exception,
            HttpServletRequest request
    ) {
        return error(
                HttpStatus.CONFLICT,
                "PAYMENT_VERSION_CONFLICT",
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        return error(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Payment request validation failed",
                request
        );
    }

    private ResponseEntity<ApiError> error(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request
    ) {
        ApiError body = new ApiError(
                code,
                message,
                request.getHeader(CORRELATION_ID_HEADER)
        );

        return ResponseEntity.status(status).body(body);
    }
}
