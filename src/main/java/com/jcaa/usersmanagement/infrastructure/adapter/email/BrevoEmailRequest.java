package com.jcaa.usersmanagement.infrastructure.adapter.email;

import java.util.List;
import java.util.Map;

record BrevoEmailRequest(
        Contact sender,
        List<Contact> to,
        String subject,
        String htmlContent,
        Map<String, String> headers) {

    record Contact(String email, String name) {
    }
}
