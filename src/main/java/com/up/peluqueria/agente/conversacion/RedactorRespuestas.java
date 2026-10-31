package com.up.peluqueria.agente.conversacion;

import com.up.peluqueria.agente.dominio.ReglaIncumplida;
import com.up.peluqueria.agente.skill.disponibilidad.Disponibilidad;
import com.up.peluqueria.agente.skill.reserva.ResultadoReserva;
import com.up.peluqueria.agente.tiempo.CalendarioTurnos;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Redacta las respuestas del agente a partir de plantillas.
 *
 * <p>Decisión de diseño: el modelo <b>entiende</b> pero no <b>redacta</b>. Si el texto lo escribiera
 * el modelo, podría afirmar "tu turno quedó reservado" sin que la reserva exista (alucinación).
 * Con plantillas, cada afirmación sale de un dato real: un turno solo se informa como reservado
 * si {@code reservar-turno} devolvió su id.</p>
 */
@Component
public class RedactorRespuestas {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final int MAX_HORARIOS_EN_TEXTO = 8;
    private static final String HORARIO_DE_ATENCION = "de martes a sábado, de 09:00 a 17:30";

    private final CalendarioTurnos calendario;

    public RedactorRespuestas(CalendarioTurnos calendario) {
        this.calendario = calendario;
    }

    public String saludo() {
        return "¡Hola! Soy la recepcionista virtual de la peluquería. Puedo decirte quiénes atienden, "
                + "mostrarte los horarios libres y reservarte un turno. Por ejemplo: «quiero un turno con Tobias el sábado a las 10».";
    }

    public String fueraDeAlcance() {
        return "Solo puedo ayudarte con turnos de la peluquería: decirte quiénes atienden, mostrarte horarios libres "
                + "y reservar un turno. No puedo modificar ni cancelar turnos ya reservados.";
    }

    public String peluqueros(List<String> nombres) {
        return "Atienden " + enumerar(nombres) + ", " + HORARIO_DE_ATENCION + ". ¿Con quién querés sacar turno?";
    }

    public String peluqueroDesconocido(String mencionado, List<String> nombres) {
        return mencionado + " no trabaja en la peluquería. Atienden " + enumerar(nombres) + ". ¿Con quién querés el turno?";
    }

    public String preguntarPeluquero(List<String> nombres) {
        return "¿Con quién querés el turno? Atienden " + enumerar(nombres) + ".";
    }

    public String preguntarPeluqueroEnHorario(LocalDate fecha, LocalTime hora, List<String> libres) {
        String quienes = libres.size() == 1 ? "solo tiene lugar " + libres.get(0) : "tienen lugar " + enumerar(libres);
        return capitalizar(cuando(fecha)) + " a las " + hora(hora) + " " + quienes + ". ¿Con quién querés el turno?";
    }

    public String nadieLibreEnHorario(LocalDate fecha, LocalTime hora) {
        return capitalizar(cuando(fecha)) + " a las " + hora(hora) + " no hay ningún peluquero libre. ¿Probamos otro horario?";
    }

    public String preguntarFecha(String peluquero) {
        return "¿Para qué día querés el turno con " + peluquero + "? Atendemos " + HORARIO_DE_ATENCION + ".";
    }

    public String preguntarFechaConsulta() {
        return "¿Para qué día querés consultar los horarios libres?";
    }

    public String preguntarHora(String peluquero, LocalDate fecha, List<LocalTime> libres) {
        return peluquero + " tiene libre " + cuando(fecha) + ": " + horas(libres) + ". ¿A qué hora te queda bien?";
    }

    public String sinHorariosLibres(String peluquero, LocalDate fecha) {
        return peluquero + " no tiene horarios libres " + cuando(fecha) + ". ¿Probamos otro día?";
    }

    public String diaSinAtencion(LocalDate fecha, LocalDate proximoDia) {
        return "Los " + plural(calendario.nombreDia(fecha)) + " no atendemos (abrimos " + HORARIO_DE_ATENCION + "). "
                + "El próximo día disponible es " + cuando(proximoDia) + ". ¿Te sirve?";
    }

    public String fechaPasada() {
        return "Esa fecha ya pasó. ¿Para qué día querés el turno?";
    }

    public String fechaFueraDeRango(LocalDate ultimoDia) {
        return "Por ahora doy turnos hasta " + cuando(ultimoDia) + ". ¿Querés elegir un día antes?";
    }

    public String fechaNoEntendida() {
        return "No entendí la fecha. Podés decirme, por ejemplo, «el sábado» o «31/10».";
    }

    public String horaNoEntendida() {
        return "No entendí la hora. Podés decirme, por ejemplo, «a las 10» o «10:30».";
    }

    public String horaNoValida(ReglaIncumplida regla, LocalTime pedida, List<LocalTime> alternativas) {
        String motivo = switch (regla) {
            case FUERA_DE_HORARIO -> "A las " + hora(pedida) + " no hay turnos: atendemos de 09:00 a 17:30.";
            case MINUTOS_NO_PERMITIDOS -> "Los turnos empiezan en punto o y media, así que " + hora(pedida) + " no está disponible.";
            case HORARIO_PASADO -> "Las " + hora(pedida) + " de hoy ya pasaron.";
            case DIA_SIN_ATENCION -> "Ese día no atendemos.";
        };
        String opciones = alternativas.isEmpty() ? "" : " Te puedo ofrecer: " + horas(alternativas) + ".";
        return motivo + opciones + " ¿Qué horario preferís?";
    }

