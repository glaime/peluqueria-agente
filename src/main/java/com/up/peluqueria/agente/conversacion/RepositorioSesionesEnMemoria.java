package com.up.peluqueria.agente.conversacion;

import com.up.peluqueria.agente.config.AgenteProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sesiones en memoria, con vencimiento por inactividad y un tope de cantidad.
 *
 * <p>Seguridad: el tope evita que alguien agote la memoria del servidor abriendo
 * conversaciones sin parar. Al llegar al máximo se descartan primero las vencidas y,
 * si no alcanza, la de actividad más antigua.</p>
 */
@Slf4j
@Component
public class RepositorioSesionesEnMemoria implements RepositorioSesiones {

    private final Map<String, SesionConversacion> sesiones = new ConcurrentHashMap<>();
    private final Clock reloj;
    private final Duration vencimiento;
    private final int maximo;

    @Autowired
    public RepositorioSesionesEnMemoria(Clock reloj, AgenteProperties propiedades) {
        this(reloj, Duration.ofMinutes(propiedades.sesionTtlMinutos()), propiedades.sesionesMaximas());
    }

    public RepositorioSesionesEnMemoria(Clock reloj, Duration vencimiento, int maximo) {
        if (maximo < 1) {
            throw new IllegalArgumentException("El máximo de sesiones debe ser al menos 1");
        }
        this.reloj = reloj;
        this.vencimiento = vencimiento;
        this.maximo = maximo;
    }

    @Override
    public Optional<SesionConversacion> buscar(String id) {
        SesionConversacion sesion = sesiones.get(id);
        if (sesion == null) {
            return Optional.empty();
        }
        if (estaVencida(sesion)) {
            sesiones.remove(id, sesion);
            log.info("[SESION] {} vencida por inactividad", abreviar(id));
            return Optional.empty();
        }
        return Optional.of(sesion);
    }

    @Override
    public void guardar(SesionConversacion sesion) {
        if (!sesiones.containsKey(sesion.id()) && sesiones.size() >= maximo) {
            liberarEspacio();
        }
        sesiones.put(sesion.id(), sesion);
    }

    @Override
    public void eliminar(String id) {
        sesiones.remove(id);
    }

    public int cantidad() {
        return sesiones.size();
    }

    private void liberarEspacio() {
        sesiones.values().removeIf(this::estaVencida);
        if (sesiones.size() >= maximo) {
            sesiones.values().stream()
                    .min(Comparator.comparing(SesionConversacion::ultimaActividad))
                    .ifPresent(masAntigua -> {
                        sesiones.remove(masAntigua.id());
                        log.warn("[SESION] se alcanzó el máximo de {} sesiones; se descartó {}", maximo, abreviar(masAntigua.id()));
                    });
        }
    }

    private boolean estaVencida(SesionConversacion sesion) {
        return sesion.ultimaActividad().plus(vencimiento).isBefore(Instant.now(reloj));
    }

    static String abreviar(String id) {
        return id.length() <= 8 ? id : id.substring(0, 8);
    }
}
