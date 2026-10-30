package com.up.peluqueria.agente.definicion;

import java.util.List;

/**
 * Agente tal como quedó declarado en su archivo {@code .agent.md}.
 *
 * @param nombre        identificador del agente (kebab-case)
 * @param descripcion   para qué sirve el agente
 * @param modelo        modelo de Ollama que ejecuta las skills de tipo LLM (ej. {@code qwen2.5:3b})
 * @param parametros    parámetros de inferencia del modelo
 * @param skills        skills habilitadas; funcionan como lista blanca de capacidades
 * @param instrucciones cuerpo del archivo: rol, alcance y reglas que recibe el modelo como prompt de sistema
 * @param origen        ubicación del archivo
 */
public record DefinicionAgente(
        String nombre,
        String descripcion,
        String modelo,
        ParametrosModelo parametros,
        List<String> skills,
        String instrucciones,
        String origen) {

    public DefinicionAgente {
        skills = List.copyOf(skills);
    }
}
