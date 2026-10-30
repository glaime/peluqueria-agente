package com.up.peluqueria.agente.tiempo;

import org.jspecify.annotations.Nullable;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Utilidades de texto para comparar lo que escribe el cliente sin depender de
 * mayúsculas, tildes ni signos de puntuación.
 */
public final class Textos {

    private static final Pattern MARCAS_DIACRITICAS = Pattern.compile("\\p{M}+");
    private static final Pattern CARACTERES_NO_UTILES = Pattern.compile("[^a-z0-9/:.\\s]");
    private static final Pattern ESPACIOS = Pattern.compile("\\s+");

    private Textos() {
    }

    /**
     * Pasa a minúsculas, quita tildes (la ñ queda como n), reemplaza signos por espacios
     * y colapsa los espacios. Conserva {@code /}, {@code :} y {@code .} porque forman
     * parte de fechas y horas.
     *
     * <p>Ejemplo: {@code "¿Mañana con Tobías?"} se convierte en {@code "manana con tobias"}.</p>
     *
     * @param texto texto a normalizar; puede ser {@code null}
     * @return el texto normalizado, o una cadena vacía si era {@code null}
     */
    public static String normalizar(@Nullable String texto) {
        if (texto == null) {
            return "";
        }
        String sinTildes = MARCAS_DIACRITICAS.matcher(Normalizer.normalize(texto, Normalizer.Form.NFD)).replaceAll("");
        String soloUtiles = CARACTERES_NO_UTILES.matcher(sinTildes.toLowerCase(Locale.ROOT)).replaceAll(" ");
        return ESPACIOS.matcher(soloUtiles).replaceAll(" ").strip();
    }
}
