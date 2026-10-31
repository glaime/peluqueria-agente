package com.up.peluqueria.agente.skill;

import com.up.peluqueria.agente.definicion.CatalogoAgente;
import com.up.peluqueria.agente.definicion.DefinicionAgente;
import com.up.peluqueria.agente.definicion.DefinicionInvalidaException;
import com.up.peluqueria.agente.definicion.DefinicionSkill;
import com.up.peluqueria.agente.definicion.ParametrosModelo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Cruza las skills declaradas en el {@code .agent.md} con las implementadas en el código.
 *
 * <ul>
 *   <li>Declarada pero sin implementación: la aplicación no arranca.</li>
 *   <li>Implementada pero no declarada: queda deshabilitada y el agente no puede usarla
 *       (principio de mínimo privilegio: el {@code .agent.md} funciona como lista blanca).</li>
 * </ul>
 */
@Slf4j
@Component
public final class RegistroSkills {

    private final CatalogoAgente catalogo;
    private final Map<String, Skill> implementaciones;

    public RegistroSkills(CatalogoAgente catalogo, List<Skill> skills) {
        this.catalogo = catalogo;
        this.implementaciones = skills.stream()
                .collect(Collectors.toUnmodifiableMap(Skill::nombre, Function.identity()));
        validar();
        registrarResumen();
    }

    /**
     * @param nombre nombre de la skill
     * @return {@code true} si el {@code .agent.md} la habilita
     */
    public boolean estaHabilitada(String nombre) {
        return catalogo.skills().containsKey(nombre);
    }

    /**
     * Devuelve la definición de una skill habilitada.
     *
     * @param nombre nombre de la skill
     * @return su definición
     * @throws SkillNoHabilitadaException si el {@code .agent.md} no la habilita
     */
    public DefinicionSkill definicion(String nombre) {
        return catalogo.skill(nombre).orElseThrow(() -> new SkillNoHabilitadaException(nombre));
    }

    /**
     * Comprueba que una skill esté habilitada antes de ejecutarla.
     *
     * @param nombre nombre de la skill
     * @throws SkillNoHabilitadaException si el {@code .agent.md} no la habilita
     */
    public void verificarHabilitada(String nombre) {
        if (!estaHabilitada(nombre)) {
            throw new SkillNoHabilitadaException(nombre);
        }
    }

    public List<String> habilitadas() {
        return List.copyOf(catalogo.skills().keySet());
    }

    public DefinicionAgente agente() {
        return catalogo.agente();
    }

    private void validar() {
        List<String> problemas = new ArrayList<>();
        catalogo.skills().forEach((nombre, definicion) -> {
            Skill implementacion = implementaciones.get(nombre);
            if (implementacion == null) {
                problemas.add("la skill '" + nombre + "' está declarada pero no hay una clase que la implemente");
            } else if (implementacion.tipo() != definicion.tipo()) {
                problemas.add("la skill '" + nombre + "' se declara de tipo '" + definicion.tipo().valorEnArchivo()
                        + "' en su SKILL.md pero la clase es de tipo '" + implementacion.tipo().valorEnArchivo() + "'");
            }
        });
        if (!problemas.isEmpty()) {
            throw new DefinicionInvalidaException(catalogo.agente().origen(), problemas);
        }
    }

    private void registrarResumen() {
        DefinicionAgente agente = catalogo.agente();
        ParametrosModelo p = agente.parametros();
        log.info("[AGENTE] '{}' cargado desde {}", agente.nombre(), agente.origen());
        log.info("[AGENTE] modelo={} | temperature={} | top_p={} | num_ctx={} | num_predict={} | seed={} | keep_alive={}",
                agente.modelo(), p.temperature(), valor(p.topP()), p.numCtx(), p.numPredict(),
                valor(p.seed()), valor(p.keepAlive()));
        log.info("[AGENTE] skills habilitadas: {}", habilitadas());

        implementaciones.keySet().stream()
                .filter(nombre -> !estaHabilitada(nombre))
                .sorted()
                .forEach(nombre -> log.warn("[AGENTE] la skill '{}' existe en el código pero no está habilitada en {}",
                        nombre, agente.origen()));
    }

    private static Object valor(Object valor) {
        return valor == null ? "-" : valor;
    }
}
