package com.up.peluqueria.agente.conversacion;

import com.up.peluqueria.agente.dominio.DirectorioPeluqueros;
import com.up.peluqueria.agente.dominio.ReglaIncumplida;
import com.up.peluqueria.agente.dominio.ReglasAgenda;
import com.up.peluqueria.agente.modelo.ModeloNoDisponibleException;
import com.up.peluqueria.agente.seguridad.GuardiaEntrada;
import com.up.peluqueria.agente.seguridad.ResultadoGuardia;
import com.up.peluqueria.agente.skill.RegistroSkills;
import com.up.peluqueria.agente.skill.SkillNoHabilitadaException;
import com.up.peluqueria.agente.skill.disponibilidad.ConsultarDisponibilidadSkill;
import com.up.peluqueria.agente.skill.disponibilidad.Disponibilidad;
import com.up.peluqueria.agente.skill.interpretar.ContextoInterpretacion;
import com.up.peluqueria.agente.skill.interpretar.Intencion;
import com.up.peluqueria.agente.skill.interpretar.InterpretarPedidoSkill;
import com.up.peluqueria.agente.skill.interpretar.PedidoInterpretado;
import com.up.peluqueria.agente.skill.interpretar.ProblemaDato;
import com.up.peluqueria.agente.skill.interpretar.ResultadoInterpretacion;
import com.up.peluqueria.agente.skill.interpretar.SalidaModeloInvalidaException;
import com.up.peluqueria.agente.skill.peluqueros.ListarPeluquerosSkill;
import com.up.peluqueria.agente.skill.reserva.ReservarTurnoSkill;
import com.up.peluqueria.agente.skill.reserva.ResultadoReserva;
import com.up.peluqueria.agente.tiempo.CalendarioTurnos;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Orquestador del agente: recibe cada mensaje del cliente y decide qué skills ejecutar.
 *
 * <p>Patrón <b>pipeline con verificación</b>:</p>
 * <ol>
 *   <li><b>Entender</b>: la skill {@code interpretar-pedido} (LLM) convierte el mensaje en intención y datos.</li>
 *   <li><b>Verificar</b>: los datos se combinan con el pedido en curso y se validan contra las reglas de la agenda.</li>
 *   <li><b>Actuar</b>: según la intención se ejecuta una skill determinista
 *       ({@code listar-peluqueros}, {@code consultar-disponibilidad} o {@code reservar-turno}).</li>
 *   <li><b>Responder</b>: el texto sale de plantillas ({@link RedactorRespuestas}), no del modelo.</li>
 * </ol>
 *
 * <p>La conversación es una máquina de estados ({@link EstadoConversacion}): el agente pide los
 * datos que faltan y solo reserva después de una confirmación explícita del cliente.</p>
 */
@Slf4j
@Service
public class AgenteRecepcionista {

    private static final int MAX_SUGERENCIAS_HORAS = 6;
    private static final int CANTIDAD_ALTERNATIVAS = 3;
    private static final int DIAS_SUGERIDOS = 3;
    private static final List<String> EJEMPLOS = List.of(
            "¿Quiénes atienden?", "¿Qué horarios hay mañana?", "Quiero un turno el sábado a las 10");
    private static final List<String> CONFIRMAR_O_CANCELAR = List.of("Sí, confirmo", "No");

    private final RegistroSkills registro;
    private final InterpretarPedidoSkill skillInterpretar;
    private final ListarPeluquerosSkill skillListar;
    private final ConsultarDisponibilidadSkill skillConsultar;
    private final ReservarTurnoSkill skillReservar;
    private final RepositorioSesiones sesiones;
    private final DirectorioPeluqueros directorio;
    private final ReglasAgenda reglas;
    private final CalendarioTurnos calendario;
    private final RedactorRespuestas redactor;
    private final GuardiaEntrada guardia;
    private final Clock reloj;

