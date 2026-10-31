package com.up.peluqueria.agente.dominio;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Reglas de la agenda de la peluquería: qué días se atiende y en qué horarios hay turnos.
 *
 * <p>Son las mismas reglas que valida {@code TurnoService} al crear un turno. El agente las usa
 * antes, para avisarle al cliente apenas pide algo imposible ("los lunes no atendemos") y para
 * calcular horarios libres. {@code TurnoService} sigue siendo la validación final
 * (defensa en profundidad: si el agente se equivoca, la API igual rechaza el turno).</p>
 */
@Component
public class ReglasAgenda {

    public static final LocalTime APERTURA = LocalTime.of(9, 0);
    public static final LocalTime ULTIMO_TURNO = LocalTime.of(17, 30);
    public static final int MINUTOS_POR_TURNO = 30;

    private static final Set<DayOfWeek> DIAS_SIN_ATENCION = EnumSet.of(DayOfWeek.SUNDAY, DayOfWeek.MONDAY);

    /** 09:00, 09:30, ..., 17:30: los horarios de turno de un día de atención. */
    private static final List<LocalTime> HORARIOS_DE_ATENCION = Stream
            .iterate(APERTURA, hora -> !hora.isAfter(ULTIMO_TURNO), hora -> hora.plusMinutes(MINUTOS_POR_TURNO))
            .toList();

    private final Clock reloj;

    public ReglasAgenda(Clock reloj) {
        this.reloj = reloj;
    }

    /**
     * @return {@code true} si la peluquería atiende ese día (martes a sábado)
     */
    public boolean atiende(LocalDate fecha) {
        return !DIAS_SIN_ATENCION.contains(fecha.getDayOfWeek());
    }

    /**
     * @return la regla incumplida por la fecha, o vacío si es un día de atención
     */
    public Optional<ReglaIncumplida> validarFecha(LocalDate fecha) {
        return atiende(fecha) ? Optional.empty() : Optional.of(ReglaIncumplida.DIA_SIN_ATENCION);
    }

    /**
     * @param fecha fecha del turno, si ya se conoce (para detectar horarios pasados de hoy)
     * @param hora  hora pedida
     * @return la regla incumplida por la hora, o vacío si es un horario de turno válido
     */
    public Optional<ReglaIncumplida> validarHora(@Nullable LocalDate fecha, LocalTime hora) {
        if (hora.isBefore(APERTURA) || hora.isAfter(ULTIMO_TURNO)) {
            return Optional.of(ReglaIncumplida.FUERA_DE_HORARIO);
        }
        if (hora.getMinute() % MINUTOS_POR_TURNO != 0 || hora.getSecond() != 0) {
            return Optional.of(ReglaIncumplida.MINUTOS_NO_PERMITIDOS);
        }
        LocalDateTime ahora = LocalDateTime.now(reloj);
        if (fecha != null && fecha.equals(ahora.toLocalDate()) && !hora.isAfter(ahora.toLocalTime())) {
            return Optional.of(ReglaIncumplida.HORARIO_PASADO);
        }
        return Optional.empty();
    }

    /**
     * Horarios de turno de un día: 09:00, 09:30, ..., 17:30. Si la fecha es hoy, solo los que
     * todavía no pasaron. Si ese día no se atiende, la lista está vacía.
     */
    public List<LocalTime> horariosDelDia(LocalDate fecha) {
        if (!atiende(fecha)) {
            return List.of();
        }
        return HORARIOS_DE_ATENCION.stream().filter(hora -> validarHora(fecha, hora).isEmpty()).toList();
    }

    /**
     * @return los horarios de turno de un día de atención cualquiera (09:00 a 17:30, cada 30 minutos)
     */
    public List<LocalTime> horariosDeAtencion() {
        return HORARIOS_DE_ATENCION;
    }

    /**
     * @return el primer día de atención a partir de la fecha dada (inclusive)
     */
    public LocalDate proximoDiaDeAtencion(LocalDate desde) {
        LocalDate fecha = desde;
        while (!atiende(fecha)) {
            fecha = fecha.plusDays(1);
        }
        return fecha;
    }

    /**
     * @param candidatos horarios entre los que elegir
     * @param referencia hora pedida por el cliente
     * @param cantidad   máximo de horarios a devolver
     * @return los horarios más cercanos a la referencia, ordenados cronológicamente
     */
    public static List<LocalTime> masCercanos(List<LocalTime> candidatos, LocalTime referencia, int cantidad) {
        return candidatos.stream()
                .sorted(Comparator.comparingLong((LocalTime h) -> Math.abs(Duration.between(referencia, h).toMinutes()))
                        .thenComparing(Comparator.naturalOrder()))
                .limit(cantidad)
                .sorted()
                .toList();
    }
}
