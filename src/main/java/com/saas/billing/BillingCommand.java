package com.saas.billing;

import java.time.LocalDate;
import java.util.Objects;

/** Parâmetros de entrada de uma cobrança mensal. */
public record BillingCommand(
        String customerId,
        Money basePrice,
        LocalDate signupDate,
        int overdueDays,
        String promoCode,
        boolean nonProfit,
        String paymentCardToken) {

    public BillingCommand {
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(basePrice, "basePrice");
        Objects.requireNonNull(signupDate, "signupDate");
    }
}
