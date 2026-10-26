package com.saas.billing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Valor monetário imutável. As operações são exatas (BigDecimal);
 * o arredondamento para centavos acontece só na saída (rounded/toString).
 */
public record Money(BigDecimal amount) {

    public static final Money ZERO = new Money(BigDecimal.ZERO);

    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    public Money {
        Objects.requireNonNull(amount, "amount");
    }

    public static Money of(String amount) {
        return new Money(new BigDecimal(amount));
    }

    public static Money of(double amount) {
        return new Money(BigDecimal.valueOf(amount));
    }

    public Money plus(Money other) {
        return new Money(amount.add(other.amount));
    }

    public Money minus(Money other) {
        return new Money(amount.subtract(other.amount));
    }

    public Money times(BigDecimal factor) {
        return new Money(amount.multiply(factor));
    }

    public Money times(int factor) {
        return times(BigDecimal.valueOf(factor));
    }

    public Money percent(int percent) {
        return times(BigDecimal.valueOf(percent).divide(ONE_HUNDRED));
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    public Money atLeastZero() {
        return isNegative() ? ZERO : this;
    }

    public BigDecimal rounded() {
        return amount.setScale(SCALE, ROUNDING);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Money other && amount.compareTo(other.amount) == 0;
    }

    @Override
    public int hashCode() {
        return amount.stripTrailingZeros().hashCode();
    }

    @Override
    public String toString() {
        return rounded().toPlainString();
    }
}
