package com.up.peluqueria.agente.dominio;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ReglasAgendaTest {

    // = viernes 04/01/2030 10:00 en Buenos Aires.
    // sábado 2030-01-05 | domingo 06 | lunes 07 | martes 08 | miércoles 09
    private final Clock reloj = Clock.fixed(Instant.parse("2030-01-04T13:00:00Z"),
            ZoneId.of("America/Argentina/Buenos_Aires"));
    private final ReglasAgenda reglas = new ReglasAgenda(reloj);

    private static final LocalDate HOY = LocalDate.parse("2030-01-04");
    private static final LocalDate MARTES = LocalDate.parse("2030-01-08");

    @ParameterizedTest(name = "{0} atiende={1}")
    @DisplayName("atiende: de martes a sábado atiende; domingo y lunes no")
    @CsvSource({
            "2030-01-04, true",
            "2030-01-05, true",
            "2030-01-06, false",
            "2030-01-07, false",
            "2030-01-08, true",
            "2030-01-09, true"
    })
    void atiende_segunElDia_respetaLosDiasDeAtencion(String fecha, boolean esperado) {
        // Arrange
        LocalDate dia = LocalDate.parse(fecha);

        // Act
        boolean resultado = reglas.atiende(dia);

        // Assert
        assertThat(resultado).isEqualTo(esperado);
    }

    @ParameterizedTest(name = "{0} → {1}")
    @DisplayName("validarFecha: domingo y lunes incumplen DIA_SIN_ATENCION")
    @CsvSource({
            "2030-01-06, DIA_SIN_ATENCION",
            "2030-01-07, DIA_SIN_ATENCION"
    })
    void validarFecha_domingoOLunes_devuelveDiaSinAtencion(String fecha, ReglaIncumplida esperada) {
        // Arrange
        LocalDate dia = LocalDate.parse(fecha);

        // Act
        Optional<ReglaIncumplida> resultado = reglas.validarFecha(dia);

        // Assert
        assertThat(resultado).contains(esperada);
    }

    @Test
    @DisplayName("validarFecha: un día de atención no incumple ninguna regla")
    void validarFecha_diaDeAtencion_devuelveVacio() {
        // Arrange
        LocalDate dia = MARTES;

        // Act
        Optional<ReglaIncumplida> resultado = reglas.validarFecha(dia);

        // Assert
        assertThat(resultado).isEmpty();
    }

    @ParameterizedTest(name = "{0} → {1}")
    @DisplayName("validarHora: fuera de horario, minutos no permitidos u hora pasada de hoy")
    @CsvSource({
            "2030-01-08, 08:30, FUERA_DE_HORARIO",
            "2030-01-08, 08:59, FUERA_DE_HORARIO",
            "2030-01-08, 17:31, FUERA_DE_HORARIO",
            "2030-01-08, 18:00, FUERA_DE_HORARIO",
            "2030-01-08, 10:15, MINUTOS_NO_PERMITIDOS",
            "2030-01-08, 09:45, MINUTOS_NO_PERMITIDOS",
            "2030-01-04, 09:30, HORARIO_PASADO",
            "2030-01-04, 10:00, HORARIO_PASADO"
    })
    void validarHora_horaInvalida_devuelveLaReglaIncumplida(String fecha, String hora, ReglaIncumplida esperada) {
        // Arrange
        LocalDate dia = LocalDate.parse(fecha);
        LocalTime horaPedida = LocalTime.parse(hora);

        // Act
        Optional<ReglaIncumplida> resultado = reglas.validarHora(dia, horaPedida);

        // Assert
        assertThat(resultado).contains(esperada);
    }

    @ParameterizedTest(name = "{0} {1} → válida")
    @DisplayName("validarHora: los horarios de turno válidos no incumplen ninguna regla")
    @CsvSource({
            "2030-01-08, 09:00",
            "2030-01-08, 09:30",
            "2030-01-08, 17:30",
            "2030-01-04, 10:30",
            "2030-01-04, 17:30"
    })
    void validarHora_horaValida_devuelveVacio(String fecha, String hora) {
        // Arrange
        LocalDate dia = LocalDate.parse(fecha);
        LocalTime horaPedida = LocalTime.parse(hora);

        // Act
        Optional<ReglaIncumplida> resultado = reglas.validarHora(dia, horaPedida);

        // Assert
        assertThat(resultado).isEmpty();
    }

    @Test
    @DisplayName("validarHora: sin fecha conocida no se evalúa si el horario ya pasó")
    void validarHora_sinFecha_noEvaluaHorarioPasado() {
        // Arrange
        LocalTime horaPedida = LocalTime.of(9, 30);

        // Act
        Optional<ReglaIncumplida> resultado = reglas.validarHora(null, horaPedida);

        // Assert
        assertThat(resultado).isEmpty();
    }

    @Test
    @DisplayName("validarHora: 09:30 de otro día no está pasado")
    void validarHora_mismaHoraEnOtroDia_noEstaPasada() {
        // Arrange
        LocalDate manana = LocalDate.parse("2030-01-05");

        // Act
        Optional<ReglaIncumplida> resultado = reglas.validarHora(manana, LocalTime.of(9, 30));

        // Assert
        assertThat(resultado).isEmpty();
    }

    @Test
    @DisplayName("horariosDelDia: hoy a las 10:00 solo ofrece desde las 10:30 hasta las 17:30")
    void horariosDelDia_hoy_soloDevuelveLosHorariosFuturos() {
        // Arrange
        LocalDate hoy = HOY;

        // Act
        List<LocalTime> horarios = reglas.horariosDelDia(hoy);

        // Assert
        assertThat(horarios).hasSize(15)
                .first().isEqualTo(LocalTime.of(10, 30));
        assertThat(horarios).last().isEqualTo(LocalTime.of(17, 30));
        assertThat(horarios).doesNotContain(LocalTime.of(9, 0), LocalTime.of(9, 30), LocalTime.of(10, 0));
    }

    @Test
    @DisplayName("horariosDelDia: un día futuro de atención ofrece los 18 horarios de 09:00 a 17:30")
    void horariosDelDia_diaFuturo_devuelveTodosLosHorarios() {
        // Arrange
        LocalDate dia = MARTES;

        // Act
        List<LocalTime> horarios = reglas.horariosDelDia(dia);

        // Assert
        assertThat(horarios).hasSize(18)
                .first().isEqualTo(LocalTime.of(9, 0));
        assertThat(horarios).last().isEqualTo(LocalTime.of(17, 30));
        assertThat(horarios).isSorted();
    }

    @ParameterizedTest(name = "{0} → vacío")
    @DisplayName("horariosDelDia: un día sin atención (domingo o lunes) devuelve una lista vacía")
    @CsvSource({
            "2030-01-06",
            "2030-01-07"
    })
    void horariosDelDia_diaSinAtencion_devuelveVacio(String fecha) {
        // Arrange
        LocalDate dia = LocalDate.parse(fecha);

        // Act
        List<LocalTime> horarios = reglas.horariosDelDia(dia);

        // Assert
        assertThat(horarios).isEmpty();
    }

    @ParameterizedTest(name = "desde {0} → {1}")
    @DisplayName("proximoDiaDeAtencion: salta los días sin atención y es inclusivo con el día de partida")
    @CsvSource({
            "2030-01-06, 2030-01-08",
            "2030-01-07, 2030-01-08",
            "2030-01-04, 2030-01-04",
            "2030-01-08, 2030-01-08"
    })
    void proximoDiaDeAtencion_desdeUnaFecha_devuelveElPrimerDiaDeAtencion(String desde, String esperado) {
        // Arrange
        LocalDate fechaDesde = LocalDate.parse(desde);

        // Act
        LocalDate resultado = reglas.proximoDiaDeAtencion(fechaDesde);

        // Assert
        assertThat(resultado).isEqualTo(LocalDate.parse(esperado));
    }

    @Test
    @DisplayName("masCercanos: devuelve los más cercanos a la referencia, ordenados cronológicamente")
    void masCercanos_referenciaEnElMedio_devuelveLosCercanosOrdenados() {
        // Arrange
        List<LocalTime> candidatos = reglas.horariosDeAtencion();

        // Act
        List<LocalTime> resultado = ReglasAgenda.masCercanos(candidatos, LocalTime.of(12, 0), 3);

        // Assert
        assertThat(resultado).containsExactly(LocalTime.of(11, 30), LocalTime.of(12, 0), LocalTime.of(12, 30));
    }

    @Test
    @DisplayName("masCercanos: ante un empate de distancia prefiere el horario más temprano")
    void masCercanos_empate_prefiereElMasTemprano() {
        // Arrange
        List<LocalTime> candidatos = reglas.horariosDeAtencion();

        // Act
        List<LocalTime> resultado = ReglasAgenda.masCercanos(candidatos, LocalTime.of(10, 15), 3);

        // Assert
        assertThat(resultado).containsExactly(LocalTime.of(9, 30), LocalTime.of(10, 0), LocalTime.of(10, 30));
    }

    @Test
    @DisplayName("masCercanos: pidiendo más de los que hay devuelve todos los candidatos ordenados")
    void masCercanos_cantidadMayorQueCandidatos_devuelveTodos() {
        // Arrange
        List<LocalTime> candidatos = List.of(LocalTime.of(15, 0), LocalTime.of(10, 0));

        // Act
        List<LocalTime> resultado = ReglasAgenda.masCercanos(candidatos, LocalTime.of(12, 0), 5);

        // Assert
        assertThat(resultado).containsExactly(LocalTime.of(10, 0), LocalTime.of(15, 0));
    }

    @Test
    @DisplayName("masCercanos: sin candidatos o con cantidad 0 devuelve una lista vacía")
    void masCercanos_sinCandidatosOCantidadCero_devuelveVacio() {
        // Arrange
        List<LocalTime> candidatos = reglas.horariosDeAtencion();

        // Act
        List<LocalTime> sinCandidatos = ReglasAgenda.masCercanos(List.of(), LocalTime.NOON, 3);
        List<LocalTime> cantidadCero = ReglasAgenda.masCercanos(candidatos, LocalTime.NOON, 0);

        // Assert
        assertThat(sinCandidatos).isEmpty();
        assertThat(cantidadCero).isEmpty();
    }
}
