package com.up.peluqueria.agente.conversacion;

import com.up.peluqueria.agente.skill.interpretar.Intencion;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Respuesta del agente a un mensaje, con el detalle de lo que hizo para responder.
 *
 * @param sesionId         sesión a la que pertenece la respuesta; el cliente la reenvía en el próximo mensaje
 * @param respuesta        texto para el cliente
 * @param estado           punto de la conversación después de este mensaje
 * @param intencion        intención detectada en el mensaje
 * @param pedidoEnCurso    datos que el agente tiene del pedido hasta ahora
 * @param skillsEjecutadas skills invocadas para responder, en orden
 * @param correcciones     ajustes del código sobre lo que propuso el modelo (guardrails)
 * @param sugerencias      respuestas rápidas que el cliente puede elegir
 * @param turnoReservadoId id del turno creado, si en este mensaje se reservó uno
 * @param duracionModeloMs tiempo que tardó el modelo, si se lo consultó
 */
@Schema(description = "Respuesta del agente recepcionista")
public record RespuestaAgente(
        String sesionId,
        String respuesta,
        EstadoConversacion estado,
        @Nullable Intencion intencion,
        @Nullable PedidoEnCurso pedidoEnCurso,
        List<String> skillsEjecutadas,
        List<String> correcciones,
        List<String> sugerencias,
        @Nullable Long turnoReservadoId,
        @Nullable Long duracionModeloMs) {
}
