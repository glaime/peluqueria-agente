package com.up.peluqueria.agente.definicion;

import org.jspecify.annotations.Nullable;

/**
 * Parámetros de inferencia declarados en la sección {@code parameters} del {@code .agent.md}.
 *
 * @param temperature aleatoriedad del muestreo (0 = determinista)
 * @param topP        muestreo por núcleo; opcional
 * @param numCtx      tamaño de la ventana de contexto en tokens
 * @param numPredict  máximo de tokens que puede generar el modelo
 * @param seed        semilla para que la misma entrada produzca la misma salida; opcional
 * @param keepAlive   tiempo que Ollama mantiene el modelo cargado en memoria (ej. "10m"); opcional
 */
public record ParametrosModelo(
        double temperature,
        @Nullable Double topP,
        int numCtx,
        int numPredict,
        @Nullable Integer seed,
        @Nullable String keepAlive) {
}
