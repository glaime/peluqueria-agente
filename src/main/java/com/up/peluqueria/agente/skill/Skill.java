package com.up.peluqueria.agente.skill;

import com.up.peluqueria.agente.definicion.TipoSkill;

/**
 * Capacidad concreta del agente.
 *
 * <p>Cada implementación tiene un {@code SKILL.md} con su contrato (rol, objetivo, entradas,
 * salidas y restricciones) en {@code resources/agente/skills/<nombre>/}. Una skill solo puede
 * usarse si el {@code .agent.md} la declara en su lista {@code skills}.</p>
 */
public interface Skill {

    /**
     * @return nombre de la skill; coincide con el de su carpeta y con la lista del {@code .agent.md}
     */
    String nombre();

    /**
     * @return quién resuelve la skill: el modelo ({@link TipoSkill#LLM}) o el código
     *         ({@link TipoSkill#DETERMINISTA}); debe coincidir con el {@code type} de su {@code SKILL.md}
     */
    TipoSkill tipo();
}
