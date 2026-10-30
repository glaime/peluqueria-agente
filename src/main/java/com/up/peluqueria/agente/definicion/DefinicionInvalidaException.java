package com.up.peluqueria.agente.definicion;

import java.util.List;

/**
 * Se lanza al arrancar la aplicación cuando un archivo {@code .agent.md} o {@code SKILL.md}
 * no cumple el contrato esperado. Reúne todos los problemas encontrados para corregirlos
 * de una sola vez (estrategia "fail fast": la aplicación no arranca con un agente mal configurado).
 */
public class DefinicionInvalidaException extends RuntimeException {

    private final String origen;
    private final List<String> problemas;

    public DefinicionInvalidaException(String origen, List<String> problemas) {
        super(construirMensaje(origen, problemas));
        this.origen = origen;
        this.problemas = List.copyOf(problemas);
    }

    public DefinicionInvalidaException(String origen, String problema, Throwable causa) {
        super(construirMensaje(origen, List.of(problema)), causa);
        this.origen = origen;
        this.problemas = List.of(problema);
    }

    public String getOrigen() {
        return origen;
    }

    public List<String> getProblemas() {
        return problemas;
    }

    private static String construirMensaje(String origen, List<String> problemas) {
        StringBuilder sb = new StringBuilder("Definición inválida en ").append(origen).append(':');
        problemas.forEach(p -> sb.append(System.lineSeparator()).append("  - ").append(p));
        return sb.toString();
    }
}
