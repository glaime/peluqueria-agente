package com.up.peluqueria.agente.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Mensaje a interpretar con la skill {@code interpretar-pedido}.
 */
@Schema(description = "Mensaje del cliente a interpretar")
public record InterpretarRequest(
        @NotBlank(message = "El mensaje es requerido")
        @Size(max = 500, message = "El mensaje no puede superar los 500 caracteres")
        @Schema(example = "quiero cortarme con Tobias el sábado a las 10")
        String mensaje) {
}