    public AgenteRecepcionista(RegistroSkills registro, InterpretarPedidoSkill skillInterpretar,
                               ListarPeluquerosSkill skillListar, ConsultarDisponibilidadSkill skillConsultar,
                               ReservarTurnoSkill skillReservar, RepositorioSesiones sesiones,
                               DirectorioPeluqueros directorio, ReglasAgenda reglas, CalendarioTurnos calendario,
                               RedactorRespuestas redactor, GuardiaEntrada guardia, Clock reloj) {
        this.guardia = guardia;
        this.registro = registro;
        this.skillInterpretar = skillInterpretar;
        this.skillListar = skillListar;
        this.skillConsultar = skillConsultar;
        this.skillReservar = skillReservar;
        this.sesiones = sesiones;
        this.directorio = directorio;
        this.reglas = reglas;
        this.calendario = calendario;
        this.redactor = redactor;
        this.reloj = reloj;
    }

    /**
     * Procesa un mensaje del cliente.
     *
     * @param sesionId        identificador de la conversación
     * @param mensajeOriginal texto del cliente tal como llegó
     * @return la respuesta del agente y el detalle de lo que hizo
     */
    public RespuestaAgente procesar(String sesionId, String mensajeOriginal) {
        SesionConversacion sesion = sesiones.buscar(sesionId)
                .orElseGet(() -> SesionConversacion.nueva(sesionId, Instant.now(reloj)));
        Interaccion ix = new Interaccion(sesion);

        // Capa 1 de seguridad: el mensaje se sanea y se revisa antes de llegar al modelo.
        ResultadoGuardia revision = guardia.revisar(mensajeOriginal);
        if (!revision.permitido()) {
            ix.intencion = Intencion.FUERA_DE_ALCANCE;
            ix.correcciones.add("mensaje bloqueado por la guardia de entrada: " + revision.motivo());
            ix.sugerencias.addAll(EJEMPLOS);
            return ix.responder(redactor.fueraDeAlcance(), sesion.estado(), sesion.pedido());
        }
        String mensaje = revision.mensajeSaneado();

        // Atajo determinista: "sí" / "no" a la pregunta de confirmación no pasa por el modelo.
        if (sesion.estado() == EstadoConversacion.ESPERANDO_CONFIRMACION) {
            Optional<Intencion> respuestaCorta = RespuestasRapidas.detectar(mensaje);
            if (respuestaCorta.isPresent()) {
                ix.intencion = respuestaCorta.get();
                ix.correcciones.add("respuesta corta reconocida sin consultar al modelo: " + respuestaCorta.get());
                return ix.intencion == Intencion.CONFIRMAR ? responderConfirmacion(ix) : responderCancelacion(ix);
            }
        }

        PedidoInterpretado pedido;
        try {
            pedido = interpretar(ix, mensaje);
        } catch (SkillNoHabilitadaException e) {
            return ix.responder(redactor.capacidadNoHabilitada("entender mensajes"), sesion.estado(), sesion.pedido());
        } catch (ModeloNoDisponibleException e) {
            log.error("[AGENTE] el modelo no está disponible: {}", e.getMessage());
            return ix.responder(redactor.errorTecnico(), sesion.estado(), sesion.pedido());
        } catch (SalidaModeloInvalidaException e) {
            log.warn("[AGENTE] el modelo devolvió una salida inválida: {}", e.getTextoRecibido());
            return ix.responder(redactor.noEntendi(), sesion.estado(), sesion.pedido());
        }

        ix.intencion = ajustarIntencion(pedido, sesion.pedido(), ix);
        return switch (ix.intencion) {
            case SALUDO -> responderSaludo(ix);
            case LISTAR_PELUQUEROS -> responderPeluqueros(ix);
            case CONSULTAR_DISPONIBILIDAD -> responderConsulta(ix, pedido);
            case RESERVAR_TURNO -> responderReserva(ix, pedido);
            case CONFIRMAR -> responderConfirmacion(ix);
            case CANCELAR -> responderCancelacion(ix);
            case FUERA_DE_ALCANCE -> responderFueraDeAlcance(ix);
        };
    }

    // ---------------------------------------------------------------- 1. Entender

