package com.up.peluqueria.agente.modelo;

import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * Solicitud a enviar al modelo de lenguaje.
 *
 * @param instruccionesSistema prompt de sistema (instrucciones del agente más las de la skill)
 * @param mensajeUsuario       contexto y mensaje del cliente
 * @param esquemaSalida        JSON Schema que obliga al modelo a responder con esa estructura; opcional
 */
public record SolicitudModelo(
        String instruccionesSistema,
        String mensajeUsuario,
        @Nullable Map<String, Object> esquemaSalida) {
}
