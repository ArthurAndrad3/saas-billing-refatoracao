package com.saas.billing;

import com.saas.billing.port.CustomerRepository;
import com.saas.billing.port.InvoiceRepository;
import com.saas.billing.port.NotificationService;
import com.saas.billing.port.PaymentGateway;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SubscriptionBillingService {

    private static final BigDecimal NON_PROFIT_FACTOR = new BigDecimal("0.70");
    private static final BigDecimal WELCOME10_FACTOR = new BigDecimal("0.90");
    private static final Money BLACKFRIDAY_DISCOUNT = Money.of("30.00");
    private static final BigDecimal LATE_PENALTY_RATE = new BigDecimal("0.02");
    private static final BigDecimal DAILY_INTEREST_RATE = new BigDecimal("0.001");

    private final CustomerRepository customers;
    private final InvoiceRepository invoices;
    private final PaymentGateway paymentGateway;
    private final NotificationService notifications;
    private final Clock clock;

    public SubscriptionBillingService(CustomerRepository customers, InvoiceRepository invoices,
                                      PaymentGateway paymentGateway, NotificationService notifications,
                                      Clock clock) {
        this.customers = customers;
        this.invoices = invoices;
        this.paymentGateway = paymentGateway;
        this.notifications = notifications;
        this.clock = clock;
    }

    public List<String> processMonthlyBilling(BillingCommand command) {

        List<String> logs = new ArrayList<>();

        LocalDate signupDate = command.signupDate();
        LocalDate today = LocalDate.now(clock);

        int monthsActive = (today.getYear() - signupDate.getYear()) * 12 + (today.getMonthValue() - signupDate.getMonthValue());

        Money basePrice = command.basePrice();
        Money calculatedPrice = basePrice;

        if (monthsActive > 24) {
            calculatedPrice = calculatedPrice.minus(basePrice.percent(15)); // 15% após 2 anos
        } else if (monthsActive > 12) {
            calculatedPrice = calculatedPrice.minus(basePrice.percent(10)); // 10% após 1 ano
        }

        if (command.nonProfit()) {
            calculatedPrice = calculatedPrice.times(NON_PROFIT_FACTOR); // 30% off para ONGs
        }

        String promoCode = command.promoCode();
        if (promoCode != null && !promoCode.trim().isEmpty()) {
            if (promoCode.equalsIgnoreCase("BLACKFRIDAY") && today.getMonthValue() == 11) {
                calculatedPrice = calculatedPrice.minus(BLACKFRIDAY_DISCOUNT);
            } else if (promoCode.equalsIgnoreCase("WELCOME10") && monthsActive <= 1) {
                calculatedPrice = calculatedPrice.times(WELCOME10_FACTOR);
            }
        }

        int overdueDays = command.overdueDays();
        if (overdueDays > 0) {
            Money penalty = calculatedPrice.times(LATE_PENALTY_RATE);
            Money dailyInterest = calculatedPrice.times(DAILY_INTEREST_RATE).times(overdueDays);
            calculatedPrice = calculatedPrice.plus(penalty).plus(dailyInterest);
        }

        calculatedPrice = calculatedPrice.atLeastZero();

        String customerId = command.customerId();
        Optional<Customer> found = customers.findById(customerId);

        if (found.isPresent()) {
            Customer customer = found.get();

            if (!customer.isActive()) {
                logs.add("Cliente inativo. Cobrança cancelada: " + customerId);
                return logs;
            }

            boolean paymentSuccess = paymentGateway.charge(command.paymentCardToken(), calculatedPrice);
            if (paymentSuccess) {
                logs.add("Pagamento processado com sucesso: R$ " + calculatedPrice);
            } else {
                logs.add("Falha no pagamento do cartão para: " + customerId);
            }

            invoices.save(customerId, calculatedPrice, paymentSuccess ? InvoiceStatus.PAID : InvoiceStatus.FAILED);

            notifications.sendInvoice(customer.email(), calculatedPrice);
            logs.add("E-mail disparado para: " + customer.email());
        }

        return logs;
    }
}
