package com.up.peluqueria.agente.skill.peluqueros;

import com.up.peluqueria.agente.definicion.TipoSkill;
import com.up.peluqueria.agente.dominio.DirectorioPeluqueros;
import com.up.peluqueria.agente.skill.Skill;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Skill {@code listar-peluqueros}: informa quiénes atienden.
 *
 * <p>Determinista y de solo lectura. Contrato en {@code resources/agente/skills/listar-peluqueros/SKILL.md}.</p>
 */
@Slf4j
@Component
public class ListarPeluquerosSkill implements Skill {

    public static final String NOMBRE = "listar-peluqueros";

    private final DirectorioPeluqueros directorio;

    public ListarPeluquerosSkill(DirectorioPeluqueros directorio) {
        this.directorio = directorio;
    }

    @Override
    public String nombre() {
        return NOMBRE;
    }

    @Override
    public TipoSkill tipo() {
        return TipoSkill.DETERMINISTA;
    }

    /**
     * @return nombres de los peluqueros, en el orden en que están registrados
     */
    public List<String> ejecutar() {
        List<String> peluqueros = directorio.nombres();
        log.info("[SKILL {}] {} peluqueros: {}", NOMBRE, peluqueros.size(), peluqueros);
        return peluqueros;
    }
}