    private PedidoInterpretado interpretar(Interaccion ix, String mensaje) {
        registro.verificarHabilitada(InterpretarPedidoSkill.NOMBRE);
        PedidoEnCurso enCurso = ix.sesion.pedido();
        ContextoInterpretacion contexto = new ContextoInterpretacion(
                directorio.nombres(), enCurso == null ? null : enCurso.comoContexto());

        ResultadoInterpretacion resultado = skillInterpretar.ejecutar(mensaje, contexto);
        ix.skill(InterpretarPedidoSkill.NOMBRE);
        ix.duracionModeloMs = resultado.telemetria().duracionMs();
        ix.correcciones.addAll(resultado.pedido().correcciones());
        return resultado.pedido();
    }

    /**
     * Corrige intenciones incoherentes con los datos del mensaje. Por ejemplo, "hola, con Tobias
     * a las 10" clasificado como SALUDO, o "sí, pero a las 11" clasificado como CONFIRMAR cuando
     * en realidad cambia la hora.
     */
    private Intencion ajustarIntencion(PedidoInterpretado pedido, @Nullable PedidoEnCurso enCurso, Interaccion ix) {
        Intencion detectada = pedido.intencion();
        boolean aportaDatos = pedido.peluquero() != null || pedido.fecha() != null || pedido.hora() != null
                || pedido.peluqueroMencionado() != null || pedido.tieneProblemas();
        if (!aportaDatos) {
            return detectada;
        }
        boolean corresponde = switch (detectada) {
            case SALUDO, CONFIRMAR -> true;
            case FUERA_DE_ALCANCE -> enCurso != null;
            default -> false;
        };
        if (!corresponde) {
            return detectada;
        }
        Intencion ajustada = enCurso != null ? enCurso.intencion() : Intencion.RESERVAR_TURNO;
        ix.correcciones.add("intención ajustada de " + detectada + " a " + ajustada + ": el mensaje aporta datos del pedido");
        return ajustada;
    }

    // ---------------------------------------------------------------- 2 y 3. Verificar y actuar

    private RespuestaAgente responderSaludo(Interaccion ix) {
        ix.sugerencias.addAll(EJEMPLOS);
        return ix.responder(redactor.saludo(), ix.sesion.estado(), ix.sesion.pedido());
    }

    private RespuestaAgente responderFueraDeAlcance(Interaccion ix) {
        log.info("[AGENTE] mensaje fuera de alcance: no se ejecuta ninguna skill");
        ix.sugerencias.addAll(EJEMPLOS);
        return ix.responder(redactor.fueraDeAlcance(), ix.sesion.estado(), ix.sesion.pedido());
    }

    private RespuestaAgente responderPeluqueros(Interaccion ix) {
        if (!registro.estaHabilitada(ListarPeluquerosSkill.NOMBRE)) {
            return ix.responder(redactor.capacidadNoHabilitada("informar quiénes atienden"),
                    ix.sesion.estado(), ix.sesion.pedido());
        }
        List<String> peluqueros = skillListar.ejecutar();
        ix.skill(ListarPeluquerosSkill.NOMBRE);
        ix.sugerencias.addAll(peluqueros);

        // La respuesta termina con "¿Con quién querés sacar turno?": se abre un pedido para recibir la respuesta.
        PedidoEnCurso pedido = ix.sesion.pedido();
        if (pedido == null && registro.estaHabilitada(ReservarTurnoSkill.NOMBRE)) {
            pedido = PedidoEnCurso.nuevo(Intencion.RESERVAR_TURNO);
        }
        EstadoConversacion estado = pedido == null ? EstadoConversacion.LIBRE : EstadoConversacion.RECOLECTANDO_DATOS;
        return ix.responder(redactor.peluqueros(peluqueros), estado, pedido);
    }

