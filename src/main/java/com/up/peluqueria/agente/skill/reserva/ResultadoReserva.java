package com.up.peluqueria.agente.skill.reserva;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Resultado de intentar reservar un turno. Es una jerarquía cerrada ({@code sealed}):
 * el compilador obliga a manejar los dos casos en un {@code switch}.
 */
public sealed interface ResultadoReserva {

    /**
     * El turno quedó reservado.
     */
    record Confirmada(Long turnoId, String peluquero, LocalDate fecha, LocalTime hora) implements ResultadoReserva {
    }

    /**
     * La API rechazó el turno (por ejemplo, porque otro cliente lo reservó un instante antes).
     */
    record Rechazada(String motivo) implements ResultadoReserva {
    }
}
