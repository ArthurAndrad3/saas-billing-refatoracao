package com.saas.billing;

public record Customer(String id, String email, String status) {

    public boolean isActive() {
        return "ACTIVE".equalsIgnoreCase(status);
    }
}
