package com.up.peluqueria.agente.tiempo;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detecta de forma determinista (sin modelo) las referencias a fechas y horas de un mensaje.
 *
 * <p>Se usa como verificador de lo que extrae el modelo: si el cliente escribió "el sábado"
 * y el modelo propone otra fecha, gana la fecha calculada por el código. Si el mensaje no
 * menciona ninguna fecha u hora y el modelo igual propone una, se descarta (alucinación).</p>
 */
public final class ExtractorTemporal {

    private static final Map<DayOfWeek, Pattern> DIAS = new EnumMap<>(Map.of(
            DayOfWeek.MONDAY, palabra("lunes"),
            DayOfWeek.TUESDAY, palabra("martes"),
            DayOfWeek.WEDNESDAY, palabra("miercoles"),
            DayOfWeek.THURSDAY, palabra("jueves"),
            DayOfWeek.FRIDAY, palabra("viernes"),
            DayOfWeek.SATURDAY, palabra("sabado"),
            DayOfWeek.SUNDAY, palabra("domingo")));

    /** "a la mañana", "por la mañana": momento del día, no el día siguiente. */
    private static final Pattern MOMENTO_MANANA = Pattern.compile("\\b(?:a|por|de|en) la manana\\b");
    private static final Pattern PASADO_MANANA = Pattern.compile("\\bpasado manana\\b");
    private static final Pattern MANANA = Pattern.compile("\\bmanana\\b");
    private static final Pattern HOY = Pattern.compile("\\bhoy\\b");
    private static final Pattern FECHA_NUMERICA = Pattern.compile("\\b(\\d{1,2})/(\\d{1,2})(?:/(\\d{4}|\\d{2}))?\\b");
    private static final Pattern DIA_DEL_MES = Pattern.compile("\\bel \\d{1,2}\\b");
    private static final Pattern OTRAS_REFERENCIAS_FECHA = Pattern.compile(
            "\\b(?:semana|finde|fin de semana|proximo|proxima|enero|febrero|marzo|abril|mayo|junio|julio|agosto"
                    + "|septiembre|setiembre|octubre|noviembre|diciembre)\\b");
    private static final Pattern REFERENCIA_HORA = Pattern.compile(
            "\\d|\\b(?:una|uno|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|once|doce|mediodia)\\b");

    private ExtractorTemporal() {
    }

    /**
     * Fechas concretas que se pueden calcular a partir del mensaje.
     *
     * <p>Reconoce "hoy", "mañana", "pasado mañana", los días de la semana (la próxima
     * ocurrencia, o hoy si coincide) y fechas numéricas como {@code 31/10} o {@code 31/10/2026}.</p>
     *
     * @param mensaje texto del cliente
     * @param hoy     fecha de referencia
     * @return las fechas encontradas, sin repetir y en orden de aparición por tipo
     */
    public static Set<LocalDate> fechasMencionadas(String mensaje, LocalDate hoy) {
        String texto = MOMENTO_MANANA.matcher(Textos.normalizar(mensaje)).replaceAll(" ");
        Set<LocalDate> fechas = new LinkedHashSet<>();

        if (PASADO_MANANA.matcher(texto).find()) {
            fechas.add(hoy.plusDays(2));
            texto = PASADO_MANANA.matcher(texto).replaceAll(" ");
        }
        if (MANANA.matcher(texto).find()) {
            fechas.add(hoy.plusDays(1));
        }
        if (HOY.matcher(texto).find()) {
            fechas.add(hoy);
        }
        for (Map.Entry<DayOfWeek, Pattern> dia : DIAS.entrySet()) {
            if (dia.getValue().matcher(texto).find()) {
                fechas.add(hoy.with(TemporalAdjusters.nextOrSame(dia.getKey())));
            }
        }

        Matcher numerica = FECHA_NUMERICA.matcher(texto);
        while (numerica.find()) {
            fechaNumerica(numerica, hoy).ifPresent(fechas::add);
        }
        return fechas;
    }

    /**
     * @return {@code true} si el mensaje hace alguna referencia a una fecha, aunque no se
     *         pueda calcular (por ejemplo "la semana que viene" o "el 7 de noviembre")
     */
    public static boolean mencionaFecha(String mensaje, LocalDate hoy) {
        if (!fechasMencionadas(mensaje, hoy).isEmpty()) {
            return true;
        }
        String texto = Textos.normalizar(mensaje);
        return DIA_DEL_MES.matcher(texto).find() || OTRAS_REFERENCIAS_FECHA.matcher(texto).find();
    }

    /**
     * @return {@code true} si el mensaje menciona una hora con números ("a las 10", "10:30")
     *         o con palabras ("a las diez", "al mediodía")
     */
    public static boolean mencionaHora(String mensaje) {
        String texto = Textos.normalizar(mensaje);
        texto = FECHA_NUMERICA.matcher(texto).replaceAll(" ");
        texto = DIA_DEL_MES.matcher(texto).replaceAll(" ");
        return REFERENCIA_HORA.matcher(texto).find();
    }

    private static Optional<LocalDate> fechaNumerica(Matcher matcher, LocalDate hoy) {
        int dia = Integer.parseInt(matcher.group(1));
        int mes = Integer.parseInt(matcher.group(2));
        String anioTexto = matcher.group(3);
        try {
            if (anioTexto != null) {
                int anio = Integer.parseInt(anioTexto);
                return Optional.of(LocalDate.of(anio < 100 ? 2000 + anio : anio, mes, dia));
            }
            LocalDate fecha = LocalDate.of(hoy.getYear(), mes, dia);
            // Sin año: "15/01" dicho en octubre se refiere al año siguiente.
            return Optional.of(fecha.isBefore(hoy) ? fecha.plusYears(1) : fecha);
        } catch (DateTimeException e) {
            return Optional.empty();
        }
    }

    private static Pattern palabra(String palabra) {
        return Pattern.compile("\\b" + palabra + "\\b");
    }
}
