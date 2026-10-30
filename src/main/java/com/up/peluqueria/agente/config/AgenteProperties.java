package com.up.peluqueria.agente.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Propiedades del agente configurables desde {@code application.properties}
 * con el prefijo {@code agente.}.
 *
 * @param definicion       ubicación del archivo {@code .agent.md}
 * @param directorioSkills carpeta con una subcarpeta (y su {@code SKILL.md}) por skill
 * @param zonaHoraria      zona horaria de la peluquería, usada para saber qué día es "hoy"
 * @param diasCalendario   cantidad de días hacia adelante en los que se pueden pedir turnos
 */
@ConfigurationProperties(prefix = "agente")
public record AgenteProperties(
        @DefaultValue("classpath:agente/recepcionista.agent.md") String definicion,
        @DefaultValue("classpath:agente/skills") String directorioSkills,
        @DefaultValue("America/Argentina/Buenos_Aires") String zonaHoraria,
        @DefaultValue("14") int diasCalendario) {
}
