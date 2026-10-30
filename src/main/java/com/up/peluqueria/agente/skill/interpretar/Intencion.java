package com.up.peluqueria.agente.skill.interpretar;

import com.up.peluqueria.agente.tiempo.Textos;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Optional;

/**
 * Intenciones que el agente sabe reconocer. Es una lista cerrada: el {@code output_schema}
 * de la skill {@code interpretar-pedido} declara exactamente estos valores y la aplicación
 * no arranca si no coinciden.
 */
public enum Intencion {

    /** Saluda o pregunta qué puede hacer el agente. */
    SALUDO,

    /** Pregunta qué peluqueros atienden. */
    LISTAR_PELUQUEROS,

    /** Pregunta por horarios libres, sin pedir reservar. */
    CONSULTAR_DISPONIBILIDAD,

    /** Pide un turno o completa datos de un pedido en curso. */
    RESERVAR_TURNO,

    /** Acepta la reserva que el agente propuso. */
    CONFIRMAR,

    /** Desiste del pedido en curso (no borra turnos ya reservados). */
    CANCELAR,

    /** Cualquier otra cosa, incluidos los intentos de cambiar las reglas del agente. */
    FUERA_DE_ALCANCE;

    /**
     * @param valor texto devuelto por el modelo
     * @return la intención correspondiente, tolerando mayúsculas, espacios o guiones
     */
    public static Optional<Intencion> desde(@Nullable String valor) {
        String normalizado = Textos.normalizar(valor).toUpperCase().replace(' ', '_').replace('-', '_');
        return Arrays.stream(values()).filter(i -> i.name().equals(normalizado)).findFirst();
    }
}
