package com.sergevol.qe.payments.payment;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<CreatePaymentResponse> createPayment(
            @RequestHeader("X-Correlation-ID") UUID correlationId,
            @RequestBody CreatePaymentRequest request
    ) {
        Payment payment = paymentService.createPayment(
                request.accountId(),
                request.amount(),
                request.currency(),
                request.merchantReference(),
                correlationId
        );

        CreatePaymentResponse response = new CreatePaymentResponse(
                payment.paymentId(),
                payment.status()
        );

        return ResponseEntity.accepted().body(response);
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<Payment> getPayment(@PathVariable UUID paymentId) {
        return paymentService.findById(paymentId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
