package com.up.peluqueria.agente.web;

import com.up.peluqueria.agente.definicion.DefinicionAgente;
import com.up.peluqueria.agente.skill.RegistroSkills;
import com.up.peluqueria.agente.tiempo.CalendarioTurnos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Información pública del agente.
 */
@Slf4j
@RestController
@RequestMapping("/api/agente")
@Tag(name = "Agente", description = "Agente recepcionista de la peluquería")
public class AgenteController {

    private final RegistroSkills registro;
    private final CalendarioTurnos calendario;

    public AgenteController(RegistroSkills registro, CalendarioTurnos calendario) {
        this.registro = registro;
        this.calendario = calendario;
    }

    @GetMapping
    @Operation(summary = "Configuración del agente",
            description = "Modelo, parámetros y skills habilitadas, tal como se cargaron desde el archivo .agent.md")
    public InfoAgenteResponse info() {
        log.info("[API] GET /api/agente");
        DefinicionAgente agente = registro.agente();
        return new InfoAgenteResponse(agente.nombre(), agente.descripcion(), agente.modelo(), agente.parametros(),
                registro.habilitadas(), calendario.hoy(), agente.origen());
    }
}
