package com.techstore.actualizacion;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class MobileVersionControllerTest {

    private final MobileVersionController controller = new MobileVersionController(
            new MobileUpdateProperties(5, 8, URI.create("https://play.google.com/store/apps/details?id=test")));

    @Test
    void obligaActualizarSoloVersionesIncompatibles() {
        var antigua = controller.version("android", 4);
        var compatible = controller.version("android", 6);
        var actual = controller.version("android", 8);

        assertThat(antigua.mandatory()).isTrue();
        assertThat(antigua.updateAvailable()).isTrue();
        assertThat(compatible.mandatory()).isFalse();
        assertThat(compatible.updateAvailable()).isTrue();
        assertThat(actual.updateAvailable()).isFalse();
        assertThat(actual.distribution()).isEqualTo("GOOGLE_PLAY");
    }
}
