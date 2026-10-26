package com.saas.billing;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Compara o PricingCalculator com a fórmula original (transcrita do commit 1, em double)
 * em entradas aleatórias. A única diferença aceita é 1 centavo, causada pela imprecisão do double.
 */
class LegacyPricingEquivalenceTest {

    private static final String[] PROMOS = {null, "", "  ", "BLACKFRIDAY", "blackfriday", "WELCOME10", "welcome10", "XPTO"};
    private static final BigDecimal ONE_CENT = new BigDecimal("0.01");

    @Test
    void mesmoPrecoQueOLegado() {
        Random random = new Random(42);
        PricingCalculator calculator = new PricingCalculator();

        for (int i = 0; i < 100_000; i++) {
            double basePrice = random.nextInt(100_000) / 100.0;
            LocalDate today = LocalDate.of(2026, 1, 1).plusDays(random.nextInt(365));
            LocalDate signupDate = today.minusDays(random.nextInt(1200));
            int overdueDays = random.nextInt(65) - 5;
            String promoCode = PROMOS[random.nextInt(PROMOS.length)];
            boolean nonProfit = random.nextBoolean();

            BigDecimal legacy = BigDecimal.valueOf(legacyPrice(basePrice, signupDate, today, overdueDays, promoCode, nonProfit))
                    .setScale(2, RoundingMode.HALF_EVEN);
            BigDecimal current = calculator.calculate(new BillingCommand("c", Money.of(basePrice), signupDate,
                    overdueDays, promoCode, nonProfit, "tok_x"), today).rounded();

            assertTrue(legacy.subtract(current).abs().compareTo(ONE_CENT) <= 0,
                    () -> "legado=" + legacy + " atual=" + current);
        }
    }

    /** Cópia literal do cálculo de SubscriptionBillingService no commit 1. */
    private static double legacyPrice(double basePrice, LocalDate signupDate, LocalDate today,
                                      int overdueDays, String promoCode, boolean isNonProfit) {
        int monthsActive = (today.getYear() - signupDate.getYear()) * 12 + (today.getMonthValue() - signupDate.getMonthValue());
        double calculatedPrice = basePrice;
        if (monthsActive > 24) {
            calculatedPrice = calculatedPrice - (basePrice * 0.15);
        } else if (monthsActive > 12) {
            calculatedPrice = calculatedPrice - (basePrice * 0.10);
        }
        if (isNonProfit) {
            calculatedPrice = calculatedPrice * 0.70;
        }
        if (promoCode != null && !promoCode.trim().isEmpty()) {
            if (promoCode.equalsIgnoreCase("BLACKFRIDAY") && today.getMonthValue() == 11) {
                calculatedPrice = calculatedPrice - 30.0;
            } else if (promoCode.equalsIgnoreCase("WELCOME10") && monthsActive <= 1) {
                calculatedPrice = calculatedPrice * 0.90;
            }
        }
        if (overdueDays > 0) {
            double penalty = calculatedPrice * 0.02;
            double dailyInterest = (calculatedPrice * 0.001) * overdueDays;
            calculatedPrice = calculatedPrice + penalty + dailyInterest;
        }
        if (calculatedPrice < 0) {
            calculatedPrice = 0.0;
        }
        return calculatedPrice;
    }
}
