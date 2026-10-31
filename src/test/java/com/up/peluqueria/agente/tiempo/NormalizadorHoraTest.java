package com.up.peluqueria.agente.tiempo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class NormalizadorHoraTest {

    @ParameterizedTest(name = "\"{0}\" → {1}")
    @DisplayName("normalizar: con un formato válido devuelve la hora interpretada")
    @CsvSource({
            "10,      10:00",
            "10:30,   10:30",
            "10.30,   10:30",
            "10hs,    10:00",
            "10 hs,   10:00",
            "mediodía, 12:00",
            "MEDIODIA, 12:00",
            "17:30,   17:30",
            "12,      12:00"
    })
    void normalizar_formatoValido_devuelveLaHora(String texto, String esperada) {
        // Arrange
        LocalTime horaEsperada = LocalTime.parse(esperada);

        // Act
        Optional<LocalTime> resultado = NormalizadorHora.normalizar(texto);

        // Assert
        assertThat(resultado).contains(horaEsperada);
    }

    @ParameterizedTest(name = "\"{0}\" → {1}")
    @DisplayName("normalizar: las horas de 1 a 8 se interpretan de la tarde y la 9 queda igual")
    @CsvSource({
            "1, 13:00",
            "2, 14:00",
            "3, 15:00",
            "4, 16:00",
            "5, 17:00",
            "6, 18:00",
            "7, 19:00",
            "8, 20:00",
            "9, 09:00",
            "3:30, 15:30"
    })
    void normalizar_horaDeLaTarde_sumaDoceHorasSoloDeUnoAOcho(String texto, String esperada) {
        // Arrange
        LocalTime horaEsperada = LocalTime.parse(esperada);

        // Act
        Optional<LocalTime> resultado = NormalizadorHora.normalizar(texto);

        // Assert
        assertThat(resultado).contains(horaEsperada);
    }

    @ParameterizedTest(name = "\"{0}\" → vacío")
    @DisplayName("normalizar: con una entrada que no es una hora válida devuelve vacío")
    @CsvSource({
            "25",
            "24",
            "10:75",
            "abc",
            "10:3",
            "'10:30:15'",
            "diez"
    })
    void normalizar_entradaInvalida_devuelveVacio(String texto) {
        // Arrange (el texto llega por parámetro)

        // Act
        Optional<LocalTime> resultado = NormalizadorHora.normalizar(texto);

        // Assert
        assertThat(resultado).isEmpty();
    }

    @ParameterizedTest(name = "[{index}] null o vacío → vacío")
    @DisplayName("normalizar: con null o cadena vacía devuelve vacío")
    @NullAndEmptySource
    void normalizar_nullOVacio_devuelveVacio(String texto) {
        // Arrange (el texto llega por parámetro)

        // Act
        Optional<LocalTime> resultado = NormalizadorHora.normalizar(texto);

        // Assert
        assertThat(resultado).isEmpty();
    }

    @Test
    @DisplayName("normalizar: con solo espacios devuelve vacío")
    void normalizar_soloEspacios_devuelveVacio() {
        // Arrange
        String texto = "   ";

        // Act
        Optional<LocalTime> resultado = NormalizadorHora.normalizar(texto);

        // Assert
        assertThat(resultado).isEmpty();
    }
}
