package com.up.peluqueria.agente.skill.interpretar;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jspecify.annotations.Nullable;

/**
 * JSON tal como lo devuelve el modelo, antes de validarlo.
 *
 * <p>Se trata como una entrada no confiable: nada de lo que contiene se usa sin pasar
 * antes por {@link NormalizadorPedido}.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SalidaModeloPedido(
        @Nullable String intencion,
        @Nullable String peluquero,
        @Nullable String fecha,
        @Nullable String hora) {
}
