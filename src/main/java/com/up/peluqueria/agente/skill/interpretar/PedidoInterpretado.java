package com.up.peluqueria.agente.skill.interpretar;

import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Pedido del cliente ya validado y normalizado.
 *
 * @param intencion           qué quiere hacer el cliente
 * @param peluquero           nombre de un peluquero que existe en la peluquería
 * @param peluqueroMencionado nombre que dijo el cliente y no corresponde a ningún peluquero
 * @param fecha               fecha pedida
 * @param hora                hora pedida
 * @param problemas           datos que el cliente tiene que corregir
 * @param correcciones        ajustes que hizo el código sobre lo que propuso el modelo (guardrails)
 */
public record PedidoInterpretado(
        Intencion intencion,
        @Nullable String peluquero,
        @Nullable String peluqueroMencionado,
        @Nullable LocalDate fecha,
        @Nullable LocalTime hora,
        Set<ProblemaDato> problemas,
        List<String> correcciones) {

    public PedidoInterpretado {
        problemas = Collections.unmodifiableSet(new TreeSet<>(problemas));
        correcciones = List.copyOf(correcciones);
    }

    public boolean tieneProblemas() {
        return !problemas.isEmpty();
    }
}
