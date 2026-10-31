package com.up.peluqueria.agente.web;

import com.up.peluqueria.agente.conversacion.AgenteRecepcionista;
import com.up.peluqueria.agente.conversacion.RespuestaAgente;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Canal de chat del agente. La interfaz web ({@code /chat.html}) lo usa, pero cualquier
 * canal (WhatsApp, Telegram, etc.) podría usar el mismo endpoint.
 */
@Slf4j
@RestController
@RequestMapping("/api/agente/chat")
@Tag(name = "Agente", description = "Agente recepcionista de la peluquería")
public class ChatAgenteController {

    private final AgenteRecepcionista agente;

    public ChatAgenteController(AgenteRecepcionista agente) {
        this.agente = agente;
    }

    @PostMapping
    @Operation(summary = "Envía un mensaje al agente",
            description = "Devuelve la respuesta del agente, el estado de la conversación y las skills que ejecutó. "
                    + "Para continuar la conversación, reenviar el sesionId recibido.")
    public RespuestaAgente chatear(@Valid @RequestBody ChatRequest request) {
        // Conversación nueva: el servidor genera un UUID aleatorio. Las siguientes lo reenvían (se valida que sea un UUID).
        String sesionId = request.sesionId() == null ? UUID.randomUUID().toString() : request.sesionId();
        log.info("[API] POST /api/agente/chat | sesion={} | mensaje de {} caracteres",
                sesionId.substring(0, 8), request.mensaje().length());
        return agente.procesar(sesionId, request.mensaje());
    }
}
