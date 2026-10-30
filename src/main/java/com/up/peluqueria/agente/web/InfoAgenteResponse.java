package com.up.peluqueria.agente.web;

import com.up.peluqueria.agente.definicion.ParametrosModelo;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

/**
 * Configuración vigente del agente, tal como se cargó desde su {@code .agent.md}.
 */
@Schema(description = "Configuración vigente del agente, cargada desde su archivo .agent.md")
public record InfoAgenteResponse(
        @Schema(example = "recepcionista") String nombre,
        String descripcion,
        @Schema(example = "qwen2.5:3b") String modelo,
        ParametrosModelo parametros,
        @Schema(example = "[\"interpretar-pedido\"]") List<String> skillsHabilitadas,
        @Schema(description = "Fecha que el agente considera como hoy") LocalDate hoy,
        @Schema(description = "Archivo del que se cargó la definición") String origen) {
}
