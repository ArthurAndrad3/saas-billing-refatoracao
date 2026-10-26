package com.saas.billing.adapter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.saas.billing.Money;
import org.junit.jupiter.api.Test;

class TokenPrefixPaymentGatewayTest {

    private final TokenPrefixPaymentGateway gateway = new TokenPrefixPaymentGateway();

    @Test
    void aprovaTokenComPrefixo() {
        assertTrue(gateway.charge("tok_visa", Money.of("10")));
    }

    @Test
    void recusaTokenInvalidoOuNulo() {
        assertFalse(gateway.charge("card_123", Money.of("10")));
        assertFalse(gateway.charge(null, Money.of("10")));
    }
}