    private RespuestaAgente responderConsulta(Interaccion ix, PedidoInterpretado nuevo) {
        if (!registro.estaHabilitada(ConsultarDisponibilidadSkill.NOMBRE)) {
            return ix.responder(redactor.capacidadNoHabilitada("consultar horarios"), ix.sesion.estado(), ix.sesion.pedido());
        }
        PedidoEnCurso base = ix.sesion.pedido() == null
                ? PedidoEnCurso.nuevo(Intencion.CONSULTAR_DISPONIBILIDAD) : ix.sesion.pedido();
        PedidoEnCurso pedido = base.conIntencion(Intencion.CONSULTAR_DISPONIBILIDAD).combinar(nuevo);

        Optional<RespuestaAgente> problema = responderProblemas(ix, pedido, nuevo);
        if (problema.isPresent()) {
            return problema.get();
        }
        if (pedido.fecha() == null) {
            sugerirFechas(ix, calendario.hoy());
            return ix.responder(redactor.preguntarFechaConsulta(), EstadoConversacion.RECOLECTANDO_DATOS, pedido);
        }
        if (!reglas.atiende(pedido.fecha())) {
            return responderDiaSinAtencion(ix, pedido);
        }

        boolean puedeReservar = registro.estaHabilitada(ReservarTurnoSkill.NOMBRE);
        if (pedido.hora() != null && puedeReservar) {
            // "¿Tobias tiene algo el sábado a las 10?": se verifica ese horario como si fuera una reserva.
            return avanzarReserva(ix, pedido.conIntencion(Intencion.RESERVAR_TURNO), true);
        }

        List<Disponibilidad> disponibilidades = skillConsultar.ejecutar(pedido.fecha(), pedido.peluquero());
        ix.skill(ConsultarDisponibilidadSkill.NOMBRE);
        String texto = redactor.disponibilidad(pedido.fecha(), disponibilidades, puedeReservar);
        if (!puedeReservar) {
            return ix.responder(texto, EstadoConversacion.LIBRE, null);
        }

        // Queda abierto un pedido de reserva con lo consultado, para que el cliente solo elija el horario.
        PedidoEnCurso siguiente = pedido.conIntencion(Intencion.RESERVAR_TURNO).sinHora();
        if (disponibilidades.stream().noneMatch(Disponibilidad::tieneLibres)) {
            sugerirFechas(ix, pedido.fecha().plusDays(1));
            return ix.responder(texto, EstadoConversacion.RECOLECTANDO_DATOS, siguiente.sinFecha());
        }
        if (pedido.peluquero() != null) {
            ix.sugerencias.addAll(RedactorRespuestas.comoSugerencias(disponibilidades.get(0).horariosLibres(), MAX_SUGERENCIAS_HORAS));
        } else {
            disponibilidades.stream().filter(Disponibilidad::tieneLibres).map(Disponibilidad::peluquero).forEach(ix.sugerencias::add);
        }
        return ix.responder(texto, EstadoConversacion.RECOLECTANDO_DATOS, siguiente);
    }

    private RespuestaAgente responderReserva(Interaccion ix, PedidoInterpretado nuevo) {
        if (!registro.estaHabilitada(ReservarTurnoSkill.NOMBRE)) {
            return ix.responder(redactor.capacidadNoHabilitada("tomar reservas"), EstadoConversacion.LIBRE, null);
        }
        PedidoEnCurso base = ix.sesion.pedido() == null
                ? PedidoEnCurso.nuevo(Intencion.RESERVAR_TURNO) : ix.sesion.pedido();
        PedidoEnCurso pedido = base.conIntencion(Intencion.RESERVAR_TURNO).combinar(nuevo);

        Optional<RespuestaAgente> problema = responderProblemas(ix, pedido, nuevo);
        return problema.orElseGet(() -> avanzarReserva(ix, pedido, false));
    }

