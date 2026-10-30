package com.up.peluqueria.agente.definicion;

import java.util.Arrays;
import java.util.Optional;

/**
 * Tipo de una skill según quién la resuelve.
 */
public enum TipoSkill {

    /** La resuelve el modelo de lenguaje; requiere un esquema de salida ({@code output_schema}). */
    LLM("llm"),

    /** La resuelve código Java: misma entrada, misma salida, sin intervención del modelo. */
    DETERMINISTA("deterministic");

    private final String valorEnArchivo;

    TipoSkill(String valorEnArchivo) {
        this.valorEnArchivo = valorEnArchivo;
    }

    public String valorEnArchivo() {
        return valorEnArchivo;
    }

    public static Optional<TipoSkill> desde(String valor) {
        return Arrays.stream(values())
                .filter(tipo -> tipo.valorEnArchivo.equalsIgnoreCase(valor))
                .findFirst();
    }
}
