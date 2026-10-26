package com.saas.billing.adapter;

import com.saas.billing.Customer;
import com.saas.billing.port.CustomerRepository;
import com.saas.billing.port.InfrastructureException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import javax.sql.DataSource;

public class JdbcCustomerRepository implements CustomerRepository {

    private static final String SQL = "SELECT email, status FROM customers WHERE id = ?";

    private final DataSource dataSource;

    public JdbcCustomerRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<Customer> findById(String customerId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL)) {
            stmt.setString(1, customerId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new Customer(customerId, rs.getString("email"), rs.getString("status")));
            }
        } catch (SQLException e) {
            throw new InfrastructureException("Falha ao consultar cliente " + customerId, e);
        }
    }
}
