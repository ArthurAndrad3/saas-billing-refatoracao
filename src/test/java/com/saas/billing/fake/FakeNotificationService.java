package com.saas.billing.fake;

import com.saas.billing.Money;
import com.saas.billing.port.NotificationService;
import java.util.ArrayList;
import java.util.List;

public class FakeNotificationService implements NotificationService {

    public record Sent(String email, Money amount) {
    }

    public final List<Sent> sent = new ArrayList<>();

    @Override
    public void sendInvoice(String email, Money amount) {
        sent.add(new Sent(email, amount));
    }
}
