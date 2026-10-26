package com.saas.billing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.saas.billing.fake.FakeNotificationService;
import com.saas.billing.fake.FakePaymentGateway;
import com.saas.billing.fake.InMemoryCustomerRepository;
import com.saas.billing.fake.InMemoryInvoiceRepository;
import com.saas.billing.port.InfrastructureException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SubscriptionBillingServiceTest {

    private static final String ID = "cus_1";
    private static final String EMAIL = "cliente@exemplo.com";

    private final InMemoryCustomerRepository customers =
            new InMemoryCustomerRepository().with(new Customer(ID, EMAIL, "ACTIVE"));
    private final InMemoryInvoiceRepository invoices = new InMemoryInvoiceRepository();
    private final FakePaymentGateway gateway = new FakePaymentGateway();
    private final FakeNotificationService notifications = new FakeNotificationService();

    private SubscriptionBillingService serviceOn(String today) {
        Clock clock = Clock.fixed(LocalDate.parse(today).atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        return new SubscriptionBillingService(customers, invoices, gateway, notifications, clock);
    }

    private static BillingCommand command(String basePrice, String signupDate, int overdueDays,
                                          String promoCode, boolean nonProfit) {
        return new BillingCommand(ID, Money.of(basePrice), LocalDate.parse(signupDate),
                overdueDays, promoCode, nonProfit, "tok_visa");
    }

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
    void calculaPrecoComoOLegado(String cenario, String today, String basePrice, String signupDate,
                                 int overdueDays, String promoCode, boolean nonProfit, String expected) {
        List<String> logs = serviceOn(today)
                .processMonthlyBilling(command(basePrice, signupDate, overdueDays, promoCode, nonProfit));

        assertEquals("Pagamento processado com sucesso: R$ " + expected, logs.get(0));
        assertEquals(new BigDecimal(expected), invoices.saved.get(0).amount().rounded());
    }

    @Test
    void cobrancaAprovada() {
        List<String> logs = serviceOn("2026-10-15").processMonthlyBilling(command("100.00", "2026-09-15", 0, null, false));

        assertEquals(List.of(
                "Pagamento processado com sucesso: R$ 100.00",
                "E-mail disparado para: " + EMAIL), logs);
        assertEquals(List.of(new InMemoryInvoiceRepository.Invoice(ID, Money.of("100"), InvoiceStatus.PAID)), invoices.saved);
        assertEquals(List.of(new FakeNotificationService.Sent(EMAIL, Money.of("100"))), notifications.sent);
    }

    @Test
    void cobrancaRecusadaGravaFaturaFalhaEAindaEnviaEmail() {
        gateway.declining();

        List<String> logs = serviceOn("2026-10-15").processMonthlyBilling(command("100.00", "2026-09-15", 0, null, false));

        assertEquals(List.of(
                "Falha no pagamento do cartão para: " + ID,
                "E-mail disparado para: " + EMAIL), logs);
        assertEquals(InvoiceStatus.FAILED, invoices.saved.get(0).status());
        assertEquals(1, notifications.sent.size());
    }

    @Test
    void clienteInativoNaoECobrado() {
        customers.with(new Customer(ID, EMAIL, "SUSPENDED"));

        List<String> logs = serviceOn("2026-10-15").processMonthlyBilling(command("100.00", "2026-09-15", 0, null, false));

        assertEquals(List.of("Cliente inativo. Cobrança cancelada: " + ID), logs);
        assertTrue(gateway.charges.isEmpty());
        assertTrue(invoices.saved.isEmpty());
        assertTrue(notifications.sent.isEmpty());
    }

    @Test
    void clienteInexistenteNaoGeraNada() {
        BillingCommand desconhecido = new BillingCommand("cus_x", Money.of("100"), LocalDate.parse("2026-09-15"),
                0, null, false, "tok_visa");

        List<String> logs = serviceOn("2026-10-15").processMonthlyBilling(desconhecido);

        assertTrue(logs.isEmpty());
        assertTrue(gateway.charges.isEmpty());
        assertTrue(invoices.saved.isEmpty());
    }

    @Test
    void falhaDeInfraestruturaNaoESilenciada() {
        SubscriptionBillingService service = new SubscriptionBillingService(
                id -> { throw new InfrastructureException("banco fora", new SQLException()); },
                invoices, gateway, notifications, Clock.systemUTC());

        assertThrows(InfrastructureException.class,
                () -> service.processMonthlyBilling(command("100.00", "2026-09-15", 0, null, false)));
    }
}
