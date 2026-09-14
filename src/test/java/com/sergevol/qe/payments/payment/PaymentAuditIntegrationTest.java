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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class PaymentAuditIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(
                    "postgres:17-alpine"
            );

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PaymentService paymentService;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update(
                "DELETE FROM payment_audit"
        );
        jdbcTemplate.update(
                "DELETE FROM payments"
        );
        jdbcTemplate.update(
                "DELETE FROM accounts"
        );
    }

    @Test
    void createsOneAuditRecordForSuccessfulTransition() {
        UUID paymentId =
                createPayment(
                        PaymentStatus.PENDING,
                        0L
                );

        paymentService.transitionStatus(
                paymentId,
                PaymentStatus.CANCELLED
        );

        List<Map<String, Object>> auditRows =
                jdbcTemplate.queryForList(
                        """
                        SELECT
                            payment_id,
                            from_status,
                            to_status,
                            created_at
                        FROM payment_audit
                        WHERE payment_id = ?
                        """,
                        paymentId
                );

        assertThat(auditRows)
                .hasSize(1);

        Map<String, Object> audit =
                auditRows.getFirst();

        assertThat(
                audit.get("payment_id")
        ).isEqualTo(paymentId);

        assertThat(
                audit.get("from_status")
        ).isEqualTo("PENDING");

        assertThat(
                audit.get("to_status")
        ).isEqualTo("CANCELLED");

        assertThat(
                audit.get("created_at")
        ).isNotNull();
    }

    @Test
    void doesNotCreateAuditForRejectedTransition() {
        UUID paymentId =
                createPayment(
                        PaymentStatus.PROCESSING,
                        2L
                );

        assertThatThrownBy(
                () -> paymentService.transitionStatus(
                        paymentId,
                        PaymentStatus.CANCELLED
                )
        ).isInstanceOf(
                InvalidPaymentStateTransitionException.class
        );

        Integer auditCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM payment_audit
                        WHERE payment_id = ?
                        """,
                        Integer.class,
                        paymentId
                );

        assertThat(auditCount)
                .isZero();

        Payment persistedPayment =
                paymentService.findById(paymentId)
                        .orElseThrow();

        assertThat(
                persistedPayment.status()
        ).isEqualTo(
                PaymentStatus.PROCESSING
        );

        assertThat(
                persistedPayment.version()
        ).isEqualTo(2L);
    }

    @Test
    void createsOneAuditRecordPerSuccessfulTransition() {
        UUID paymentId =
                createPayment(
                        PaymentStatus.PENDING,
                        0L
                );

        paymentService.transitionStatus(
                paymentId,
                PaymentStatus.APPROVED
        );

        paymentService.transitionStatus(
                paymentId,
                PaymentStatus.PROCESSING
        );

        Integer auditCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM payment_audit
                        WHERE payment_id = ?
                        """,
                        Integer.class,
                        paymentId
                );

        assertThat(auditCount)
                .isEqualTo(2);

        List<String> transitions =
                jdbcTemplate.query(
                        """
                        SELECT
                            from_status,
                            to_status
                        FROM payment_audit
                        WHERE payment_id = ?
                        """,
                        (resultSet, rowNum) ->
                                resultSet.getString(
                                        "from_status"
                                )
                                        + "->"
                                        + resultSet.getString(
                                        "to_status"
                                ),
                        paymentId
                );

        assertThat(transitions)
                .containsExactlyInAnyOrder(
                        "PENDING->APPROVED",
                        "APPROVED->PROCESSING"
                );
    }

    private UUID createPayment(
            PaymentStatus status,
            long version
    ) {
        UUID accountId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();

        OffsetDateTime now =
                OffsetDateTime.now(
                        ZoneOffset.UTC
                );

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
                "audit-" + UUID.randomUUID(),
                now,
                now,
                version,
                correlationId
        );

        return paymentId;
    }
}
