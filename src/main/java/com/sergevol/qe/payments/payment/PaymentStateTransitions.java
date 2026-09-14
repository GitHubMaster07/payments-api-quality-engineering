package com.sergevol.qe.payments.payment;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class PaymentStateTransitions {

    private static final Map<PaymentStatus, Set<PaymentStatus>> ALLOWED_TRANSITIONS =
            Map.of(
                    PaymentStatus.PENDING,
                    EnumSet.of(
                            PaymentStatus.APPROVED,
                            PaymentStatus.DECLINED,
                            PaymentStatus.CANCELLED
                    ),

                    PaymentStatus.APPROVED,
                    EnumSet.of(
                            PaymentStatus.PROCESSING,
                            PaymentStatus.CANCELLED
                    ),

                    PaymentStatus.PROCESSING,
                    EnumSet.of(
                            PaymentStatus.COMPLETED,
                            PaymentStatus.FAILED
                    )
            );

    private PaymentStateTransitions() {
    }

    public static boolean canTransition(
            PaymentStatus currentStatus,
            PaymentStatus targetStatus
    ) {
        Set<PaymentStatus> allowedTargets =
                ALLOWED_TRANSITIONS.getOrDefault(currentStatus, Set.of());

        return allowedTargets.contains(targetStatus);
    }
}
