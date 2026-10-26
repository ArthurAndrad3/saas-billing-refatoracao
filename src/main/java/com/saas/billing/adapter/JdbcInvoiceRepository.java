package com.saas.billing.adapter;

import com.saas.billing.InvoiceStatus;
import com.saas.billing.Money;
import com.saas.billing.port.InfrastructureException;
import com.saas.billing.port.InvoiceRepository;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import javax.sql.DataSource;

public class JdbcInvoiceRepository implements InvoiceRepository {

    private static final String SQL =
            "INSERT INTO invoices(customer_id, amount, status, created_at) VALUES (?, ?, ?, NOW())";

    private final DataSource dataSource;

    public JdbcInvoiceRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void save(String customerId, Money amount, InvoiceStatus status) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL)) {
            stmt.setString(1, customerId);
            stmt.setBigDecimal(2, amount.rounded());
            stmt.setString(3, status.name());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new InfrastructureException("Falha ao gravar fatura de " + customerId, e);
        }
    }
}
