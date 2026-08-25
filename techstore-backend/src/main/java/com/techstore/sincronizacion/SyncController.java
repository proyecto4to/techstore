package com.techstore.sincronizacion;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/sync")
@SecurityRequirement(name = "bearerAuth")
public class SyncController {
    private final DomainEventStore store;

    @GetMapping("/events")
    public List<DomainEventResponse> events(
            @RequestParam(defaultValue = "0") @Min(0) long after,
            @RequestParam(defaultValue = "100") @Min(1) @Max(200) int limit) {
        return store.after(after, limit);
    }
}
