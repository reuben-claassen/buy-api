package com.buyapi.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Simple keep-alive endpoint used by the GitHub Actions ping.
 *
 * Real health checks are handled by Spring Boot Actuator.
 * This exists only to provide a stable public URL without exposing full actuator details.
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
