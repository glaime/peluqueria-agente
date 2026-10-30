package com.up.peluqueria.agente.tiempo;

import org.jspecify.annotations.Nullable;

import java.time.LocalTime;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Convierte la hora que propone el modelo a {@link LocalTime}.
 *
 * <p>Acepta {@code "10"}, {@code "10:30"}, {@code "10.30"}, {@code "10hs"} y {@code "mediodía"}.
 * Regla del dominio: en una peluquería que abre de 09:00 a 17:30, "a las 3" significa
 * las 15:00, por lo que las horas de 1 a 8 se interpretan como de la tarde.</p>
 */
public final class NormalizadorHora {

    private static final Pattern HORA = Pattern.compile("^(\\d{1,2})(?:[:.](\\d{2}))?\\s*(?:hs?|horas?)?$");
    private static final int PRIMERA_HORA_DE_LA_TARDE = 1;
    private static final int ULTIMA_HORA_DE_LA_TARDE = 8;

    private NormalizadorHora() {
    }

    /**
     * @param texto hora en formato libre; puede ser {@code null}
     * @return la hora interpretada, o vacío si el texto no es una hora válida
     */
    public static Optional<LocalTime> normalizar(@Nullable String texto) {
        String limpio = Textos.normalizar(texto);
        if (limpio.isEmpty()) {
            return Optional.empty();
        }
        if (limpio.equals("mediodia")) {
            return Optional.of(LocalTime.NOON);
        }

        Matcher matcher = HORA.matcher(limpio);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        int horas = Integer.parseInt(matcher.group(1));
        int minutos = matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2));
        if (horas > 23 || minutos > 59) {
            return Optional.empty();
        }
        if (horas >= PRIMERA_HORA_DE_LA_TARDE && horas <= ULTIMA_HORA_DE_LA_TARDE) {
            horas += 12;
        }
        return Optional.of(LocalTime.of(horas, minutos));
    }
}
