package com.sergevol.qe.payments.payment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class PaymentStatePersistenceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PaymentService paymentService;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM payment_audit");
        jdbcTemplate.update("DELETE FROM payments");
        jdbcTemplate.update("DELETE FROM accounts");
    }

    @Test
    void transitionsPendingPaymentToApprovedAndIncrementsVersion() {
        UUID paymentId = createPayment(PaymentStatus.PENDING, 0L);

        Payment transitionedPayment =
                paymentService.transitionStatus(
                        paymentId,
                        PaymentStatus.APPROVED
                );

        assertThat(transitionedPayment.status())
                .isEqualTo(PaymentStatus.APPROVED);

        assertThat(transitionedPayment.version())
                .isEqualTo(1L);

        Payment persistedPayment =
                paymentService.findById(paymentId)
                        .orElseThrow();

        assertThat(persistedPayment.status())
                .isEqualTo(PaymentStatus.APPROVED);

        assertThat(persistedPayment.version())
                .isEqualTo(1L);
    }

    @Test
    void rejectsInvalidTransitionWithoutChangingPersistedState() {
        UUID paymentId = createPayment(PaymentStatus.PROCESSING, 2L);

        assertThatThrownBy(
                () -> paymentService.transitionStatus(
                        paymentId,
                        PaymentStatus.CANCELLED
                )
        )
                .isInstanceOf(
                        InvalidPaymentStateTransitionException.class
                )
                .hasMessageContaining(
                        "PROCESSING -> CANCELLED"
                );

        Payment persistedPayment =
                paymentService.findById(paymentId)
                        .orElseThrow();

        assertThat(persistedPayment.status())
                .isEqualTo(PaymentStatus.PROCESSING);

        assertThat(persistedPayment.version())
                .isEqualTo(2L);
    }

    @Test
    void rejectsTransitionForMissingPayment() {
        UUID missingPaymentId = UUID.randomUUID();

        assertThatThrownBy(
                () -> paymentService.transitionStatus(
                        missingPaymentId,
                        PaymentStatus.APPROVED
                )
        )
                .isInstanceOf(PaymentNotFoundException.class)
                .hasMessageContaining(missingPaymentId.toString());
    }

    private UUID createPayment(
            PaymentStatus status,
            long version
    ) {
        UUID accountId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        jdbcTemplate.update(
                """
                INSERT INTO accounts (
                    account_id,
                    owner_user_id,
                    status,
                    created_at
                )
                VALUES (?, ?, ?, ?)
                """,
                accountId,
                ownerUserId,
                "ACTIVE",
                now
        );

        jdbcTemplate.update(
                """
                INSERT INTO payments (
                    payment_id,
                    account_id,
                    amount,
                    currency,
                    status,
                    merchant_reference,
                    created_at,
                    updated_at,
                    version,
                    correlation_id
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                paymentId,
                accountId,
                new BigDecimal("125.5000"),
                "USD",
                status.name(),
                "order-" + UUID.randomUUID(),
                now,
                now,
                version,
                correlationId
        );

        return paymentId;
    }
}
