package com.up.peluqueria.agente.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Mensaje del cliente para el agente.
 *
 * @param sesionId id de la conversación devuelto en la respuesta anterior; vacío para empezar una nueva
 * @param mensaje  texto del cliente
 */
@Schema(description = "Mensaje del cliente para el agente recepcionista")
public record ChatRequest(
        @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                message = "El sesionId debe ser un UUID")
        @Schema(description = "Id de la conversación. Omitirlo para empezar una nueva.", nullable = true)
        String sesionId,

        @NotBlank(message = "El mensaje es requerido")
        @Size(max = 500, message = "El mensaje no puede superar los 500 caracteres")
        @Schema(example = "quiero un turno con Tobias el sábado a las 10")
        String mensaje) {
}
