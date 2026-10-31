package com.up.peluqueria.agente.tiempo;

import com.up.peluqueria.agente.config.AgenteProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

/**
 * Ventana de días en la que se pueden pedir turnos, a partir de "hoy".
 *
 * <p>Los modelos chicos calculan mal las fechas ("¿qué día cae el sábado?"). Por eso, en lugar
 * de pedirle al modelo que calcule, se le pasa esta tabla de días con su fecha exacta y
 * solo tiene que copiar la que corresponde.</p>
 */
@Component
public class CalendarioTurnos {

    private static final Locale ES_AR = Locale.of("es", "AR");
    private static final DateTimeFormatter DIA_MES = DateTimeFormatter.ofPattern("dd/MM");

    private final Clock reloj;
    private final int diasVentana;

    @Autowired
    public CalendarioTurnos(Clock reloj, AgenteProperties propiedades) {
        this(reloj, propiedades.diasCalendario());
    }

    public CalendarioTurnos(Clock reloj, int diasVentana) {
        if (diasVentana < 1) {
            throw new IllegalArgumentException("La ventana del calendario debe tener al menos 1 día");
        }
        this.reloj = reloj;
        this.diasVentana = diasVentana;
    }

    public LocalDate hoy() {
        return LocalDate.now(reloj);
    }

    /**
     * @return los días de la ventana, empezando por hoy
     */
    public List<LocalDate> proximosDias() {
        LocalDate hoy = hoy();
        return IntStream.range(0, diasVentana).mapToObj(hoy::plusDays).toList();
    }

    public LocalDate ultimoDia() {
        return hoy().plusDays(diasVentana - 1L);
    }

    /**
     * @param fecha fecha a evaluar
     * @return {@code true} si la fecha está entre hoy y el último día de la ventana (inclusive)
     */
    public boolean estaEnVentana(LocalDate fecha) {
        return !fecha.isBefore(hoy()) && !fecha.isAfter(ultimoDia());
    }

    /**
     * @return nombre del día en español, por ejemplo {@code "sábado"}
     */
    public String nombreDia(LocalDate fecha) {
        return fecha.getDayOfWeek().getDisplayName(TextStyle.FULL, ES_AR);
    }

    /**
     * @return descripción corta para el cliente, por ejemplo {@code "sábado 31/10"}
     */
    public String describir(LocalDate fecha) {
        return nombreDia(fecha) + " " + fecha.format(DIA_MES);
    }

    /**
     * @return descripción para el cliente con referencia relativa cuando aplica:
     *         {@code "hoy jueves 29/10"}, {@code "mañana viernes 30/10"} o {@code "sábado 31/10"}
     */
    public String describirParaCliente(LocalDate fecha) {
        LocalDate hoy = hoy();
        if (fecha.equals(hoy)) {
            return "hoy " + describir(fecha);
        }
        if (fecha.equals(hoy.plusDays(1))) {
            return "mañana " + describir(fecha);
        }
        return describir(fecha);
    }

    /**
     * Tabla de días para el prompt del modelo, una línea por día:
     * <pre>
     * - hoy jueves 29/10 → 2026-10-29
     * - mañana viernes 30/10 → 2026-10-30
     * - sábado 31/10 → 2026-10-31
     * </pre>
     */
    public String calendarioParaPrompt() {
        StringBuilder tabla = new StringBuilder();
        List<LocalDate> dias = proximosDias();
        for (int i = 0; i < dias.size(); i++) {
            String prefijo = switch (i) {
                case 0 -> "hoy ";
                case 1 -> "mañana ";
                default -> "";
            };
            LocalDate fecha = dias.get(i);
            tabla.append("- ").append(prefijo).append(describir(fecha)).append(" → ").append(fecha).append('\n');
        }
        return tabla.toString().stripTrailing();
    }
}
