package com.sergevol.qe.payments.payment;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PaymentRepository {

    private final JdbcTemplate jdbcTemplate;

    public PaymentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(Payment payment) {
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
                payment.paymentId(),
                payment.accountId(),
                payment.amount(),
                payment.currency(),
                payment.status().name(),
                payment.merchantReference(),
                payment.createdAt(),
                payment.updatedAt(),
                payment.version(),
                payment.correlationId()
        );
    }

    public Optional<Payment> findById(UUID paymentId) {
        return jdbcTemplate.query(
                """
                SELECT
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
                FROM payments
                WHERE payment_id = ?
                """,
                this::mapPayment,
                paymentId
        ).stream().findFirst();
    }

    private Payment mapPayment(ResultSet resultSet, int rowNum) throws SQLException {
        return new Payment(
                resultSet.getObject("payment_id", UUID.class),
                resultSet.getObject("account_id", UUID.class),
                resultSet.getBigDecimal("amount"),
                resultSet.getString("currency"),
                PaymentStatus.valueOf(resultSet.getString("status")),
                resultSet.getString("merchant_reference"),
                resultSet.getObject("created_at", java.time.OffsetDateTime.class),
                resultSet.getObject("updated_at", java.time.OffsetDateTime.class),
                resultSet.getLong("version"),
                resultSet.getObject("correlation_id", UUID.class)
        );
    }
}
