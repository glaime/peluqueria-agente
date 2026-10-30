package com.up.peluqueria.agente.modelo;

/**
 * Puerto de salida hacia el modelo de lenguaje.
 *
 * <p>Las skills dependen de esta interfaz y no de Ollama ni de Spring AI. Así los tests
 * reemplazan el modelo por un doble de prueba, y pasar a un modelo en la nube
 * solo requiere otra implementación.</p>
 */
public interface ClienteModelo {

    /**
     * Envía una solicitud al modelo y devuelve el texto generado junto con su telemetría.
     *
     * @param solicitud instrucciones de sistema, mensaje y (opcionalmente) esquema de salida
     * @return la respuesta del modelo
     * @throws ModeloNoDisponibleException si el modelo no responde o devuelve una respuesta vacía
     */
    RespuestaModelo generar(SolicitudModelo solicitud);
}
