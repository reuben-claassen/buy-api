package com.buyapi.dto.request;

import jakarta.validation.constraints.NotBlank;

public record OrderStatusRequest(
        @NotBlank String status
) {}