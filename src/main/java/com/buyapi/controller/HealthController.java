package com.buyapi.controller;

import java.time.Instant;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Simple public health endpoint.
 *
 * Returns a lightweight status response without exposing full actuator details.
 * Comprehensive health checks are available via Spring Boot Actuator.
 */
@Tag(name = "Health", description = "Keep-alive endpoint (see /actuator/health for full details)")
@RestController
@RequestMapping("/api/health")
public class HealthController {

    @Operation(summary = "Get health status")
    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "timestamp", Instant.now().toString()
        ));
    }
}