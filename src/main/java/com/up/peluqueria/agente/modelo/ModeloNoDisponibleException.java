package com.up.peluqueria.agente.modelo;

/**
 * El modelo de lenguaje no respondió (por ejemplo, Ollama apagado) o devolvió una respuesta vacía.
 */
public class ModeloNoDisponibleException extends RuntimeException {

    public ModeloNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }

    public ModeloNoDisponibleException(String mensaje) {
        super(mensaje);
    }
}