    /**
     * Avanza una reserva un paso: valida los datos acumulados contra las reglas de la agenda,
     * pide el primer dato que falte y, si está todo y el horario está libre, pide confirmación.
     *
     * @param desdeConsulta si el cliente preguntó por disponibilidad (cambia cómo se formula la confirmación)
     */
    private RespuestaAgente avanzarReserva(Interaccion ix, PedidoEnCurso pedido, boolean desdeConsulta) {
        if (pedido.fecha() != null && !reglas.atiende(pedido.fecha())) {
            return responderDiaSinAtencion(ix, pedido);
        }
        if (pedido.hora() != null) {
            Optional<ReglaIncumplida> regla = reglas.validarHora(pedido.fecha(), pedido.hora());
            if (regla.isPresent()) {
                List<LocalTime> candidatos = pedido.fecha() == null ? reglas.horariosDeAtencion()
                        : pedido.peluquero() == null ? reglas.horariosDelDia(pedido.fecha())
                        : horariosLibres(ix, pedido.peluquero(), pedido.fecha());
                List<LocalTime> alternativas = ReglasAgenda.masCercanos(candidatos, pedido.hora(), CANTIDAD_ALTERNATIVAS);
                ix.sugerencias.addAll(RedactorRespuestas.comoSugerencias(alternativas, CANTIDAD_ALTERNATIVAS));
                return ix.responder(redactor.horaNoValida(regla.get(), pedido.hora(), alternativas),
                        EstadoConversacion.RECOLECTANDO_DATOS, pedido.sinHora());
            }
        }
        if (pedido.peluquero() == null) {
            return preguntarPeluquero(ix, pedido);
        }
        if (pedido.fecha() == null) {
            sugerirFechas(ix, calendario.hoy());
            return ix.responder(redactor.preguntarFecha(pedido.peluquero()), EstadoConversacion.RECOLECTANDO_DATOS, pedido);
        }

        List<LocalTime> libres = horariosLibres(ix, pedido.peluquero(), pedido.fecha());
        if (pedido.hora() == null) {
            if (libres.isEmpty()) {
                sugerirFechas(ix, pedido.fecha().plusDays(1));
                return ix.responder(redactor.sinHorariosLibres(pedido.peluquero(), pedido.fecha()),
                        EstadoConversacion.RECOLECTANDO_DATOS, pedido.sinFecha());
            }
            ix.sugerencias.addAll(RedactorRespuestas.comoSugerencias(libres, MAX_SUGERENCIAS_HORAS));
            return ix.responder(redactor.preguntarHora(pedido.peluquero(), pedido.fecha(), libres),
                    EstadoConversacion.RECOLECTANDO_DATOS, pedido);
        }
        if (!libres.contains(pedido.hora())) {
            List<LocalTime> alternativas = ReglasAgenda.masCercanos(libres, pedido.hora(), CANTIDAD_ALTERNATIVAS);
            ix.sugerencias.addAll(RedactorRespuestas.comoSugerencias(alternativas, CANTIDAD_ALTERNATIVAS));
            return ix.responder(redactor.horarioOcupado(pedido.peluquero(), pedido.fecha(), pedido.hora(), alternativas),
                    EstadoConversacion.RECOLECTANDO_DATOS, pedido.sinHora());
        }

        ix.sugerencias.addAll(CONFIRMAR_O_CANCELAR);
        String texto = desdeConsulta ? redactor.libreParaReservar(pedido) : redactor.confirmar(pedido);
        return ix.responder(texto, EstadoConversacion.ESPERANDO_CONFIRMACION, pedido);
    }

    private RespuestaAgente preguntarPeluquero(Interaccion ix, PedidoEnCurso pedido) {
        if (pedido.fecha() != null && pedido.hora() != null && registro.estaHabilitada(ConsultarDisponibilidadSkill.NOMBRE)) {
            // Si ya eligió día y hora, se ofrecen solo los peluqueros libres en ese horario.
            List<String> libres = skillConsultar.ejecutar(pedido.fecha(), null).stream()
                    .filter(d -> d.horariosLibres().contains(pedido.hora()))
                    .map(Disponibilidad::peluquero)
                    .toList();
            ix.skill(ConsultarDisponibilidadSkill.NOMBRE);
            if (libres.isEmpty()) {
                return ix.responder(redactor.nadieLibreEnHorario(pedido.fecha(), pedido.hora()),
                        EstadoConversacion.RECOLECTANDO_DATOS, pedido.sinHora());
            }
            ix.sugerencias.addAll(libres);
            return ix.responder(redactor.preguntarPeluqueroEnHorario(pedido.fecha(), pedido.hora(), libres),
                    EstadoConversacion.RECOLECTANDO_DATOS, pedido);
        }
        List<String> peluqueros = directorio.nombres();
        ix.sugerencias.addAll(peluqueros);
        return ix.responder(redactor.preguntarPeluquero(peluqueros), EstadoConversacion.RECOLECTANDO_DATOS, pedido);
    }

