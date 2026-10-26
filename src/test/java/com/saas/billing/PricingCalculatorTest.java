package com.saas.billing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PricingCalculatorTest {

    private final PricingCalculator calculator = new PricingCalculator();

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', nullValues = "null", textBlock = """
            # cenário                          | hoje       | base   | assinatura | atraso | cupom       | ONG   | esperado
            sem desconto                       | 2026-10-15 | 100.00 | 2026-09-15 | 0      | null        | false | 100.00
            12 meses ainda sem fidelidade      | 2026-10-15 | 100.00 | 2025-10-15 | 0      | null        | false | 100.00
            13 meses: 10% fidelidade           | 2026-10-15 | 100.00 | 2025-09-15 | 0      | null        | false | 90.00
            24 meses: ainda 10%                | 2026-10-15 | 100.00 | 2024-10-15 | 0      | null        | false | 90.00
            25 meses: 15% fidelidade           | 2026-10-15 | 100.00 | 2024-09-15 | 0      | null        | false | 85.00
            ONG: 30% off                       | 2026-10-15 | 100.00 | 2026-09-15 | 0      | null        | true  | 70.00
            ONG sobre fidelidade               | 2026-10-15 | 100.00 | 2024-09-15 | 0      | null        | true  | 59.50
            BLACKFRIDAY em novembro            | 2026-11-15 | 100.00 | 2026-09-15 | 0      | BLACKFRIDAY | false | 70.00
            BLACKFRIDAY fora de novembro       | 2026-10-15 | 100.00 | 2026-09-15 | 0      | BLACKFRIDAY | false | 100.00
            WELCOME10 no 1º mês, sem caixa     | 2026-10-15 | 100.00 | 2026-09-15 | 0      | welcome10   | false | 90.00
            WELCOME10 após 1 mês               | 2026-10-15 | 100.00 | 2026-08-15 | 0      | WELCOME10   | false | 100.00
            cupom em branco                    | 2026-10-15 | 100.00 | 2026-09-15 | 0      | '  '        | false | 100.00
            cupom desconhecido                 | 2026-10-15 | 100.00 | 2026-09-15 | 0      | XPTO        | false | 100.00
            atraso: 2% multa + 0,1% ao dia     | 2026-10-15 | 100.00 | 2026-09-15 | 10     | null        | false | 103.00
            atraso negativo é ignorado         | 2026-10-15 | 100.00 | 2026-09-15 | -3     | null        | false | 100.00
            piso em zero                       | 2026-11-15 | 20.00  | 2026-09-15 | 0      | BLACKFRIDAY | false | 0.00
            combinação completa                | 2026-11-15 | 100.00 | 2024-09-15 | 5      | BLACKFRIDAY | true  | 30.24
            """)
    void calcula(String cenario, String today, String basePrice, String signupDate,
                                 int overdueDays, String promoCode, boolean nonProfit, String expected) {
        BillingCommand command = new BillingCommand("cus_1", Money.of(basePrice), LocalDate.parse(signupDate),
                overdueDays, promoCode, nonProfit, "tok_visa");

        Money price = calculator.calculate(command, LocalDate.parse(today));

        assertEquals(new BigDecimal(expected), price.rounded());
    }

    @Test
    void mesesIgnoramODia() {
        assertEquals(1, PricingCalculator.monthsBetween(LocalDate.parse("2026-01-31"), LocalDate.parse("2026-02-01")));
        assertEquals(0, PricingCalculator.monthsBetween(LocalDate.parse("2026-02-01"), LocalDate.parse("2026-02-28")));
        assertEquals(13, PricingCalculator.monthsBetween(LocalDate.parse("2025-09-30"), LocalDate.parse("2026-10-01")));
    }
}
