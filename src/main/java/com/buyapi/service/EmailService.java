package com.buyapi.service;

import com.buyapi.entity.Order;

public interface EmailService {
    void sendOrderConfirmation(Order order);
}
