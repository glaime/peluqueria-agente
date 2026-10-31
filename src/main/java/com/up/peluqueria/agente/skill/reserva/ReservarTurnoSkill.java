package com.up.peluqueria.agente.skill.reserva;

import com.up.peluqueria.agente.definicion.TipoSkill;
import com.up.peluqueria.agente.dominio.DirectorioPeluqueros;
import com.up.peluqueria.agente.skill.Skill;
import com.up.peluqueria.dto.request.TurnoRequestDTO;
import com.up.peluqueria.dto.response.TurnoResponseDTO;
import com.up.peluqueria.entity.EstadoTurno;
import com.up.peluqueria.exception.BadRequestException;
import com.up.peluqueria.exception.ConflictException;
import com.up.peluqueria.exception.ResourceNotFoundException;
import com.up.peluqueria.service.TurnoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Skill {@code reservar-turno}: crea un turno RESERVADO.
 *
 * <p>Es la única skill que escribe datos, y solo puede crear: no existe en el agente ninguna
 * skill para modificar o eliminar turnos (mínimo privilegio). El agente la ejecuta únicamente
 * después de que el cliente confirma. El turno se crea con {@link TurnoService}, que vuelve a
 * validar las reglas de negocio y los conflictos de horario. Contrato en
 * {@code resources/agente/skills/reservar-turno/SKILL.md}.</p>
 */
@Slf4j
@Component
public class ReservarTurnoSkill implements Skill {

    public static final String NOMBRE = "reservar-turno";

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final TurnoService turnoService;
    private final DirectorioPeluqueros directorio;

    public ReservarTurnoSkill(TurnoService turnoService, DirectorioPeluqueros directorio) {
        this.turnoService = turnoService;
        this.directorio = directorio;
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
     * @param peluquero nombre de un peluquero existente
     * @param fecha     fecha del turno
     * @param hora      hora del turno
     * @return {@link ResultadoReserva.Confirmada} con el id del turno, o {@link ResultadoReserva.Rechazada} con el motivo
     */
    public ResultadoReserva ejecutar(String peluquero, LocalDate fecha, LocalTime hora) {
        Optional<Long> peluqueroId = directorio.idDe(peluquero);
        if (peluqueroId.isEmpty()) {
            log.warn("[SKILL {}] rechazada: el peluquero '{}' no existe", NOMBRE, peluquero);
            return new ResultadoReserva.Rechazada("el peluquero " + peluquero + " no existe");
        }

        TurnoRequestDTO solicitud = new TurnoRequestDTO(
                fecha.toString(), hora.format(HORA), EstadoTurno.RESERVADO.name(), peluqueroId.get());
        try {
            TurnoResponseDTO turno = turnoService.crear(solicitud);
            log.info("[SKILL {}] turno #{} reservado: {} {} {}", NOMBRE, turno.getId(), peluquero, fecha, hora.format(HORA));
            return new ResultadoReserva.Confirmada(turno.getId(), peluquero, fecha, hora);
        } catch (BadRequestException | ConflictException | ResourceNotFoundException e) {
            log.warn("[SKILL {}] rechazada por la API: {}", NOMBRE, e.getMessage());
            return new ResultadoReserva.Rechazada(e.getMessage());
        }
    }
}
