package com.up.peluqueria.agente.conversacion;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * Memoria del agente sobre una conversación: en qué estado está y qué pedido viene armando
 * el cliente. No guarda los mensajes: solo lo necesario para continuar.
 *
 * @param id               identificador de la sesión (UUID)
 * @param estado           punto de la conversación
 * @param pedido           pedido en curso, si lo hay
 * @param ultimaActividad  momento del último mensaje, para descartar sesiones abandonadas
 */
public record SesionConversacion(
        String id,
        EstadoConversacion estado,
        @Nullable PedidoEnCurso pedido,
        Instant ultimaActividad) {

    public static SesionConversacion nueva(String id, Instant ahora) {
        return new SesionConversacion(id, EstadoConversacion.LIBRE, null, ahora);
    }
}
