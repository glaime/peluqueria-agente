package com.up.peluqueria.agente.skill.disponibilidad;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Horarios libres de un peluquero en una fecha.
 *
 * @param peluquero      nombre del peluquero
 * @param fecha          fecha consultada
 * @param horariosLibres horarios sin turno reservado, en orden cronológico
 */
public record Disponibilidad(String peluquero, LocalDate fecha, List<LocalTime> horariosLibres) {

    public Disponibilidad {
        horariosLibres = List.copyOf(horariosLibres);
    }

    public boolean tieneLibres() {
        return !horariosLibres.isEmpty();
    }
}
