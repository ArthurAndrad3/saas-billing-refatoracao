package com.saas.billing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;

/** Regras de preço da assinatura. Classe pura: sem I/O e sem relógio próprio. */
public class PricingCalculator {

    private static final BigDecimal NON_PROFIT_FACTOR = new BigDecimal("0.70");
    private static final BigDecimal WELCOME10_FACTOR = new BigDecimal("0.90");
    private static final Money BLACKFRIDAY_DISCOUNT = Money.of("30.00");
    private static final BigDecimal LATE_PENALTY_RATE = new BigDecimal("0.02");
    private static final BigDecimal DAILY_INTEREST_RATE = new BigDecimal("0.001");

    public Money calculate(BillingCommand command, LocalDate today) {
        int monthsActive = monthsBetween(command.signupDate(), today);

        Money price = applyLoyaltyDiscount(command.basePrice(), monthsActive);
        price = applyNonProfitDiscount(price, command.nonProfit());
        price = applyPromoCode(price, command.promoCode(), today, monthsActive);
        price = applyLateFees(price, command.overdueDays());
        return price.atLeastZero();
    }

    /** Diferença em meses de calendário; o dia do mês é ignorado, como no legado. */
    static int monthsBetween(LocalDate signupDate, LocalDate today) {
        return (today.getYear() - signupDate.getYear()) * 12 + (today.getMonthValue() - signupDate.getMonthValue());
    }

    private Money applyLoyaltyDiscount(Money basePrice, int monthsActive) {
        if (monthsActive > 24) {
            return basePrice.minus(basePrice.percent(15));
        }
        if (monthsActive > 12) {
            return basePrice.minus(basePrice.percent(10));
        }
        return basePrice;
    }

    private Money applyNonProfitDiscount(Money price, boolean nonProfit) {
        return nonProfit ? price.times(NON_PROFIT_FACTOR) : price;
    }

    private Money applyPromoCode(Money price, String promoCode, LocalDate today, int monthsActive) {
        if (promoCode == null || promoCode.isBlank()) {
            return price;
        }
        if (promoCode.equalsIgnoreCase("BLACKFRIDAY") && today.getMonth() == Month.NOVEMBER) {
            return price.minus(BLACKFRIDAY_DISCOUNT);
        }
        if (promoCode.equalsIgnoreCase("WELCOME10") && monthsActive <= 1) {
            return price.times(WELCOME10_FACTOR);
        }
        return price;
    }

    private Money applyLateFees(Money price, int overdueDays) {
        if (overdueDays <= 0) {
            return price;
        }
        Money penalty = price.times(LATE_PENALTY_RATE);
        Money interest = price.times(DAILY_INTEREST_RATE).times(overdueDays);
        return price.plus(penalty).plus(interest);
    }
}
