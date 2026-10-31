package com.up.peluqueria.agente.conversacion;

import com.up.peluqueria.agente.skill.interpretar.Intencion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RespuestasRapidasTest {

    @ParameterizedTest(name = "\"{0}\" → CONFIRMAR")
    @DisplayName("detectar: con una afirmación conocida devuelve CONFIRMAR")
    @CsvSource({
            "sí",
            "Dale",
            "'si, confirmo.'",
            "SI",
            "OK",
            "  perfecto  ",
            "'¡Dale!'",
            "de acuerdo"
    })
    void detectar_afirmacion_devuelveConfirmar(String mensaje) {
        // Arrange (el mensaje llega por parámetro)

        // Act
        Optional<Intencion> resultado = RespuestasRapidas.detectar(mensaje);

        // Assert
        assertThat(resultado).contains(Intencion.CONFIRMAR);
    }

    @ParameterizedTest(name = "\"{0}\" → CANCELAR")
    @DisplayName("detectar: con una negación conocida devuelve CANCELAR")
    @CsvSource({
            "no",
            "No",
            "mejor no",
            "'No, gracias.'",
            "cancelar",
            "'dejalo'",
            "NO QUIERO"
    })
    void detectar_negacion_devuelveCancelar(String mensaje) {
        // Arrange (el mensaje llega por parámetro)

        // Act
        Optional<Intencion> resultado = RespuestasRapidas.detectar(mensaje);

        // Assert
        assertThat(resultado).contains(Intencion.CANCELAR);
    }

    @ParameterizedTest(name = "\"{0}\" → vacío")
    @DisplayName("detectar: con frases que no son una respuesta corta conocida devuelve vacío")
    @CsvSource({
            "'sí, pero a las 11'",
            "quiero un turno",
            "'no sé'",
            "'dale, pero con Tobias'",
            "'hola'",
            "'ok, cambiá el horario'"
    })
    void detectar_fraseNoReconocida_devuelveVacio(String mensaje) {
        // Arrange (el mensaje llega por parámetro)

        // Act
        Optional<Intencion> resultado = RespuestasRapidas.detectar(mensaje);

        // Assert
        assertThat(resultado).isEmpty();
    }

    @ParameterizedTest(name = "[{index}] null o vacío → vacío")
    @DisplayName("detectar: con null o cadena vacía devuelve vacío")
    @NullAndEmptySource
    void detectar_nullOVacio_devuelveVacio(String mensaje) {
        // Arrange (el mensaje llega por parámetro)

        // Act
        Optional<Intencion> resultado = RespuestasRapidas.detectar(mensaje);

        // Assert
        assertThat(resultado).isEmpty();
    }

    @Test
    @DisplayName("detectar: con solo signos de puntuación devuelve vacío")
    void detectar_soloPuntuacion_devuelveVacio() {
        // Arrange
        String mensaje = "...";

        // Act
        Optional<Intencion> resultado = RespuestasRapidas.detectar(mensaje);

        // Assert
        assertThat(resultado).isEmpty();
    }
}
