package com.up.peluqueria.agente.conversacion;

import com.up.peluqueria.agente.skill.interpretar.Intencion;
import com.up.peluqueria.agente.tiempo.Textos;

import java.util.Optional;
import java.util.Set;

/**
 * Reconoce sin modelo las respuestas cortas a "¿confirmás?" ("sí", "dale", "no").
 *
 * <p>Confirmar es el paso que escribe en la base, así que no se deja librado a la
 * interpretación del modelo: solo se aceptan expresiones exactas de una lista conocida.
 * Cualquier otra respuesta pasa por la skill {@code interpretar-pedido}.</p>
 */
public final class RespuestasRapidas {

    private static final Set<String> AFIRMACIONES = Set.of(
            "si", "sii", "si si", "dale", "dale si", "si dale", "ok", "oka", "okey", "okay", "confirmo", "si confirmo",
            "confirmado", "perfecto", "listo", "de acuerdo", "joya", "de una", "si por favor", "reservalo",
            "si reservalo", "claro", "genial", "va", "bueno");

    private static final Set<String> NEGACIONES = Set.of(
            "no", "nop", "no gracias", "mejor no", "cancelar", "cancela", "cancelalo", "dejalo", "no dejalo",
            "no quiero", "no confirmo");

    private RespuestasRapidas() {
    }

    /**
     * @param mensaje texto del cliente
     * @return {@link Intencion#CONFIRMAR}, {@link Intencion#CANCELAR}, o vacío si no es una respuesta corta conocida
     */
    public static Optional<Intencion> detectar(String mensaje) {
        String texto = Textos.normalizar(mensaje).replace(".", "").strip();
        if (AFIRMACIONES.contains(texto)) {
            return Optional.of(Intencion.CONFIRMAR);
        }
        if (NEGACIONES.contains(texto)) {
            return Optional.of(Intencion.CANCELAR);
        }
        return Optional.empty();
    }
}
