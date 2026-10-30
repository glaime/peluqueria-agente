package com.up.peluqueria.agente.definicion;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Resultado de cargar el {@code .agent.md} junto con el {@code SKILL.md} de cada skill que declara.
 *
 * @param agente definición del agente
 * @param skills definiciones de las skills habilitadas, en el orden en que se declararon
 */
public record CatalogoAgente(DefinicionAgente agente, Map<String, DefinicionSkill> skills) {

    public CatalogoAgente {
        skills = Collections.unmodifiableMap(new LinkedHashMap<>(skills));
    }

    public Optional<DefinicionSkill> skill(String nombre) {
        return Optional.ofNullable(skills.get(nombre));
    }
}
