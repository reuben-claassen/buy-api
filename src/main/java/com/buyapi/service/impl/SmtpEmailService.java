package com.buyapi.service.impl;

import com.buyapi.entity.Order;
import com.buyapi.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.mail.enabled", havingValue = "true")
public class SmtpEmailService implements EmailService {

    private final JavaMailSender mailSender;

    @Override
    @Async
    public void sendOrderConfirmation(Order order) {
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(order.getUser().getEmail());
            msg.setSubject("Order Confirmed — #" + order.getId());
            msg.setText(buildOrderBody(order));
            mailSender.send(msg);
            log.info("Sent order confirmation for order #{}", order.getId());
        } catch (Exception e) {
            log.warn("Failed to send order confirmation for order #{}: {}", order.getId(), e.getMessage());
        }
    }

    private String buildOrderBody(Order order) {
        StringBuilder sb = new StringBuilder();
        sb.append("Hi ").append(order.getUser().getFullName()).append(",\n\n");
        sb.append("Thank you for your order! Here's your summary:\n\n");
        sb.append("Order ID:  #").append(order.getId()).append("\n");
        sb.append("Status:    ").append(order.getStatus()).append("\n");
        sb.append("Ship to:   ").append(order.getShippingAddress()).append("\n\n");
        sb.append("Items:\n");
        order.getItems().forEach(item ->
            sb.append("  - ").append(item.getProduct().getName())
              .append("  x").append(item.getQuantity())
              .append("  @ $").append(item.getUnitPrice())
              .append("  = $").append(item.getSubtotal()).append("\n")
        );
        sb.append("\nTotal: $").append(order.getTotalAmount()).append("\n\n");
        sb.append("We'll notify you when your order ships.\n\nThanks,\nbuy-api");
        return sb.toString();
    }
}
