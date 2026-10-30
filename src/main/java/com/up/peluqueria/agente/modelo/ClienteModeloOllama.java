package com.up.peluqueria.agente.modelo;

import com.up.peluqueria.agente.definicion.DefinicionAgente;
import com.up.peluqueria.agente.definicion.ParametrosModelo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.ollama.api.OllamaChatOptions;

/**
 * Implementación de {@link ClienteModelo} sobre Ollama mediante Spring AI.
 *
 * <p>Los parámetros de cada llamada (modelo, temperatura, contexto, semilla...) salen del
 * {@code .agent.md}. Cuando la skill declara un {@code output_schema}, se envía como
 * {@code format}: Ollama restringe la generación para que el JSON respete ese esquema
 * (salida estructurada nativa), en lugar de solo pedírselo al modelo en el prompt.</p>
 */
@Slf4j
public class ClienteModeloOllama implements ClienteModelo {

    private final ChatClient chatClient;
    private final DefinicionAgente agente;

    public ClienteModeloOllama(ChatClient chatClient, DefinicionAgente agente) {
        this.chatClient = chatClient;
        this.agente = agente;
    }

    @Override
    public RespuestaModelo generar(SolicitudModelo solicitud) {
        OllamaChatOptions.Builder opciones = construirOpciones(solicitud);

        log.info("[OLLAMA] enviando solicitud | modelo={} | salida estructurada={} | system={} caracteres | user={} caracteres",
                agente.modelo(), solicitud.esquemaSalida() != null,
                solicitud.instruccionesSistema().length(), solicitud.mensajeUsuario().length());
        log.debug("[OLLAMA] mensaje de usuario:\n{}", solicitud.mensajeUsuario());

        long inicio = System.nanoTime();
        ChatResponse respuesta;
        try {
            respuesta = chatClient.prompt()
                    .system(solicitud.instruccionesSistema())
                    .user(solicitud.mensajeUsuario())
                    .options(opciones)
                    .call()
                    .chatResponse();
        } catch (RuntimeException e) {
            log.error("[OLLAMA] falló la llamada al modelo '{}' tras {} ms: {}",
                    agente.modelo(), (System.nanoTime() - inicio) / 1_000_000, e.getMessage());
            throw new ModeloNoDisponibleException(
                    "No se pudo obtener respuesta del modelo '" + agente.modelo() + "' en Ollama: " + e.getMessage(), e);
        }
        long duracionMs = (System.nanoTime() - inicio) / 1_000_000;

        Generation generacion = respuesta == null ? null : respuesta.getResult();
        String texto = generacion == null ? null : generacion.getOutput().getText();
        if (texto == null || texto.isBlank()) {
            log.warn("[OLLAMA] el modelo '{}' devolvió una respuesta vacía tras {} ms", agente.modelo(), duracionMs);
            throw new ModeloNoDisponibleException("El modelo '" + agente.modelo() + "' devolvió una respuesta vacía");
        }

        Usage uso = respuesta.getMetadata().getUsage();
        log.info("[OLLAMA] respuesta recibida | modelo={} | {} ms | tokens entrada={} salida={}",
                agente.modelo(), duracionMs, uso.getPromptTokens(), uso.getCompletionTokens());
        log.debug("[OLLAMA] texto devuelto: {}", texto);
        return new RespuestaModelo(texto, agente.modelo(), duracionMs, uso.getPromptTokens(), uso.getCompletionTokens());
    }

    private OllamaChatOptions.Builder construirOpciones(SolicitudModelo solicitud) {
        ParametrosModelo parametros = agente.parametros();
        OllamaChatOptions.Builder opciones = OllamaChatOptions.builder()
                .model(agente.modelo())
                .temperature(parametros.temperature())
                .topP(parametros.topP())
                .numCtx(parametros.numCtx())
                .numPredict(parametros.numPredict())
                .seed(parametros.seed())
                .keepAlive(parametros.keepAlive());
        if (solicitud.esquemaSalida() != null) {
            opciones.format(solicitud.esquemaSalida());
        }
        return opciones;
    }
}
