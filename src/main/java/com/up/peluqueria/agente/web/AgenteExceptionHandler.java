package com.up.peluqueria.agente.web;

import com.up.peluqueria.agente.modelo.ModeloNoDisponibleException;
import com.up.peluqueria.agente.skill.SkillNoHabilitadaException;
import com.up.peluqueria.agente.skill.interpretar.SalidaModeloInvalidaException;
import com.up.peluqueria.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

/**
 * Traduce los errores del agente a respuestas HTTP con el formato estándar de la API.
 * Tiene prioridad sobre el manejador global, que respondería 500 a todo.
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.up.peluqueria.agente")
public class AgenteExceptionHandler {

    @ExceptionHandler(ModeloNoDisponibleException.class)
    public ResponseEntity<ErrorResponse> modeloNoDisponible(ModeloNoDisponibleException ex, HttpServletRequest request) {
        log.error("[AGENTE] modelo no disponible: {}", ex.getMessage());
        return respuesta(HttpStatus.SERVICE_UNAVAILABLE,
                "El modelo de lenguaje no está disponible. Verificá que Ollama esté corriendo.", request);
    }

    @ExceptionHandler(SalidaModeloInvalidaException.class)
    public ResponseEntity<ErrorResponse> salidaInvalida(SalidaModeloInvalidaException ex, HttpServletRequest request) {
        log.warn("[AGENTE] salida inválida del modelo: {}", ex.getTextoRecibido());
        return respuesta(HttpStatus.BAD_GATEWAY, "El modelo devolvió una respuesta que no se pudo interpretar.", request);
    }

    @ExceptionHandler(SkillNoHabilitadaException.class)
    public ResponseEntity<ErrorResponse> skillNoHabilitada(SkillNoHabilitadaException ex, HttpServletRequest request) {
        log.warn("[AGENTE] intento de usar una skill no habilitada: {}", ex.getSkill());
        return respuesta(HttpStatus.FORBIDDEN, ex.getMessage(), request);
    }

    private static ResponseEntity<ErrorResponse> respuesta(HttpStatus estado, String mensaje, HttpServletRequest request) {
        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(estado.value())
                .error(estado.getReasonPhrase())
                .message(mensaje)
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(estado).body(error);
    }
}
