package com.up.peluqueria.agente.web;

import com.up.peluqueria.agente.skill.RegistroSkills;
import com.up.peluqueria.agente.skill.interpretar.ContextoInterpretacion;
import com.up.peluqueria.agente.skill.interpretar.InterpretarPedidoSkill;
import com.up.peluqueria.agente.skill.interpretar.ResultadoInterpretacion;
import com.up.peluqueria.dto.response.PeluqueroResponseDTO;
import com.up.peluqueria.service.PeluqueroService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Endpoints para probar cada skill de forma aislada y ver qué devuelve el modelo.
 *
 * <p>Exponen la salida cruda del modelo, por lo que solo se registran si
 * {@code agente.diagnostico.habilitado=true}. En producción deben quedar apagados.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/agente/diagnostico")
@ConditionalOnProperty(name = "agente.diagnostico.habilitado", havingValue = "true")
@Tag(name = "Agente - diagnóstico", description = "Prueba aislada de skills (solo en desarrollo)")
public class DiagnosticoAgenteController {

    private final RegistroSkills registro;
    private final InterpretarPedidoSkill interpretarPedido;
    private final PeluqueroService peluqueroService;

    public DiagnosticoAgenteController(RegistroSkills registro, InterpretarPedidoSkill interpretarPedido,
                                       PeluqueroService peluqueroService) {
        this.registro = registro;
        this.interpretarPedido = interpretarPedido;
        this.peluqueroService = peluqueroService;
    }

    @PostMapping("/interpretar")
    @Operation(summary = "Ejecuta la skill interpretar-pedido",
            description = "Devuelve el pedido validado, el JSON crudo del modelo y la telemetría de la llamada")
    public ResultadoInterpretacion interpretar(@Valid @RequestBody InterpretarRequest request) {
        log.info("[API] POST /api/agente/diagnostico/interpretar | mensaje de {} caracteres", request.mensaje().length());
        log.debug("[API] mensaje del cliente: {}", request.mensaje());

        registro.verificarHabilitada(InterpretarPedidoSkill.NOMBRE);
        List<String> peluqueros = peluqueroService.listarTodos().stream().map(PeluqueroResponseDTO::getName).toList();
        log.debug("[API] peluqueros enviados como contexto: {}", peluqueros);

        long inicio = System.nanoTime();
        ResultadoInterpretacion resultado = interpretarPedido.ejecutar(
                request.mensaje(), ContextoInterpretacion.sinPedidoEnCurso(peluqueros));
        log.info("[API] interpretación completada en {} ms | pedido={}",
                (System.nanoTime() - inicio) / 1_000_000, resultado.pedido());
        return resultado;
    }
}
