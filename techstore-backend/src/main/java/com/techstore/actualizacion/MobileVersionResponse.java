package com.techstore.actualizacion;

import java.net.URI;

public record MobileVersionResponse(
        String platform,
        int currentVersionCode,
        int minimumVersionCode,
        int latestVersionCode,
        boolean updateAvailable,
        boolean mandatory,
        String distribution,
        URI storeUrl) {
}
