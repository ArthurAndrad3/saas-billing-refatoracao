package com.saas.billing.port;

import com.saas.billing.Customer;
import java.util.Optional;

public interface CustomerRepository {

    Optional<Customer> findById(String customerId);
}
