package com.sergevol.qe.payments.payment;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentStateTransitionsTest {

    @Test
    void allowsTransitionsFromPending() {
        assertThat(
                PaymentStateTransitions.canTransition(
                        PaymentStatus.PENDING,
                        PaymentStatus.APPROVED
                )
        ).isTrue();

        assertThat(
                PaymentStateTransitions.canTransition(
                        PaymentStatus.PENDING,
                        PaymentStatus.DECLINED
                )
        ).isTrue();

        assertThat(
                PaymentStateTransitions.canTransition(
                        PaymentStatus.PENDING,
                        PaymentStatus.CANCELLED
                )
        ).isTrue();
    }

    @Test
    void allowsTransitionsFromApproved() {
        assertThat(
                PaymentStateTransitions.canTransition(
                        PaymentStatus.APPROVED,
                        PaymentStatus.PROCESSING
                )
        ).isTrue();

        assertThat(
                PaymentStateTransitions.canTransition(
                        PaymentStatus.APPROVED,
                        PaymentStatus.CANCELLED
                )
        ).isTrue();
    }

    @Test
    void allowsTransitionsFromProcessing() {
        assertThat(
                PaymentStateTransitions.canTransition(
                        PaymentStatus.PROCESSING,
                        PaymentStatus.COMPLETED
                )
        ).isTrue();

        assertThat(
                PaymentStateTransitions.canTransition(
                        PaymentStatus.PROCESSING,
                        PaymentStatus.FAILED
                )
        ).isTrue();
    }

    @Test
    void rejectsProhibitedProcessingCancellation() {
        assertThat(
                PaymentStateTransitions.canTransition(
                        PaymentStatus.PROCESSING,
                        PaymentStatus.CANCELLED
                )
        ).isFalse();
    }

    @Test
    void rejectsTransitionsFromTerminalStates() {
        assertThat(
                PaymentStateTransitions.canTransition(
                        PaymentStatus.DECLINED,
                        PaymentStatus.PENDING
                )
        ).isFalse();

        assertThat(
                PaymentStateTransitions.canTransition(
                        PaymentStatus.COMPLETED,
                        PaymentStatus.PROCESSING
                )
        ).isFalse();

        assertThat(
                PaymentStateTransitions.canTransition(
                        PaymentStatus.FAILED,
                        PaymentStatus.PROCESSING
                )
        ).isFalse();

        assertThat(
                PaymentStateTransitions.canTransition(
                        PaymentStatus.CANCELLED,
                        PaymentStatus.PENDING
                )
        ).isFalse();
    }

    @Test
    void rejectsSelfTransitions() {
        for (PaymentStatus status : PaymentStatus.values()) {
            assertThat(
                    PaymentStateTransitions.canTransition(status, status)
            ).isFalse();
        }
    }
}