    private RespuestaAgente responderConfirmacion(Interaccion ix) {
        PedidoEnCurso pedido = ix.sesion.pedido();
        boolean hayAlgoQueConfirmar = ix.sesion.estado() == EstadoConversacion.ESPERANDO_CONFIRMACION
                && pedido != null && pedido.estaCompleto();
        if (!hayAlgoQueConfirmar) {
            // "Dale" mientras faltan datos: se sigue pidiendo lo que falta.
            if (pedido != null && pedido.intencion() == Intencion.RESERVAR_TURNO) {
                return avanzarReserva(ix, pedido, false);
            }
            return ix.responder(redactor.nadaQueConfirmar(), EstadoConversacion.LIBRE, null);
        }
        if (!registro.estaHabilitada(ReservarTurnoSkill.NOMBRE)) {
            return ix.responder(redactor.capacidadNoHabilitada("tomar reservas"), EstadoConversacion.LIBRE, null);
        }
        // El horario pudo haber pasado mientras el cliente decidía.
        if (reglas.validarHora(pedido.fecha(), pedido.hora()).isPresent()) {
            return avanzarReserva(ix, pedido, false);
        }

        ResultadoReserva resultado = skillReservar.ejecutar(pedido.peluquero(), pedido.fecha(), pedido.hora());
        ix.skill(ReservarTurnoSkill.NOMBRE);
        return switch (resultado) {
            case ResultadoReserva.Confirmada confirmada -> {
                ix.turnoReservadoId = confirmada.turnoId();
                yield ix.responder(redactor.reservaConfirmada(confirmada), EstadoConversacion.LIBRE, null);
            }
            case ResultadoReserva.Rechazada(String motivo) -> {
                List<LocalTime> alternativas = ReglasAgenda.masCercanos(
                        horariosLibres(ix, pedido.peluquero(), pedido.fecha()), pedido.hora(), CANTIDAD_ALTERNATIVAS);
                ix.sugerencias.addAll(RedactorRespuestas.comoSugerencias(alternativas, CANTIDAD_ALTERNATIVAS));
                yield ix.responder(redactor.reservaRechazada(motivo, alternativas),
                        EstadoConversacion.RECOLECTANDO_DATOS, pedido.sinHora());
            }
        };
    }

    private RespuestaAgente responderCancelacion(Interaccion ix) {
        String texto = ix.sesion.pedido() == null ? redactor.nadaQueCancelar() : redactor.pedidoCancelado();
        return ix.responder(texto, EstadoConversacion.LIBRE, null);
    }

    // ---------------------------------------------------------------- Problemas y auxiliares

    /**
     * Informa el primer problema de los datos del mensaje (peluquero inexistente, fecha pasada, etc.)
     * y descarta el dato inválido del pedido.
     */
    private Optional<RespuestaAgente> responderProblemas(Interaccion ix, PedidoEnCurso pedido, PedidoInterpretado nuevo) {
        Set<ProblemaDato> problemas = nuevo.problemas();
        EstadoConversacion recolectando = EstadoConversacion.RECOLECTANDO_DATOS;

        if (problemas.contains(ProblemaDato.PELUQUERO_DESCONOCIDO)) {
            List<String> peluqueros = directorio.nombres();
            ix.sugerencias.addAll(peluqueros);
            return Optional.of(ix.responder(
                    redactor.peluqueroDesconocido(nuevo.peluqueroMencionado(), peluqueros), recolectando, pedido));
        }
        if (problemas.contains(ProblemaDato.FECHA_PASADA)) {
            sugerirFechas(ix, calendario.hoy());
            return Optional.of(ix.responder(redactor.fechaPasada(), recolectando, pedido.sinFecha()));
        }
        if (problemas.contains(ProblemaDato.FECHA_FUERA_DE_RANGO)) {
            sugerirFechas(ix, calendario.hoy());
            return Optional.of(ix.responder(redactor.fechaFueraDeRango(calendario.ultimoDia()), recolectando, pedido.sinFecha()));
        }
        if (problemas.contains(ProblemaDato.FECHA_INVALIDA)) {
            sugerirFechas(ix, calendario.hoy());
            return Optional.of(ix.responder(redactor.fechaNoEntendida(), recolectando, pedido.sinFecha()));
        }
        if (problemas.contains(ProblemaDato.HORA_INVALIDA)) {
            return Optional.of(ix.responder(redactor.horaNoEntendida(), recolectando, pedido.sinHora()));
        }
        return Optional.empty();
    }

