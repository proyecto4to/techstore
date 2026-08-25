package com.techstore.actualizacion;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/app/version")
@RequiredArgsConstructor
public class MobileVersionController {

    private final MobileUpdateProperties properties;

    @GetMapping
    @Operation(summary = "Política de compatibilidad de la aplicación Mobile")
    public MobileVersionResponse version(
            @RequestParam @Pattern(regexp = "android") String platform,
            @RequestParam @Min(1) int versionCode) {
        return new MobileVersionResponse(
                platform,
                versionCode,
                properties.minimumVersionCode(),
                properties.latestVersionCode(),
                versionCode < properties.latestVersionCode(),
                versionCode < properties.minimumVersionCode(),
                "GOOGLE_PLAY",
                properties.playStoreUrl());
    }
}
