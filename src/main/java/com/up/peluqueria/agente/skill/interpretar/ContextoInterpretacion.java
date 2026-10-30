package com.up.peluqueria.agente.skill.interpretar;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Información que necesita el modelo, además del mensaje, para interpretar el pedido.
 *
 * @param peluqueros    nombres de los peluqueros que trabajan en la peluquería
 * @param pedidoEnCurso pedido que el cliente viene armando en mensajes anteriores; puede no haber
 */
public record ContextoInterpretacion(List<String> peluqueros, @Nullable PedidoInterpretado pedidoEnCurso) {

    public ContextoInterpretacion {
        peluqueros = List.copyOf(peluqueros);
    }

    public static ContextoInterpretacion sinPedidoEnCurso(List<String> peluqueros) {
        return new ContextoInterpretacion(peluqueros, null);
    }
}
