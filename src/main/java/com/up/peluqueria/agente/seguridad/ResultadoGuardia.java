package com.up.peluqueria.agente.seguridad;

import org.jspecify.annotations.Nullable;

/**
 * Resultado de revisar un mensaje antes de enviarlo al modelo.
 *
 * @param permitido       si el mensaje puede seguir su camino hacia el modelo
 * @param mensajeSaneado  el mensaje sin caracteres de control ni espacios de más
 * @param motivo          por qué se bloqueó; {@code null} si está permitido
 */
public record ResultadoGuardia(boolean permitido, String mensajeSaneado, @Nullable String motivo) {

    public static ResultadoGuardia permitir(String mensajeSaneado) {
        return new ResultadoGuardia(true, mensajeSaneado, null);
    }

    public static ResultadoGuardia bloquear(String mensajeSaneado, String motivo) {
        return new ResultadoGuardia(false, mensajeSaneado, motivo);
    }
}