    private RespuestaAgente responderDiaSinAtencion(Interaccion ix, PedidoEnCurso pedido) {
        LocalDate proximo = reglas.proximoDiaDeAtencion(pedido.fecha());
        sugerirFechas(ix, proximo);
        return ix.responder(redactor.diaSinAtencion(pedido.fecha(), proximo),
                EstadoConversacion.RECOLECTANDO_DATOS, pedido.sinFecha());
    }

    /**
     * Horarios libres de un peluquero. Si la skill de disponibilidad no está habilitada, se usan
     * todos los horarios de atención: la API igual rechaza un horario ocupado al reservar.
     */
    private List<LocalTime> horariosLibres(Interaccion ix, String peluquero, LocalDate fecha) {
        if (!registro.estaHabilitada(ConsultarDisponibilidadSkill.NOMBRE)) {
            return reglas.horariosDelDia(fecha);
        }
        ix.skill(ConsultarDisponibilidadSkill.NOMBRE);
        return skillConsultar.horariosLibres(peluquero, fecha);
    }

    /**
     * Sugiere los próximos días de atención con horarios disponibles, dentro de la ventana del calendario.
     */
    private void sugerirFechas(Interaccion ix, LocalDate desde) {
        LocalDate fecha = desde.isBefore(calendario.hoy()) ? calendario.hoy() : desde;
        int agregadas = 0;
        while (agregadas < DIAS_SUGERIDOS && !fecha.isAfter(calendario.ultimoDia())) {
            if (!reglas.horariosDelDia(fecha).isEmpty()) {
                ix.sugerencias.add(calendario.describir(fecha));
                agregadas++;
            }
            fecha = fecha.plusDays(1);
        }
    }

    /**
     * Lo que va pasando mientras se procesa un mensaje: skills ejecutadas, correcciones y sugerencias.
     * Al responder, guarda el nuevo estado de la sesión y arma la {@link RespuestaAgente}.
     */
    private final class Interaccion {

        private final SesionConversacion sesion;
        private final List<String> skills = new ArrayList<>();
        private final List<String> correcciones = new ArrayList<>();
        private final List<String> sugerencias = new ArrayList<>();
        private @Nullable Intencion intencion;
        private @Nullable Long turnoReservadoId;
        private @Nullable Long duracionModeloMs;

        private Interaccion(SesionConversacion sesion) {
            this.sesion = sesion;
        }

        private void skill(String nombre) {
            if (!skills.contains(nombre)) {
                skills.add(nombre);
            }
        }

        private RespuestaAgente responder(String texto, EstadoConversacion estado, @Nullable PedidoEnCurso pedido) {
            EstadoConversacion estadoFinal = pedido == null ? EstadoConversacion.LIBRE : estado;
            sesiones.guardar(new SesionConversacion(sesion.id(), estadoFinal, pedido, Instant.now(reloj)));

            log.info("[AGENTE] sesion={} | intencion={} | skills={} | estado={} | pedido={}",
                    RepositorioSesionesEnMemoria.abreviar(sesion.id()), intencion, skills, estadoFinal, pedido);
            return new RespuestaAgente(sesion.id(), texto, estadoFinal, intencion, pedido,
                    List.copyOf(skills), List.copyOf(correcciones), sugerencias.stream().distinct().toList(),
                    turnoReservadoId, duracionModeloMs);
        }
    }
}
