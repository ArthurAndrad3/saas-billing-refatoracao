package com.saas.billing.adapter;

import com.saas.billing.Money;
import com.saas.billing.port.PaymentGateway;

/** Regra de aprovação herdada do legado: token de cartão válido começa com "tok_". */
public class TokenPrefixPaymentGateway implements PaymentGateway {

    @Override
    public boolean charge(String cardToken, Money amount) {
        return cardToken != null && cardToken.startsWith("tok_");
    }
}
