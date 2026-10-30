package com.up.peluqueria.agente.skill;

/**
 * Se intentó usar una skill que el {@code .agent.md} no habilita.
 */
public class SkillNoHabilitadaException extends RuntimeException {

    private final String skill;

    public SkillNoHabilitadaException(String skill) {
        super("La skill '" + skill + "' no está habilitada en la definición del agente");
        this.skill = skill;
    }

    public String getSkill() {
        return skill;
    }
}
