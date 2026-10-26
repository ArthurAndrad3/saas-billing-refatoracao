package com.saas.billing.fake;

import com.saas.billing.InvoiceStatus;
import com.saas.billing.Money;
import com.saas.billing.port.InvoiceRepository;
import java.util.ArrayList;
import java.util.List;

public class InMemoryInvoiceRepository implements InvoiceRepository {

    public record Invoice(String customerId, Money amount, InvoiceStatus status) {
    }

    public final List<Invoice> saved = new ArrayList<>();

    @Override
    public void save(String customerId, Money amount, InvoiceStatus status) {
        saved.add(new Invoice(customerId, amount, status));
    }
}
