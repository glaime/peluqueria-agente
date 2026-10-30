package com.up.peluqueria.agente.skill.interpretar;

/**
 * El modelo devolvió algo que no es el JSON esperado.
 */
public class SalidaModeloInvalidaException extends RuntimeException {

    private final String textoRecibido;

    public SalidaModeloInvalidaException(String textoRecibido, Throwable causa) {
        super("El modelo no devolvió un JSON válido", causa);
        this.textoRecibido = textoRecibido;
    }

    public String getTextoRecibido() {
        return textoRecibido;
    }
}
