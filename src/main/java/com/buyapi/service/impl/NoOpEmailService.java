package com.buyapi.service.impl;

import com.buyapi.entity.Order;
import com.buyapi.service.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@ConditionalOnProperty(name = "app.mail.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpEmailService implements EmailService {

    @Override
    public void sendOrderConfirmation(Order order) {
        log.debug("Mail not configured — skipping order confirmation for order #{}", order.getId());
    }
}
