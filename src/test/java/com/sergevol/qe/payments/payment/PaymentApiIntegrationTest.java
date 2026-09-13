package com.sergevol.qe.payments.payment;

import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class PaymentApiIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    void createsPersistsAndRetrievesPayment() {
        UUID accountId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();

        BigDecimal amount = new BigDecimal("125.5000");
        String currency = "USD";
        String merchantReference = "order-" + UUID.randomUUID();

        jdbcTemplate.update(
                """
                INSERT INTO accounts (
                    account_id,
                    owner_user_id,
                    status,
                    created_at
                )
                VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                """,
                accountId,
                ownerUserId,
                "ACTIVE"
        );

        Map<String, Object> requestBody = Map.of(
                "accountId", accountId.toString(),
                "amount", amount,
                "currency", currency,
                "merchantReference", merchantReference
        );

        String paymentId =
                given()
                        .contentType("application/json")
                        .header("X-Correlation-ID", correlationId.toString())
                        .body(requestBody)
                        .when()
                        .post("/api/v1/payments")
                        .then()
                        .statusCode(202)
                        .body("status", equalTo("PENDING"))
                        .extract()
                        .path("paymentId");

        UUID persistedPaymentId = UUID.fromString(paymentId);

        String retrievedAmount =
                given()
                        .pathParam("paymentId", persistedPaymentId)
                        .when()
                        .get("/api/v1/payments/{paymentId}")
                        .then()
                        .statusCode(200)
                        .body("paymentId", equalTo(paymentId))
                        .body("accountId", equalTo(accountId.toString()))
                        .body("currency", equalTo(currency))
                        .body("status", equalTo("PENDING"))
                        .body("merchantReference", equalTo(merchantReference))
                        .body("correlationId", equalTo(correlationId.toString()))
                        .extract()
                        .jsonPath()
                        .getString("amount");

        assertThat(new BigDecimal(retrievedAmount))
                .isEqualByComparingTo(amount);

        Map<String, Object> persistedPayment = jdbcTemplate.queryForMap(
                """
                SELECT
                    payment_id,
                    account_id,
                    amount,
                    currency,
                    status,
                    merchant_reference,
                    correlation_id
                FROM payments
                WHERE payment_id = ?
                """,
                persistedPaymentId
        );

        assertThat(persistedPayment.get("payment_id"))
                .isEqualTo(persistedPaymentId);

        assertThat(persistedPayment.get("account_id"))
                .isEqualTo(accountId);

        assertThat((BigDecimal) persistedPayment.get("amount"))
                .isEqualByComparingTo(amount);

        assertThat(persistedPayment.get("currency"))
                .isEqualTo(currency);

        assertThat(persistedPayment.get("status"))
                .isEqualTo("PENDING");

        assertThat(persistedPayment.get("merchant_reference"))
                .isEqualTo(merchantReference);

        assertThat(persistedPayment.get("correlation_id"))
                .isEqualTo(correlationId);
    }
}
