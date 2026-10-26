package com.saas.billing.port;

import com.saas.billing.Money;

public interface NotificationService {

    void sendInvoice(String email, Money amount);
}
