package com.techstore.catalogo;

import java.time.LocalDateTime;

public record FavoritoResponse(
        ProductoPublicResponse producto,
        LocalDateTime creadoEn) {
}
