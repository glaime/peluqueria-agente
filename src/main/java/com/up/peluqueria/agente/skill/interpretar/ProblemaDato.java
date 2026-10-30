package com.up.peluqueria.agente.skill.interpretar;

/**
 * Problemas detectados al validar los datos que extrajo el modelo.
 */
public enum ProblemaDato {

    /** El cliente nombró a un peluquero que no trabaja en la peluquería. */
    PELUQUERO_DESCONOCIDO,

    /** La fecha no tiene un formato reconocible. */
    FECHA_INVALIDA,

    /** La fecha ya pasó. */
    FECHA_PASADA,

    /** La fecha está más allá de la ventana de días en que se dan turnos. */
    FECHA_FUERA_DE_RANGO,

    /** La hora no tiene un formato reconocible. */
    HORA_INVALIDA
}
