package com.up.peluqueria.agente.modelo;

import org.jspecify.annotations.Nullable;

/**
 * Respuesta del modelo junto con su telemetría.
 *
 * @param texto           texto generado por el modelo (sin procesar)
 * @param modelo          modelo que generó la respuesta
 * @param duracionMs      tiempo total de la llamada, incluida la red
 * @param tokensEntrada   tokens del prompt; puede faltar según el proveedor
 * @param tokensSalida    tokens generados; puede faltar según el proveedor
 */
public record RespuestaModelo(
        String texto,
        String modelo,
        long duracionMs,
        @Nullable Integer tokensEntrada,
        @Nullable Integer tokensSalida) {
}
