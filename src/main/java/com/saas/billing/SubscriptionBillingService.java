package com.saas.billing;

import com.saas.billing.port.CustomerRepository;
import com.saas.billing.port.InvoiceRepository;
import com.saas.billing.port.NotificationService;
import com.saas.billing.port.PaymentGateway;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public class SubscriptionBillingService {

    private final CustomerRepository customers;
    private final InvoiceRepository invoices;
    private final PaymentGateway paymentGateway;
    private final NotificationService notifications;
    private final PricingCalculator pricing;
    private final Clock clock;

    public SubscriptionBillingService(CustomerRepository customers, InvoiceRepository invoices,
                                      PaymentGateway paymentGateway, NotificationService notifications,
                                      PricingCalculator pricing, Clock clock) {
        this.customers = customers;
        this.invoices = invoices;
        this.paymentGateway = paymentGateway;
        this.notifications = notifications;
        this.pricing = pricing;
        this.clock = clock;
    }

    public List<String> processMonthlyBilling(BillingCommand command) {
        Money amount = pricing.calculate(command, LocalDate.now(clock));
        Optional<Customer> customer = customers.findById(command.customerId());

        if (customer.isEmpty()) {
            return List.of();
        }
        if (!customer.get().isActive()) {
            return List.of("Cliente inativo. Cobrança cancelada: " + command.customerId());
        }
        return charge(customer.get(), command.paymentCardToken(), amount);
    }

    private List<String> charge(Customer customer, String cardToken, Money amount) {
        boolean paid = paymentGateway.charge(cardToken, amount);
        invoices.save(customer.id(), amount, paid ? InvoiceStatus.PAID : InvoiceStatus.FAILED);
        notifications.sendInvoice(customer.email(), amount);

        String paymentLog = paid
                ? "Pagamento processado com sucesso: R$ " + amount
                : "Falha no pagamento do cartão para: " + customer.id();
        return List.of(paymentLog, "E-mail disparado para: " + customer.email());
    }
}
