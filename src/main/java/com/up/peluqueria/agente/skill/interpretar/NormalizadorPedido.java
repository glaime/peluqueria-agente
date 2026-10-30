package com.up.peluqueria.agente.skill.interpretar;

import com.up.peluqueria.agente.tiempo.CalendarioTurnos;
import com.up.peluqueria.agente.tiempo.ExtractorTemporal;
import com.up.peluqueria.agente.tiempo.NormalizadorHora;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Convierte la salida cruda del modelo en un {@link PedidoInterpretado} confiable.
 *
 * <p>Principio de diseño: <b>el modelo propone, el código verifica</b>. Cada dato que propone
 * el modelo se contrasta con el mensaje original del cliente:</p>
 * <ul>
 *   <li><b>Intención:</b> si no es una de las permitidas, se trata como FUERA_DE_ALCANCE.</li>
 *   <li><b>Peluquero:</b> debe existir y aparecer en el mensaje.</li>
 *   <li><b>Fecha:</b> si el mensaje permite calcularla ("el sábado"), gana el cálculo del código;
 *       si el mensaje no menciona ninguna fecha, se descarta la del modelo.</li>
 *   <li><b>Hora:</b> se normaliza a HH:MM y se descarta si el mensaje no menciona ninguna hora.</li>
 * </ul>
 * <p>Cada ajuste queda registrado en {@link PedidoInterpretado#correcciones()}.</p>
 */
@Component
public class NormalizadorPedido {

    private final CalendarioTurnos calendario;

    public NormalizadorPedido(CalendarioTurnos calendario) {
        this.calendario = calendario;
    }

    /**
     * @param salida   JSON devuelto por el modelo
     * @param mensaje  mensaje original del cliente
     * @param contexto peluqueros disponibles y pedido en curso
     * @return el pedido validado
     */
    public PedidoInterpretado normalizar(SalidaModeloPedido salida, String mensaje, ContextoInterpretacion contexto) {
        Set<ProblemaDato> problemas = EnumSet.noneOf(ProblemaDato.class);
        List<String> correcciones = new ArrayList<>();
        PedidoInterpretado enCurso = contexto.pedidoEnCurso();

        Intencion intencion = Intencion.desde(salida.intencion()).orElseGet(() -> {
            correcciones.add("intención desconocida '" + salida.intencion() + "': se trata como FUERA_DE_ALCANCE");
            return Intencion.FUERA_DE_ALCANCE;
        });

        ResolutorPeluquero.Resultado peluquero = ResolutorPeluquero.resolver(
                salida.peluquero(), mensaje, contexto.peluqueros(), enCurso == null ? null : enCurso.peluquero());
        if (peluquero.correccion() != null) {
            correcciones.add(peluquero.correccion());
        }
        if (peluquero.mencionadoDesconocido() != null) {
            problemas.add(ProblemaDato.PELUQUERO_DESCONOCIDO);
        }

        LocalDate fecha = resolverFecha(salida.fecha(), mensaje, enCurso, problemas, correcciones);
        LocalTime hora = resolverHora(salida.hora(), mensaje, enCurso, problemas, correcciones);

        return new PedidoInterpretado(intencion, peluquero.peluquero(), peluquero.mencionadoDesconocido(),
                fecha, hora, problemas, correcciones);
    }

    private @Nullable LocalDate resolverFecha(@Nullable String delModelo, String mensaje,
                                              @Nullable PedidoInterpretado enCurso,
                                              Set<ProblemaDato> problemas, List<String> correcciones) {
        LocalDate hoy = calendario.hoy();
        String crudo = delModelo == null ? "" : delModelo.strip();
        LocalDate propuesta = crudo.isEmpty() ? null : interpretarFecha(crudo, hoy).orElse(null);

        Set<LocalDate> delMensaje = ExtractorTemporal.fechasMencionadas(mensaje, hoy);
        LocalDate fecha;

        if (delMensaje.size() == 1) {
            LocalDate verificada = delMensaje.iterator().next();
            if (propuesta != null && !propuesta.equals(verificada)) {
                correcciones.add("fecha corregida: el modelo propuso " + propuesta
                        + " pero el mensaje indica " + calendario.describir(verificada));
            } else if (propuesta == null) {
                correcciones.add("fecha calculada a partir del mensaje: " + calendario.describir(verificada));
            }
            fecha = verificada;
        } else if (propuesta != null) {
            boolean vieneDelPedido = enCurso != null && propuesta.equals(enCurso.fecha());
            if (!vieneDelPedido && !ExtractorTemporal.mencionaFecha(mensaje, hoy)) {
                correcciones.add("se descartó la fecha " + propuesta + " porque el mensaje no menciona ninguna fecha");
                fecha = null;
            } else {
                fecha = propuesta;
            }
        } else {
            if (!crudo.isEmpty()) {
                problemas.add(ProblemaDato.FECHA_INVALIDA);
                correcciones.add("el modelo devolvió una fecha con formato inválido: '" + crudo + "'");
            }
            fecha = null;
        }

        if (fecha != null && !calendario.estaEnVentana(fecha)) {
            problemas.add(fecha.isBefore(hoy) ? ProblemaDato.FECHA_PASADA : ProblemaDato.FECHA_FUERA_DE_RANGO);
        }
        return fecha;
    }

    private @Nullable LocalTime resolverHora(@Nullable String delModelo, String mensaje,
                                             @Nullable PedidoInterpretado enCurso,
                                             Set<ProblemaDato> problemas, List<String> correcciones) {
        String crudo = delModelo == null ? "" : delModelo.strip();
        if (crudo.isEmpty()) {
            return null;
        }

        Optional<LocalTime> hora = NormalizadorHora.normalizar(crudo);
        if (hora.isEmpty()) {
            problemas.add(ProblemaDato.HORA_INVALIDA);
            correcciones.add("el modelo devolvió una hora con formato inválido: '" + crudo + "'");
            return null;
        }

        boolean vieneDelPedido = enCurso != null && hora.get().equals(enCurso.hora());
        if (!vieneDelPedido && !ExtractorTemporal.mencionaHora(mensaje)) {
            correcciones.add("se descartó la hora " + hora.get() + " porque el mensaje no menciona ninguna hora");
            return null;
        }
        if (!crudo.equals(hora.get().toString())) {
            correcciones.add("hora normalizada: '" + crudo + "' → " + hora.get());
        }
        return hora.get();
    }

    /**
     * Acepta la fecha en ISO ({@code 2026-10-31}) y, como respaldo, lo que el modelo a veces
     * devuelve en lugar de la fecha: {@code "sábado"}, {@code "mañana"} o {@code "31/10"}.
     */
    private static Optional<LocalDate> interpretarFecha(String texto, LocalDate hoy) {
        try {
            return Optional.of(LocalDate.parse(texto));
        } catch (DateTimeParseException e) {
            Set<LocalDate> candidatas = ExtractorTemporal.fechasMencionadas(texto, hoy);
            return candidatas.size() == 1 ? Optional.of(candidatas.iterator().next()) : Optional.empty();
        }
    }
}
