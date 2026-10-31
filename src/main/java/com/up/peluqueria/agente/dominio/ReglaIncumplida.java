package com.up.peluqueria.agente.dominio;

/**
 * Regla de la agenda que no cumple una fecha u hora pedida.
 */
public enum ReglaIncumplida {

    /** Domingo o lunes: la peluquería no atiende. */
    DIA_SIN_ATENCION,

    /** Antes de las 09:00 o después de las 17:30. */
    FUERA_DE_HORARIO,

    /** Los turnos empiezan en punto o y media (10:15 no es válido). */
    MINUTOS_NO_PERMITIDOS,

    /** Es hoy y esa hora ya pasó. */
    HORARIO_PASADO
}
