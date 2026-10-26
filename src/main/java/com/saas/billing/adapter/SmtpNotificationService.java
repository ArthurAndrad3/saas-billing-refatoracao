package com.saas.billing.adapter;

import com.saas.billing.Money;
import com.saas.billing.port.InfrastructureException;
import com.saas.billing.port.NotificationService;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.SimpleEmail;

public class SmtpNotificationService implements NotificationService {

    private final String host;
    private final int port;
    private final String from;

    public SmtpNotificationService(String host, int port, String from) {
        this.host = host;
        this.port = port;
        this.from = from;
    }

    @Override
    public void sendInvoice(String email, Money amount) {
        try {
            SimpleEmail message = new SimpleEmail();
            message.setHostName(host);
            message.setSmtpPort(port);
            message.setFrom(from);
            message.setSubject("Sua Fatura Mensal");
            message.setMsg("Olá! Sua fatura foi processada no valor de R$ " + amount);
            message.addTo(email);
            message.send();
        } catch (EmailException e) {
            throw new InfrastructureException("Falha ao enviar fatura para " + email, e);
        }
    }
}