    public String horarioOcupado(String peluquero, LocalDate fecha, LocalTime pedida, List<LocalTime> alternativas) {
        String texto = peluquero + " ya tiene ocupado " + cuando(fecha) + " a las " + hora(pedida) + ".";
        return alternativas.isEmpty()
                ? texto + " Ese día no le quedan horarios libres. ¿Probamos otro día?"
                : texto + " Tiene libre: " + horas(alternativas) + ". ¿Te sirve alguno?";
    }

    public String confirmar(PedidoEnCurso pedido) {
        return "Te reservo un turno con " + pedido.peluquero() + " " + cuando(pedido.fecha())
                + " a las " + hora(pedido.hora()) + ". ¿Confirmás?";
    }

    public String libreParaReservar(PedidoEnCurso pedido) {
        return "¡Sí! " + pedido.peluquero() + " tiene libre " + cuando(pedido.fecha()) + " a las " + hora(pedido.hora())
                + ". ¿Querés que te lo reserve?";
    }

    public String reservaConfirmada(ResultadoReserva.Confirmada reserva) {
        return "¡Listo! Reservé tu turno con " + reserva.peluquero() + " " + cuando(reserva.fecha()) + " a las "
                + hora(reserva.hora()) + " (turno #" + reserva.turnoId() + "). ¡Te esperamos!";
    }

    public String reservaRechazada(String motivo, List<LocalTime> alternativas) {
        String texto = "No pude reservar el turno: " + motivo + ".";
        return alternativas.isEmpty()
                ? texto + " ¿Probamos otro día?"
                : texto + " Todavía hay lugar a las " + horas(alternativas) + ". ¿Te sirve alguno?";
    }

    public String pedidoCancelado() {
        return "Listo, descarté el pedido. Los turnos que ya tenías reservados no se modifican. ¿Te ayudo con algo más?";
    }

    public String nadaQueCancelar() {
        return "No hay ningún pedido en curso. ¿Querés sacar un turno?";
    }

    public String nadaQueConfirmar() {
        return "No tengo ningún turno pendiente de confirmar. ¿Querés sacar uno?";
    }

    public String disponibilidad(LocalDate fecha, List<Disponibilidad> disponibilidades, boolean puedeReservar) {
        String cierre = puedeReservar ? " ¿Querés reservar alguno?" : "";
        if (disponibilidades.stream().noneMatch(Disponibilidad::tieneLibres)) {
            return capitalizar(cuando(fecha)) + " no quedan horarios libres. ¿Probamos otro día?";
        }
        if (disponibilidades.size() == 1) {
            Disponibilidad unica = disponibilidades.get(0);
            return unica.peluquero() + " tiene libre " + cuando(fecha) + ": " + horas(unica.horariosLibres()) + "." + cierre;
        }
        StringBuilder texto = new StringBuilder("Horarios libres ").append(cuando(fecha)).append(':');
        for (Disponibilidad d : disponibilidades) {
            texto.append("\n• ").append(d.peluquero()).append(": ")
                    .append(d.tieneLibres() ? horas(d.horariosLibres()) : "sin horarios libres");
        }
        return texto.append(puedeReservar ? "\n¿Con quién y a qué hora querés el turno?" : "").toString();
    }

    public String capacidadNoHabilitada(String capacidad) {
        return "Por ahora no puedo " + capacidad + " por este medio.";
    }

    public String errorTecnico() {
        return "Estoy teniendo un problema técnico para entender tu mensaje. Probá de nuevo en un momento.";
    }

    public String noEntendi() {
        return "Perdón, no entendí tu mensaje. ¿Podés escribirlo de otra forma?";
    }

    /**
     * Referencia al día para usar dentro de una frase: "hoy jueves 29/10", "mañana viernes 30/10"
     * o "el sábado 31/10".
     */
    public String cuando(LocalDate fecha) {
        String descripcion = calendario.describirParaCliente(fecha);
        return descripcion.startsWith("hoy") || descripcion.startsWith("mañana") ? descripcion : "el " + descripcion;
    }

    public static String hora(LocalTime hora) {
        return hora.format(HORA);
    }

    /**
     * @return los horarios separados por comas; si son muchos, los primeros y cuántos más hay
     */
    public static String horas(List<LocalTime> horarios) {
        List<String> textos = horarios.stream().limit(MAX_HORARIOS_EN_TEXTO).map(RedactorRespuestas::hora).toList();
        int restantes = horarios.size() - textos.size();
        return restantes > 0
                ? String.join(", ", textos) + " y " + restantes + " horarios más"
                : enumerar(textos);
    }

    /**
     * @return "A", "A y B" o "A, B y C"
     */
    public static String enumerar(List<String> elementos) {
        if (elementos.isEmpty()) {
            return "";
        }
        if (elementos.size() == 1) {
            return elementos.get(0);
        }
        return String.join(", ", elementos.subList(0, elementos.size() - 1)) + " y " + elementos.get(elementos.size() - 1);
    }

    /**
     * @return las horas como texto para mostrar como respuestas rápidas
     */
    public static List<String> comoSugerencias(List<LocalTime> horarios, int maximo) {
        return horarios.stream().limit(maximo).map(RedactorRespuestas::hora).toList();
    }

    private static String plural(String dia) {
        return dia.endsWith("s") ? dia : dia + "s";
    }

    private static String capitalizar(String texto) {
        return texto.isEmpty() ? texto : Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }
}
