package com.saas.billing.port;

import com.saas.billing.InvoiceStatus;
import com.saas.billing.Money;

public interface InvoiceRepository {

    void save(String customerId, Money amount, InvoiceStatus status);
}
