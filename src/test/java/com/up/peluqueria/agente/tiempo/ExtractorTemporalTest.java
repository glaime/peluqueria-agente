package com.up.peluqueria.agente.tiempo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ExtractorTemporalTest {

    // = viernes 04/01/2030 10:00 en Buenos Aires.
    private final Clock reloj = Clock.fixed(Instant.parse("2030-01-04T13:00:00Z"),
            ZoneId.of("America/Argentina/Buenos_Aires"));
    private final LocalDate hoy = LocalDate.now(reloj);

    @ParameterizedTest(name = "\"{0}\" → {1}")
    @DisplayName("fechasMencionadas: con una referencia reconocible devuelve esa única fecha")
    @CsvSource(delimiter = '|', value = {
            "hoy                         | 2030-01-04",
            "quiero turno hoy            | 2030-01-04",
            "mañana                      | 2030-01-05",
            "¿Mañana hay lugar?          | 2030-01-05",
            "pasado mañana               | 2030-01-06",
            "el sábado                   | 2030-01-05",
            "el SÁBADO                   | 2030-01-05",
            "el viernes                  | 2030-01-04",
            "el martes                   | 2030-01-08",
            "el miércoles                | 2030-01-09",
            "el lunes                    | 2030-01-07",
            "el domingo                  | 2030-01-06",
            "15/01                       | 2030-01-15",
            "02/01                       | 2031-01-02",
            "04/01                       | 2030-01-04",
            "15/01/2030                  | 2030-01-15",
            "15/01/30                    | 2030-01-15",
            "el sábado a la mañana       | 2030-01-05",
            "el sábado por la mañana     | 2030-01-05",
            "el sábado de la mañana      | 2030-01-05"
    })
    void fechasMencionadas_referenciaUnica_devuelveEsaFecha(String mensaje, String esperada) {
        // Arrange
        LocalDate fechaEsperada = LocalDate.parse(esperada);

        // Act
        Set<LocalDate> fechas = ExtractorTemporal.fechasMencionadas(mensaje, hoy);

        // Assert
        assertThat(fechas).containsExactly(fechaEsperada);
    }

    @ParameterizedTest(name = "\"{0}\" → ignorada")
    @DisplayName("fechasMencionadas: con una fecha inexistente o sin fecha devuelve vacío")
    @CsvSource({
            "31/02",
            "32/01",
            "15/13",
            "quiero un turno",
            "a la mañana",
            "por la mañana"
    })
    void fechasMencionadas_fechaInvalidaOSinFecha_devuelveVacio(String mensaje) {
        // Arrange (el mensaje llega por parámetro)

        // Act
        Set<LocalDate> fechas = ExtractorTemporal.fechasMencionadas(mensaje, hoy);

        // Assert
        assertThat(fechas).isEmpty();
    }

    @ParameterizedTest(name = "null o vacío → vacío")
    @DisplayName("fechasMencionadas: con null o cadena vacía devuelve vacío")
    @NullAndEmptySource
    void fechasMencionadas_nullOVacio_devuelveVacio(String mensaje) {
        // Arrange (el mensaje llega por parámetro)

        // Act
        Set<LocalDate> fechas = ExtractorTemporal.fechasMencionadas(mensaje, hoy);

        // Assert
        assertThat(fechas).isEmpty();
    }

    @Test
    @DisplayName("fechasMencionadas: con dos fechas en el mensaje devuelve las dos")
    void fechasMencionadas_dosFechas_devuelveAmbas() {
        // Arrange
        String mensaje = "el sábado o el martes";

        // Act
        Set<LocalDate> fechas = ExtractorTemporal.fechasMencionadas(mensaje, hoy);

        // Assert
        assertThat(fechas).containsExactlyInAnyOrder(LocalDate.parse("2030-01-05"), LocalDate.parse("2030-01-08"));
    }

    @Test
    @DisplayName("fechasMencionadas: 'hoy y mañana' devuelve las dos fechas")
    void fechasMencionadas_hoyYManana_devuelveAmbas() {
        // Arrange
        String mensaje = "hoy o mañana";

        // Act
        Set<LocalDate> fechas = ExtractorTemporal.fechasMencionadas(mensaje, hoy);

        // Assert
        assertThat(fechas).containsExactlyInAnyOrder(LocalDate.parse("2030-01-04"), LocalDate.parse("2030-01-05"));
    }

    @Test
    @DisplayName("fechasMencionadas: 'pasado mañana' no cuenta además como 'mañana'")
    void fechasMencionadas_pasadoManana_noIncluyeManana() {
        // Arrange
        String mensaje = "pasado mañana a las 10";

        // Act
        Set<LocalDate> fechas = ExtractorTemporal.fechasMencionadas(mensaje, hoy);

        // Assert
        assertThat(fechas).containsExactly(LocalDate.parse("2030-01-06"));
    }

    @Test
    @DisplayName("fechasMencionadas: 'mañana' y 'a la mañana' juntos dan solo el día siguiente")
    void fechasMencionadas_mananaYMomentoDelDia_devuelveSoloElDiaSiguiente() {
        // Arrange
        String mensaje = "mañana a la mañana";

        // Act
        Set<LocalDate> fechas = ExtractorTemporal.fechasMencionadas(mensaje, hoy);

        // Assert
        assertThat(fechas).containsExactly(LocalDate.parse("2030-01-05"));
    }

    @Test
    @DisplayName("fechasMencionadas: una fecha numérica válida y otra inválida devuelve solo la válida")
    void fechasMencionadas_validaEInvalida_ignoraLaInvalida() {
        // Arrange
        String mensaje = "el 31/02 o el 15/01";

        // Act
        Set<LocalDate> fechas = ExtractorTemporal.fechasMencionadas(mensaje, hoy);

        // Assert
        assertThat(fechas).containsExactly(LocalDate.parse("2030-01-15"));
    }

    @ParameterizedTest(name = "\"{0}\" → menciona fecha")
    @DisplayName("mencionaFecha: con una referencia a una fecha devuelve true")
    @CsvSource({
            "hoy",
            "mañana",
            "el sábado",
            "15/01",
            "el 7 de noviembre",
            "la semana que viene",
            "el finde",
            "el próximo martes",
            "en octubre"
    })
    void mencionaFecha_conReferencia_devuelveTrue(String mensaje) {
        // Arrange (el mensaje llega por parámetro)

        // Act
        boolean resultado = ExtractorTemporal.mencionaFecha(mensaje, hoy);

        // Assert
        assertThat(resultado).isTrue();
    }

    @ParameterizedTest(name = "\"{0}\" → no menciona fecha")
    @DisplayName("mencionaFecha: sin referencia a una fecha devuelve false")
    @CsvSource({
            "hola",
            "quiero un turno",
            "a las 10",
            "con Tobias",
            "a la mañana"
    })
    void mencionaFecha_sinReferencia_devuelveFalse(String mensaje) {
        // Arrange (el mensaje llega por parámetro)

        // Act
        boolean resultado = ExtractorTemporal.mencionaFecha(mensaje, hoy);

        // Assert
        assertThat(resultado).isFalse();
    }

    @ParameterizedTest(name = "\"{0}\" → menciona hora")
    @DisplayName("mencionaHora: con una hora en números o en palabras devuelve true")
    @CsvSource({
            "a las 10",
            "10:30",
            "10hs",
            "a las diez",
            "al mediodía",
            "a las cuatro de la tarde",
            "el sábado a las 10"
    })
    void mencionaHora_conHora_devuelveTrue(String mensaje) {
        // Arrange (el mensaje llega por parámetro)

        // Act
        boolean resultado = ExtractorTemporal.mencionaHora(mensaje);

        // Assert
        assertThat(resultado).isTrue();
    }

    @ParameterizedTest(name = "\"{0}\" → no menciona hora")
    @DisplayName("mencionaHora: sin hora, o con solo una fecha numérica o un día del mes, devuelve false")
    @CsvSource({
            "hola",
            "quiero un turno",
            "el sábado",
            "con Tobias",
            "el 15/01",
            "el 7 de noviembre"
    })
    void mencionaHora_sinHora_devuelveFalse(String mensaje) {
        // Arrange (el mensaje llega por parámetro)

        // Act
        boolean resultado = ExtractorTemporal.mencionaHora(mensaje);

        // Assert
        assertThat(resultado).isFalse();
    }

    @ParameterizedTest(name = "null o vacío → false")
    @DisplayName("mencionaFecha y mencionaHora: con null o vacío devuelven false")
    @NullAndEmptySource
    void mencionaFechaYHora_nullOVacio_devuelvenFalse(String mensaje) {
        // Arrange (el mensaje llega por parámetro)

        // Act
        boolean fecha = ExtractorTemporal.mencionaFecha(mensaje, hoy);
        boolean hora = ExtractorTemporal.mencionaHora(mensaje);

        // Assert
        assertThat(fecha).isFalse();
        assertThat(hora).isFalse();
    }
}
