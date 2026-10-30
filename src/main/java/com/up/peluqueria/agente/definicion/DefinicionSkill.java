package com.up.peluqueria.agente.definicion;

import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * Skill tal como quedó declarada en su archivo {@code SKILL.md}.
 *
 * @param nombre        identificador de la skill; coincide con el nombre de su carpeta
 * @param descripcion   qué tarea concreta resuelve
 * @param tipo          si la resuelve el modelo ({@link TipoSkill#LLM}) o el código ({@link TipoSkill#DETERMINISTA})
 * @param esquemaSalida JSON Schema que el modelo está obligado a respetar; solo para skills LLM
 * @param instrucciones cuerpo del archivo: rol, objetivo, entradas, salida, reglas y ejemplos
 * @param origen        ubicación del archivo
 */
public record DefinicionSkill(
        String nombre,
        String descripcion,
        TipoSkill tipo,
        @Nullable Map<String, Object> esquemaSalida,
        String instrucciones,
        String origen) {
}
