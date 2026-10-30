package com.up.peluqueria.agente.skill.interpretar;

import com.up.peluqueria.agente.modelo.RespuestaModelo;

/**
 * Resultado completo de ejecutar la skill {@code interpretar-pedido}.
 *
 * @param pedido       pedido validado, listo para que lo use el agente
 * @param salidaModelo JSON tal como lo devolvió el modelo
 * @param telemetria   texto crudo, duración y tokens de la llamada al modelo
 */
public record ResultadoInterpretacion(
        PedidoInterpretado pedido,
        SalidaModeloPedido salidaModelo,
        RespuestaModelo telemetria) {
}
