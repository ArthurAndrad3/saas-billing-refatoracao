package com.saas.billing.fake;

import com.saas.billing.Money;
import com.saas.billing.port.PaymentGateway;
import java.util.ArrayList;
import java.util.List;

public class FakePaymentGateway implements PaymentGateway {

    public final List<Money> charges = new ArrayList<>();
    private boolean approve = true;

    public FakePaymentGateway declining() {
        approve = false;
        return this;
    }

    @Override
    public boolean charge(String cardToken, Money amount) {
        charges.add(amount);
        return approve;
    }
}
