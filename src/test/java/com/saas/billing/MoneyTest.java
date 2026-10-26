package com.saas.billing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void operacoesSaoExatas() {
        // Com double, 0.1 + 0.2 = 0.30000000000000004
        assertEquals(Money.of("0.30"), Money.of(0.1).plus(Money.of(0.2)));
    }

    @Test
    void operacoesNaoAlteramOriginal() {
        Money original = Money.of("100.00");
        Money resultado = original.minus(Money.of("10"));

        assertNotSame(original, resultado);
        assertEquals(Money.of("100.00"), original);
        assertEquals(Money.of("90.00"), resultado);
    }

    @Test
    void percentual() {
        assertEquals(Money.of("15.00"), Money.of("100").percent(15));
    }

    @Test
    void multiplicacao() {
        assertEquals(Money.of("70.00"), Money.of("100").times(new BigDecimal("0.70")));
        assertEquals(Money.of("300.00"), Money.of("100").times(3));
    }

    @Test
    void arredondaParaCentavosSoNaSaida() {
        Money valor = Money.of("76.4999");

        assertEquals(new BigDecimal("76.50"), valor.rounded());
        assertEquals("76.50", valor.toString());
        assertEquals("10.00", Money.of("10").toString());
    }

    @Test
    void arredondamentoBancario() {
        assertEquals("0.12", Money.of("0.125").toString());
        assertEquals("0.14", Money.of("0.135").toString());
    }

    @Test
    void pisoEmZero() {
        Money negativo = Money.of("-5.00");

        assertTrue(negativo.isNegative());
        assertSame(Money.ZERO, negativo.atLeastZero());
        assertEquals(Money.of("5"), Money.of("5").atLeastZero());
    }

    @Test
    void igualdadeIgnoraEscala() {
        assertEquals(Money.of("10"), Money.of("10.00"));
        assertEquals(Money.of("10").hashCode(), Money.of("10.00").hashCode());
    }

    @Test
    void naoAceitaNulo() {
        assertThrows(NullPointerException.class, () -> new Money(null));
    }
}
