package com.up.peluqueria.agente.conversacion;

import java.util.Optional;

/**
 * Dónde se guarda la memoria de las conversaciones.
 *
 * <p>Hoy se guarda en memoria ({@link RepositorioSesionesEnMemoria}). Para un despliegue con
 * varias instancias alcanza con otra implementación (por ejemplo, Redis) sin tocar el agente.</p>
 */
public interface RepositorioSesiones {

    /**
     * @return la sesión, o vacío si no existe o venció por inactividad
     */
    Optional<SesionConversacion> buscar(String id);

    void guardar(SesionConversacion sesion);

    void eliminar(String id);
}
