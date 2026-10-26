package com.saas.billing.port;

import com.saas.billing.Money;

public interface PaymentGateway {

    /** @return true se a cobrança foi aprovada */
    boolean charge(String cardToken, Money amount);
}
