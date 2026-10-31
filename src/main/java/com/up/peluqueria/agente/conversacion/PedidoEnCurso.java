package com.up.peluqueria.agente.conversacion;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.up.peluqueria.agente.skill.interpretar.Intencion;
import com.up.peluqueria.agente.skill.interpretar.PedidoInterpretado;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

/**
 * Pedido que el cliente va armando a lo largo de varios mensajes ("slot filling").
 *
 * <p>Cada mensaje completa o reemplaza algún dato: "quiero un turno el sábado" → "con Tobias"
 * → "a las 10". Es inmutable: cada cambio devuelve una copia.</p>
 *
 * @param intencion qué está haciendo el cliente (reservar o consultar disponibilidad)
 * @param peluquero peluquero elegido
 * @param fecha     fecha elegida
 * @param hora      hora elegida
 */
public record PedidoEnCurso(
        Intencion intencion,
        @Nullable String peluquero,
        @Nullable LocalDate fecha,
        @JsonFormat(pattern = "HH:mm") @Nullable LocalTime hora) {

    public static PedidoEnCurso nuevo(Intencion intencion) {
        return new PedidoEnCurso(intencion, null, null, null);
    }

    /**
     * Incorpora los datos de un mensaje nuevo: lo que el cliente acaba de decir reemplaza a lo
     * anterior, y lo que no mencionó se conserva.
     */
    public PedidoEnCurso combinar(PedidoInterpretado nuevo) {
        return new PedidoEnCurso(
                intencion,
                nuevo.peluquero() != null ? nuevo.peluquero() : peluquero,
                nuevo.fecha() != null ? nuevo.fecha() : fecha,
                nuevo.hora() != null ? nuevo.hora() : hora);
    }

    public PedidoEnCurso conIntencion(Intencion nuevaIntencion) {
        return new PedidoEnCurso(nuevaIntencion, peluquero, fecha, hora);
    }

    public PedidoEnCurso sinFecha() {
        return new PedidoEnCurso(intencion, peluquero, null, hora);
    }

    public PedidoEnCurso sinHora() {
        return new PedidoEnCurso(intencion, peluquero, fecha, null);
    }

    @JsonIgnore
    public boolean estaCompleto() {
        return peluquero != null && fecha != null && hora != null;
    }

    @JsonIgnore
    public boolean tieneDatos() {
        return peluquero != null || fecha != null || hora != null;
    }

    /**
     * @return el pedido en el formato que recibe la skill {@code interpretar-pedido} como contexto
     */
    public PedidoInterpretado comoContexto() {
        return new PedidoInterpretado(intencion, peluquero, null, fecha, hora, Set.of(), List.of());
    }
}
