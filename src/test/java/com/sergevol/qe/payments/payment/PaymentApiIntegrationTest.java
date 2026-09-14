package com.sergevol.qe.payments.payment;

import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@Testcontainers
class PaymentApiIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(
                    "postgres:17-alpine"
            );

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;

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
    void createsAndRetrievesPersistedPayment() {
        UUID accountId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();

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

        BigDecimal amount =
                new BigDecimal("125.5000");

        String merchantReference =
                "merchant-" + UUID.randomUUID();

        Map<String, Object> requestBody = Map.of(
                "accountId", accountId.toString(),
                "amount", amount,
                "currency", "USD",
                "merchantReference", merchantReference
        );

        String paymentId =
                given()
                        .contentType("application/json")
                        .header(
                                "X-Correlation-ID",
                                correlationId.toString()
                        )
                        .body(requestBody)
                        .when()
                        .post("/api/v1/payments")
                        .then()
                        .statusCode(202)
                        .body(
                                "paymentId",
                                notNullValue()
                        )
                        .body(
                                "status",
                                equalTo("PENDING")
                        )
                        .extract()
                        .path("paymentId");

        String apiAmount =
                given()
                        .pathParam(
                                "paymentId",
                                paymentId
                        )
                        .when()
                        .get(
                                "/api/v1/payments/{paymentId}"
                        )
                        .then()
                        .statusCode(200)
                        .body(
                                "paymentId",
                                equalTo(paymentId)
                        )
                        .body(
                                "accountId",
                                equalTo(accountId.toString())
                        )
                        .body(
                                "currency",
                                equalTo("USD")
                        )
                        .body(
                                "status",
                                equalTo("PENDING")
                        )
                        .body(
                                "merchantReference",
                                equalTo(merchantReference)
                        )
                        .body(
                                "correlationId",
                                equalTo(
                                        correlationId.toString()
                                )
                        )
                        .extract()
                        .jsonPath()
                        .getString("amount");

        assertThat(
                new BigDecimal(apiAmount)
        ).isEqualByComparingTo(amount);

        Map<String, Object> persistedPayment =
                jdbcTemplate.queryForMap(
                        """
                        SELECT
                            account_id,
                            amount,
                            currency,
                            status,
                            merchant_reference,
                            correlation_id
                        FROM payments
                        WHERE payment_id = ?
                        """,
                        UUID.fromString(paymentId)
                );

        assertThat(
                persistedPayment.get("account_id")
        ).isEqualTo(accountId);

        assertThat(
                (BigDecimal) persistedPayment.get("amount")
        ).isEqualByComparingTo(amount);

        assertThat(
                persistedPayment.get("currency")
        ).isEqualTo("USD");

        assertThat(
                persistedPayment.get("status")
        ).isEqualTo("PENDING");

        assertThat(
                persistedPayment.get(
                        "merchant_reference"
                )
        ).isEqualTo(merchantReference);

        assertThat(
                persistedPayment.get(
                        "correlation_id"
                )
        ).isEqualTo(correlationId);
    }

    @Test
    void rejectsInvalidPaymentRequestWithoutPersistingPayment() {
        UUID accountId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();

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

        Map<String, Object> requestBody =
                new HashMap<>();

        requestBody.put(
                "accountId",
                accountId.toString()
        );
        requestBody.put(
                "amount",
                new BigDecimal("0.00")
        );
        requestBody.put(
                "currency",
                "usd"
        );
        requestBody.put(
                "merchantReference",
                " "
        );

        given()
                .contentType("application/json")
                .header(
                        "X-Correlation-ID",
                        correlationId.toString()
                )
                .body(requestBody)
                .when()
                .post("/api/v1/payments")
                .then()
                .statusCode(400);

        Integer paymentCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM payments
                        WHERE account_id = ?
                        """,
                        Integer.class,
                        accountId
                );

        assertThat(paymentCount)
                .isZero();
    }

    @ParameterizedTest(
            name = "{0}"
    )
    @MethodSource("invalidPaymentRequests")
    void rejectsInvalidPaymentFieldWithoutPersistingPayment(
            String scenario,
            BigDecimal amount,
            String currency,
            String merchantReference
    ) {
        UUID accountId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();

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

        Map<String, Object> requestBody =
                new HashMap<>();

        requestBody.put(
                "accountId",
                accountId.toString()
        );
        requestBody.put(
                "amount",
                amount
        );
        requestBody.put(
                "currency",
                currency
        );
        requestBody.put(
                "merchantReference",
                merchantReference
        );

        given()
                .contentType("application/json")
                .header(
                        "X-Correlation-ID",
                        correlationId.toString()
                )
                .body(requestBody)
                .when()
                .post("/api/v1/payments")
                .then()
                .statusCode(400);

        Integer paymentCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM payments
                        WHERE account_id = ?
                        """,
                        Integer.class,
                        accountId
                );

        assertThat(paymentCount)
                .isZero();
    }

    static Stream<Arguments> invalidPaymentRequests() {
        return Stream.of(
                Arguments.of(
                        "missing amount",
                        null,
                        "USD",
                        "merchant-1"
                ),
                Arguments.of(
                        "zero amount",
                        new BigDecimal("0.00"),
                        "USD",
                        "merchant-2"
                ),
                Arguments.of(
                        "negative amount",
                        new BigDecimal("-1.00"),
                        "USD",
                        "merchant-3"
                ),
                Arguments.of(
                        "lowercase currency",
                        new BigDecimal("10.00"),
                        "usd",
                        "merchant-4"
                ),
                Arguments.of(
                        "invalid currency length",
                        new BigDecimal("10.00"),
                        "US",
                        "merchant-5"
                ),
                Arguments.of(
                        "blank merchant reference",
                        new BigDecimal("10.00"),
                        "USD",
                        " "
                ),
                Arguments.of(
                        "missing merchant reference",
                        new BigDecimal("10.00"),
                        "USD",
                        null
                )
        );
    }

    @Test
    void cancelsPendingPaymentAndPersistsStateChange() {
        UUID accountId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();

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
                "amount", new BigDecimal("75.2500"),
                "currency", "USD",
                "merchantReference",
                "cancel-" + UUID.randomUUID()
        );

        String paymentId =
                given()
                        .contentType("application/json")
                        .header(
                                "X-Correlation-ID",
                                correlationId.toString()
                        )
                        .body(requestBody)
                        .when()
                        .post("/api/v1/payments")
                        .then()
                        .statusCode(202)
                        .body(
                                "status",
                                equalTo("PENDING")
                        )
                        .extract()
                        .path("paymentId");

        given()
                .pathParam(
                        "paymentId",
                        paymentId
                )
                .when()
                .post(
                        "/api/v1/payments/{paymentId}/cancel"
                )
                .then()
                .statusCode(200)
                .body(
                        "paymentId",
                        equalTo(paymentId)
                )
                .body(
                        "status",
                        equalTo("CANCELLED")
                )
                .body(
                        "version",
                        equalTo(1)
                );

        Map<String, Object> persistedPayment =
                jdbcTemplate.queryForMap(
                        """
                        SELECT status, version
                        FROM payments
                        WHERE payment_id = ?
                        """,
                        UUID.fromString(paymentId)
                );

        assertThat(
                persistedPayment.get("status")
        ).isEqualTo("CANCELLED");

        assertThat(
                ((Number) persistedPayment.get("version"))
                        .longValue()
        ).isEqualTo(1L);
    }

    @Test
    void returnsNotFoundWhenCancellingMissingPayment() {
        UUID missingPaymentId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();

        given()
                .header(
                        "X-Correlation-ID",
                        correlationId.toString()
                )
                .pathParam(
                        "paymentId",
                        missingPaymentId
                )
                .when()
                .post(
                        "/api/v1/payments/{paymentId}/cancel"
                )
                .then()
                .statusCode(404)
                .body(
                        "code",
                        equalTo("PAYMENT_NOT_FOUND")
                )
                .body(
                        "message",
                        equalTo(
                                "Payment not found: "
                                        + missingPaymentId
                        )
                )
                .body(
                        "correlationId",
                        equalTo(correlationId.toString())
                );
    }

    @Test
    void returnsConflictWhenPaymentCannotBeCancelled() {
        UUID accountId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();

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
                VALUES (
                    ?, ?, ?, ?, ?, ?,
                    CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP,
                    ?, ?
                )
                """,
                paymentId,
                accountId,
                new BigDecimal("50.0000"),
                "USD",
                PaymentStatus.PROCESSING.name(),
                "processing-" + UUID.randomUUID(),
                2L,
                correlationId
        );

        given()
                .header(
                        "X-Correlation-ID",
                        correlationId.toString()
                )
                .pathParam(
                        "paymentId",
                        paymentId
                )
                .when()
                .post(
                        "/api/v1/payments/{paymentId}/cancel"
                )
                .then()
                .statusCode(409)
                .body(
                        "code",
                        equalTo(
                                "INVALID_PAYMENT_STATE_TRANSITION"
                        )
                )
                .body(
                        "message",
                        equalTo(
                                "Payment state transition is not allowed: "
                                        + "PROCESSING -> CANCELLED"
                        )
                )
                .body(
                        "correlationId",
                        equalTo(correlationId.toString())
                );

        Map<String, Object> persistedPayment =
                jdbcTemplate.queryForMap(
                        """
                        SELECT status, version
                        FROM payments
                        WHERE payment_id = ?
                        """,
                        paymentId
                );

        assertThat(
                persistedPayment.get("status")
        ).isEqualTo("PROCESSING");

        assertThat(
                ((Number) persistedPayment.get("version"))
                        .longValue()
        ).isEqualTo(2L);
    }
}
