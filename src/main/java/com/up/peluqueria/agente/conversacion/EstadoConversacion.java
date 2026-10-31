package com.up.peluqueria.agente.conversacion;

/**
 * En qué punto está la conversación con un cliente.
 *
 * <pre>
 * LIBRE ──(pide turno)──► RECOLECTANDO_DATOS ──(datos completos y horario libre)──► ESPERANDO_CONFIRMACION
 *   ▲                                ▲                                                  │
 *   │                                └──────────────(cambia un dato)────────────────────┤
 *   └─────────────────────(confirma: reserva, o cancela)────────────────────────────────┘
 * </pre>
 */
public enum EstadoConversacion {

    /** No hay ningún pedido en curso. */
    LIBRE,

    /** Hay un pedido en curso al que le faltan datos (peluquero, fecha u hora). */
    RECOLECTANDO_DATOS,

    /** El pedido está completo y el agente preguntó "¿confirmás?". */
    ESPERANDO_CONFIRMACION
}
