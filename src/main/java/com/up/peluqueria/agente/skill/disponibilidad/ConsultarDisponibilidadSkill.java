package com.up.peluqueria.agente.skill.disponibilidad;

import com.up.peluqueria.agente.definicion.TipoSkill;
import com.up.peluqueria.agente.dominio.DirectorioPeluqueros;
import com.up.peluqueria.agente.dominio.ReglasAgenda;
import com.up.peluqueria.agente.skill.Skill;
import com.up.peluqueria.dto.response.TurnoResponseDTO;
import com.up.peluqueria.service.TurnoService;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Skill {@code consultar-disponibilidad}: calcula los horarios libres de un día.
 *
 * <p>Determinista y de solo lectura: horarios de atención ({@link ReglasAgenda}) menos los
 * turnos ya reservados ({@link TurnoService}). Contrato en
 * {@code resources/agente/skills/consultar-disponibilidad/SKILL.md}.</p>
 */
@Slf4j
@Component
public class ConsultarDisponibilidadSkill implements Skill {

    public static final String NOMBRE = "consultar-disponibilidad";

    private final TurnoService turnoService;
    private final DirectorioPeluqueros directorio;
    private final ReglasAgenda reglas;

    public ConsultarDisponibilidadSkill(TurnoService turnoService, DirectorioPeluqueros directorio, ReglasAgenda reglas) {
        this.turnoService = turnoService;
        this.directorio = directorio;
        this.reglas = reglas;
    }

    @Override
    public String nombre() {
        return NOMBRE;
    }

    @Override
    public TipoSkill tipo() {
        return TipoSkill.DETERMINISTA;
    }

    /**
     * @param fecha     día a consultar
     * @param peluquero peluquero a consultar; si es {@code null}, se consultan todos
     * @return los horarios libres de cada peluquero, en el orden en que están registrados
     */
    public List<Disponibilidad> ejecutar(LocalDate fecha, @Nullable String peluquero) {
        List<String> peluqueros = peluquero == null ? directorio.nombres() : List.of(peluquero);
        List<LocalTime> horariosDelDia = reglas.horariosDelDia(fecha);
        Map<String, Set<LocalTime>> ocupados = ocupadosPorPeluquero(fecha);

        List<Disponibilidad> resultado = peluqueros.stream()
                .map(nombre -> new Disponibilidad(nombre, fecha, horariosDelDia.stream()
                        .filter(hora -> !ocupados.getOrDefault(nombre.toLowerCase(), Set.of()).contains(hora))
                        .toList()))
                .toList();

        resultado.forEach(d -> log.info("[SKILL {}] {} {} → {} horarios libres",
                NOMBRE, d.peluquero(), fecha, d.horariosLibres().size()));
        return resultado;
    }

    /**
     * @return horarios libres de un peluquero en una fecha
     */
    public List<LocalTime> horariosLibres(String peluquero, LocalDate fecha) {
        return ejecutar(fecha, peluquero).get(0).horariosLibres();
    }

    private Map<String, Set<LocalTime>> ocupadosPorPeluquero(LocalDate fecha) {
        Map<String, Set<LocalTime>> ocupados = new HashMap<>();
        for (TurnoResponseDTO turno : turnoService.listarReservadosPorFecha(fecha.toString())) {
            try {
                ocupados.computeIfAbsent(turno.getPeluquero().getName().toLowerCase(), k -> new HashSet<>())
                        .add(LocalTime.parse(turno.getHora()));
            } catch (DateTimeParseException e) {
                log.warn("[SKILL {}] se ignora el turno #{} con hora inválida '{}'", NOMBRE, turno.getId(), turno.getHora());
            }
        }
        return ocupados;
    }
}
