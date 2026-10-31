package com.up.peluqueria.agente.skill.interpretar;

import com.up.peluqueria.agente.tiempo.CalendarioTurnos;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class NormalizadorPedidoTest {

    // = viernes 04/01/2030 10:00 en Buenos Aires.
    // sábado 2030-01-05 | domingo 06 | lunes 07 | martes 08 | miércoles 09
    // Con una ventana de 14 días, el último día reservable es el jueves 2030-01-17.
    private final Clock reloj = Clock.fixed(Instant.parse("2030-01-04T13:00:00Z"),
            ZoneId.of("America/Argentina/Buenos_Aires"));
    private final NormalizadorPedido normalizador = new NormalizadorPedido(new CalendarioTurnos(reloj, 14));

    private static final List<String> PELUQUEROS = List.of("Tobias", "Santiago", "Mateo");
    private static final ContextoInterpretacion CONTEXTO = ContextoInterpretacion.sinPedidoEnCurso(PELUQUEROS);

    private static SalidaModeloPedido salida(String intencion, String peluquero, String fecha, String hora) {
        return new SalidaModeloPedido(intencion, peluquero, fecha, hora);
    }

    // ---------------------------------------------------------------- camino feliz

    @Test
    @DisplayName("normalizar: cuando todo coincide con el mensaje devuelve el pedido sin correcciones")
    void normalizar_salidaCoherenteConElMensaje_devuelvePedidoSinCorrecciones() {
        // Arrange
        SalidaModeloPedido salida = salida("RESERVAR_TURNO", "Tobias", "2030-01-05", "10:00");
        String mensaje = "quiero cortarme con Tobias el sábado a las 10";

        // Act
        PedidoInterpretado pedido = normalizador.normalizar(salida, mensaje, CONTEXTO);

        // Assert
        assertThat(pedido.intencion()).isEqualTo(Intencion.RESERVAR_TURNO);
        assertThat(pedido.peluquero()).isEqualTo("Tobias");
        assertThat(pedido.peluqueroMencionado()).isNull();
        assertThat(pedido.fecha()).isEqualTo(LocalDate.parse("2030-01-05"));
        assertThat(pedido.hora()).isEqualTo(LocalTime.of(10, 0));
        assertThat(pedido.problemas()).isEmpty();
        assertThat(pedido.correcciones()).isEmpty();
        assertThat(pedido.tieneProblemas()).isFalse();
    }

    @Test
    @DisplayName("normalizar: los campos vacíos del modelo quedan en null")
    void normalizar_camposVacios_quedanNulos() {
        // Arrange
        SalidaModeloPedido salida = salida("SALUDO", "", "", "");

        // Act
        PedidoInterpretado pedido = normalizador.normalizar(salida, "hola, buenas tardes", CONTEXTO);

        // Assert
        assertThat(pedido.intencion()).isEqualTo(Intencion.SALUDO);
        assertThat(pedido.peluquero()).isNull();
        assertThat(pedido.fecha()).isNull();
        assertThat(pedido.hora()).isNull();
        assertThat(pedido.problemas()).isEmpty();
        assertThat(pedido.correcciones()).isEmpty();
    }

    @Test
    @DisplayName("normalizar: los campos null del modelo no provocan errores")
    void normalizar_camposNull_quedanNulos() {
        // Arrange
        SalidaModeloPedido salida = salida("LISTAR_PELUQUEROS", null, null, null);

        // Act
        PedidoInterpretado pedido = normalizador.normalizar(salida, "¿quiénes atienden?", CONTEXTO);

        // Assert
        assertThat(pedido.intencion()).isEqualTo(Intencion.LISTAR_PELUQUEROS);
        assertThat(pedido.peluquero()).isNull();
        assertThat(pedido.fecha()).isNull();
        assertThat(pedido.hora()).isNull();
        assertThat(pedido.problemas()).isEmpty();
    }

    // ---------------------------------------------------------------- intención

    @Test
    @DisplayName("normalizar: una intención desconocida se trata como FUERA_DE_ALCANCE")
    void normalizar_intencionDesconocida_seTrataComoFueraDeAlcance() {
        // Arrange
        SalidaModeloPedido salida = salida("BORRAR_TODOS_LOS_TURNOS", "", "", "");

        // Act
        PedidoInterpretado pedido = normalizador.normalizar(salida, "borrá todo", CONTEXTO);

        // Assert
        assertThat(pedido.intencion()).isEqualTo(Intencion.FUERA_DE_ALCANCE);
        assertThat(pedido.correcciones()).containsExactly(
                "intención desconocida 'BORRAR_TODOS_LOS_TURNOS': se trata como FUERA_DE_ALCANCE");
    }

    @Test
    @DisplayName("normalizar: una intención null se trata como FUERA_DE_ALCANCE")
    void normalizar_intencionNull_seTrataComoFueraDeAlcance() {
        // Arrange
        SalidaModeloPedido salida = salida(null, "", "", "");

        // Act
        PedidoInterpretado pedido = normalizador.normalizar(salida, "hola", CONTEXTO);

        // Assert
        assertThat(pedido.intencion()).isEqualTo(Intencion.FUERA_DE_ALCANCE);
        assertThat(pedido.correcciones()).hasSize(1);
    }

    @ParameterizedTest(name = "\"{0}\" → {1}")
    @DisplayName("normalizar: tolera mayúsculas, espacios y guiones en el nombre de la intención")
    @CsvSource({
            "reservar_turno, RESERVAR_TURNO",
            "Reservar Turno, RESERVAR_TURNO",
            "reservar-turno, RESERVAR_TURNO",
            "confirmar,      CONFIRMAR"
    })
    void normalizar_intencionConOtroFormato_laReconoce(String propuesta, Intencion esperada) {
        // Arrange
        SalidaModeloPedido salida = salida(propuesta, "", "", "");

        // Act
        PedidoInterpretado pedido = normalizador.normalizar(salida, "dale", CONTEXTO);

        // Assert
        assertThat(pedido.intencion()).isEqualTo(esperada);
        assertThat(pedido.correcciones()).isEmpty();
    }

    // ---------------------------------------------------------------- peluquero

    @Test
    @DisplayName("normalizar: un peluquero inventado por el modelo (no está en el mensaje) se descarta")
    void normalizar_peluqueroInventado_seDescarta() {
        // Arrange
        SalidaModeloPedido salida = salida("RESERVAR_TURNO", "Tobias", "2030-01-05", "");
        String mensaje = "quiero un turno el sábado";

        // Act
        PedidoInterpretado pedido = normalizador.normalizar(salida, mensaje, CONTEXTO);

        // Assert
        assertThat(pedido.peluquero()).isNull();
        assertThat(pedido.peluqueroMencionado()).isNull();
        assertThat(pedido.problemas()).isEmpty();
        assertThat(pedido.correcciones())
                .containsExactly("se descartó el peluquero 'Tobias' porque no aparece en el mensaje");
    }

    @ParameterizedTest(name = "modelo=\"{0}\" mensaje=\"{1}\" → {2}")
    @DisplayName("normalizar: empareja abreviaturas, tildes y mayúsculas con el peluquero real")
    @CsvSource(delimiter = '|', value = {
            "santi  | con santi el sábado    | Santiago",
            "Santi  | con Santi el sábado    | Santiago",
            "tobi   | turno con tobi         | Tobias",
            "Tobías | turno con Tobías       | Tobias",
            "MATEO  | turno con mateo        | Mateo",
            "mate   | turno con mate         | Mateo"
    })
    void normalizar_abreviaturaOTildes_resuelveAlPeluqueroReal(String delModelo, String mensaje, String esperado) {
        // Arrange
        SalidaModeloPedido salida = salida("RESERVAR_TURNO", delModelo, "", "");

        // Act
        PedidoInterpretado pedido = normalizador.normalizar(salida, mensaje, CONTEXTO);

        // Assert
        assertThat(pedido.peluquero()).isEqualTo(esperado);
        assertThat(pedido.peluqueroMencionado()).isNull();
        assertThat(pedido.problemas()).doesNotContain(ProblemaDato.PELUQUERO_DESCONOCIDO);
    }

    @Test
    @DisplayName("normalizar: un peluquero que no existe se informa como PELUQUERO_DESCONOCIDO")
    void normalizar_peluqueroInexistente_informaPeluqueroDesconocido() {
        // Arrange
        SalidaModeloPedido salida = salida("RESERVAR_TURNO", "Bruno", "", "");
        String mensaje = "quiero un turno con Bruno";

        // Act
        PedidoInterpretado pedido = normalizador.normalizar(salida, mensaje, CONTEXTO);

        // Assert
        assertThat(pedido.peluquero()).isNull();
        assertThat(pedido.peluqueroMencionado()).isEqualTo("Bruno");
        assertThat(pedido.problemas()).containsExactly(ProblemaDato.PELUQUERO_DESCONOCIDO);
        assertThat(pedido.tieneProblemas()).isTrue();
    }

    @Test
    @DisplayName("normalizar: si el modelo no extrajo el peluquero pero el mensaje nombra a uno, lo toma del mensaje")
    void normalizar_modeloNoExtrajoPeluquero_loTomaDelMensaje() {
        // Arrange
        SalidaModeloPedido salida = salida("RESERVAR_TURNO", "", "", "");

        // Act
        PedidoInterpretado pedido = normalizador.normalizar(salida, "con Mateo, por favor", CONTEXTO);

        // Assert
        assertThat(pedido.peluquero()).isEqualTo("Mateo");
        assertThat(pedido.correcciones())
                .containsExactly("se tomó el peluquero 'Mateo' del mensaje porque el modelo no lo extrajo");
    }

    @Test
    @DisplayName("normalizar: sin ayuda del modelo no confunde 'un mate' con el peluquero Mateo")
    void normalizar_mensajeConPalabraParecida_noLaTomaComoPeluquero() {
        // Arrange
        SalidaModeloPedido salida = salida("RESERVAR_TURNO", "", "", "");

        // Act
        PedidoInterpretado pedido = normalizador.normalizar(salida, "tomo un mate y quiero un turno", CONTEXTO);

        // Assert
        assertThat(pedido.peluquero()).isNull();
        assertThat(pedido.correcciones()).isEmpty();
    }

    @Test
    @DisplayName("normalizar: el peluquero del pedido en curso se conserva aunque el mensaje no lo repita")
    void normalizar_peluqueroDelPedidoEnCurso_seConserva() {
        // Arrange
        PedidoInterpretado enCurso = new PedidoInterpretado(Intencion.RESERVAR_TURNO, "Santiago", null,
                null, null, Set.of(), List.of());
        ContextoInterpretacion contexto = new ContextoInterpretacion(PELUQUEROS, enCurso);
        SalidaModeloPedido salida = salida("RESERVAR_TURNO", "Santiago", "2030-01-05", "");

        // Act
        PedidoInterpretado pedido = normalizador.normalizar(salida, "el sábado", contexto);

        // Assert
        assertThat(pedido.peluquero()).isEqualTo("Santiago");
        assertThat(pedido.correcciones()).isEmpty();
    }

    // ---------------------------------------------------------------- fecha

    @Test
    @DisplayName("normalizar: si el modelo propone el sábado equivocado, gana la fecha calculada por el código")
    void normalizar_modeloProponeSabadoEquivocado_corrigeLaFecha() {
        // Arrange
        SalidaModeloPedido salida = salida("RESERVAR_TURNO", "", "2030-01-12", "");
        String mensaje = "quiero un turno el sábado";

        // Act
        PedidoInterpretado pedido = normalizador.normalizar(salida, mensaje, CONTEXTO);

        // Assert
        assertThat(pedido.fecha()).isEqualTo(LocalDate.parse("2030-01-05"));
        assertThat(pedido.correcciones()).containsExactly(
                "fecha corregida: el modelo propuso 2030-01-12 pero el mensaje indica sábado 05/01");
        assertThat(pedido.problemas()).isEmpty();
    }

    @Test
    @DisplayName("normalizar: si el modelo no devuelve fecha pero el mensaje la permite calcular, la calcula")
    void normalizar_modeloSinFechaYMensajeConFecha_laCalcula() {
        // Arrange
        SalidaModeloPedido salida = salida("CONSULTAR_DISPONIBILIDAD", "", "", "");

        // Act
        PedidoInterpretado pedido = normalizador.normalizar(salida, "¿qué horarios hay mañana?", CONTEXTO);

        // Assert
        assertThat(pedido.fecha()).isEqualTo(LocalDate.parse("2030-01-05"));
        assertThat(pedido.correcciones())
                .containsExactly("fecha calculada a partir del mensaje: sábado 05/01");
    }

    @Test
    @DisplayName("normalizar: una fecha inventada por el modelo (el mensaje no menciona ninguna) se descarta")
    void normalizar_fechaInventada_seDescarta() {
        // Arrange
        SalidaModeloPedido salida = salida("RESERVAR_TURNO", "", "2030-01-05", "");

        // Act
        PedidoInterpretado pedido = normalizador.normalizar(salida, "quiero un turno", CONTEXTO);

        // Assert
        assertThat(pedido.fecha()).isNull();
        assertThat(pedido.correcciones())
                .containsExactly("se descartó la fecha 2030-01-05 porque el mensaje no menciona ninguna fecha");
    }

    @Test
    @DisplayName("normalizar: la fecha del pedido en curso se conserva aunque el mensaje no la repita")
    void normalizar_fechaDelPedidoEnCurso_seConserva() {
        // Arrange
        PedidoInterpretado enCurso = new PedidoInterpretado(Intencion.RESERVAR_TURNO, null, null,
                LocalDate.parse("2030-01-05"), LocalTime.of(16, 0), Set.of(), List.of());
        ContextoInterpretacion contexto = new ContextoInterpretacion(PELUQUEROS, enCurso);
        SalidaModeloPedido salida = salida("RESERVAR_TURNO", "Santiago", "2030-01-05", "16:00");

        // Act
        PedidoInterpretado pedido = normalizador.normalizar(salida, "con Santiago", contexto);

        // Assert
        assertThat(pedido.fecha()).isEqualTo(LocalDate.parse("2030-01-05"));
        assertThat(pedido.hora()).isEqualTo(LocalTime.of(16, 0));
        assertThat(pedido.peluquero()).isEqualTo("Santiago");
        assertThat(pedido.correcciones()).isEmpty();
    }

    @ParameterizedTest(name = "\"{0}\" → {1}")
    @DisplayName("normalizar: una fecha fuera de la ventana de 14 días informa FECHA_FUERA_DE_RANGO")
    @CsvSource({
            "quiero turno el 18/01,  FECHA_FUERA_DE_RANGO",
            "quiero turno el 30/01,  FECHA_FUERA_DE_RANGO",
            "quiero turno el 02/01,  FECHA_FUERA_DE_RANGO"
    })
    void normalizar_fechaFueraDeLaVentana_informaFueraDeRango(String mensaje, ProblemaDato esperado) {
        // Arrange
        SalidaModeloPedido salida = salida("RESERVAR_TURNO", "", "", "");

        // Act
        PedidoInterpretado pedido = normalizador.normalizar(salida, mensaje, CONTEXTO);

        // Assert
        assertThat(pedido.fecha()).isNotNull();
        assertThat(pedido.problemas()).containsExactly(esperado);
    }

    @Test
    @DisplayName("normalizar: una fecha pasada informa FECHA_PASADA")
    void normalizar_fechaPasada_informaFechaPasada() {
        // Arrange
        SalidaModeloPedido salida = salida("RESERVAR_TURNO", "", "", "");

        // Act
        PedidoInterpretado pedido = normalizador.normalizar(salida, "quiero turno el 15/01/2029", CONTEXTO);

        // Assert
        assertThat(pedido.fecha()).isEqualTo(LocalDate.parse("2029-01-15"));
        assertThat(pedido.problemas()).containsExactly(ProblemaDato.FECHA_PASADA);
    }

    @ParameterizedTest(name = "\"{0}\" → sin problemas")
    @DisplayName("normalizar: los extremos de la ventana (hoy y el día 14) son válidos")
    @CsvSource({
            "quiero turno hoy",
            "quiero turno el 17/01"
    })
    void normalizar_extremosDeLaVentana_noInformaProblemas(String mensaje) {
        // Arrange
        SalidaModeloPedido salida = salida("RESERVAR_TURNO", "", "", "");

        // Act
        PedidoInterpretado pedido = normalizador.normalizar(salida, mensaje, CONTEXTO);

        // Assert
        assertThat(pedido.fecha()).isNotNull();
        assertThat(pedido.problemas()).isEmpty();
    }

    @Test
    @DisplayName("normalizar: una fecha con formato inválido (sin referencias en el mensaje) informa FECHA_INVALIDA")
    void normalizar_fechaConFormatoInvalido_informaFechaInvalida() {
        // Arrange
        SalidaModeloPedido salida = salida("RESERVAR_TURNO", "", "abc", "");

        // Act
        PedidoInterpretado pedido = normalizador.normalizar(salida, "quiero un turno", CONTEXTO);

        // Assert
        assertThat(pedido.fecha()).isNull();
        assertThat(pedido.problemas()).containsExactly(ProblemaDato.FECHA_INVALIDA);
        assertThat(pedido.correcciones())
                .containsExactly("el modelo devolvió una fecha con formato inválido: 'abc'");
    }

    @Test
    @DisplayName("normalizar: si el modelo devuelve 'sábado' en vez de la fecha, la interpreta igual")
    void normalizar_modeloDevuelveNombreDeDia_laInterpreta() {
        // Arrange
        SalidaModeloPedido salida = salida("RESERVAR_TURNO", "", "sábado", "");

        // Act
        PedidoInterpretado pedido = normalizador.normalizar(salida, "el sábado", CONTEXTO);

        // Assert
        assertThat(pedido.fecha()).isEqualTo(LocalDate.parse("2030-01-05"));
        assertThat(pedido.problemas()).isEmpty();
    }

    // ---------------------------------------------------------------- hora

    @Test
    @DisplayName("normalizar: una hora inventada por el modelo (el mensaje no menciona ninguna) se descarta")
    void normalizar_horaInventada_seDescarta() {
        // Arrange
        SalidaModeloPedido salida = salida("RESERVAR_TURNO", "", "2030-01-05", "10:00");
        String mensaje = "quiero un turno el sábado";

        // Act
        PedidoInterpretado pedido = normalizador.normalizar(salida, mensaje, CONTEXTO);

        // Assert
        assertThat(pedido.hora()).isNull();
        assertThat(pedido.correcciones())
                .containsExactly("se descartó la hora 10:00 porque el mensaje no menciona ninguna hora");
    }

    @ParameterizedTest(name = "modelo=\"{0}\" → {1}")
    @DisplayName("normalizar: normaliza la hora del modelo a HH:MM y registra la corrección")
    @CsvSource(quoteCharacter = '"', value = {
            "10,    10:00, \"hora normalizada: '10' → 10:00\"",
            "3,     15:00, \"hora normalizada: '3' → 15:00\"",
            "10hs,  10:00, \"hora normalizada: '10hs' → 10:00\"",
            "10.30, 10:30, \"hora normalizada: '10.30' → 10:30\""
    })
    void normalizar_horaConOtroFormato_laNormaliza(String delModelo, String esperada, String correccion) {
        // Arrange
        SalidaModeloPedido salida = salida("RESERVAR_TURNO", "", "", delModelo);

        // Act
        PedidoInterpretado pedido = normalizador.normalizar(salida, "a las " + delModelo, CONTEXTO);

        // Assert
        assertThat(pedido.hora()).isEqualTo(LocalTime.parse(esperada));
        assertThat(pedido.correcciones()).containsExactly(correccion);
    }

    @Test
    @DisplayName("normalizar: una hora con formato inválido informa HORA_INVALIDA")
    void normalizar_horaConFormatoInvalido_informaHoraInvalida() {
        // Arrange
        SalidaModeloPedido salida = salida("RESERVAR_TURNO", "", "", "25");

        // Act
        PedidoInterpretado pedido = normalizador.normalizar(salida, "a las 25", CONTEXTO);

        // Assert
        assertThat(pedido.hora()).isNull();
        assertThat(pedido.problemas()).containsExactly(ProblemaDato.HORA_INVALIDA);
        assertThat(pedido.correcciones())
                .containsExactly("el modelo devolvió una hora con formato inválido: '25'");
    }

    // ---------------------------------------------------------------- varios

    @Test
    @DisplayName("normalizar: acumula varios problemas y correcciones en un mismo pedido")
    void normalizar_variosProblemas_losAcumula() {
        // Arrange
        SalidaModeloPedido salida = salida("RESERVAR_TURNO", "Bruno", "2030-01-12", "10:00");
        String mensaje = "turno con Bruno el sábado a las 10 y también el 30/01";

        // Act
        PedidoInterpretado pedido = normalizador.normalizar(salida, mensaje, CONTEXTO);

        // Assert
        assertThat(pedido.peluqueroMencionado()).isEqualTo("Bruno");
        assertThat(pedido.problemas()).contains(ProblemaDato.PELUQUERO_DESCONOCIDO);
        assertThat(pedido.tieneProblemas()).isTrue();
    }
}
