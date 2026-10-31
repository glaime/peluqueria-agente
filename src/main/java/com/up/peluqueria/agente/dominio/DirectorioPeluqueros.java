package com.up.peluqueria.agente.dominio;

import com.up.peluqueria.dto.response.PeluqueroResponseDTO;
import com.up.peluqueria.service.PeluqueroService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Acceso de solo lectura a los peluqueros, a través del servicio de la API.
 *
 * <p>El agente nunca usa los repositorios directamente: pasa por los mismos servicios
 * (y validaciones) que los endpoints REST.</p>
 */
@Component
public class DirectorioPeluqueros {

    private final PeluqueroService peluqueroService;

    public DirectorioPeluqueros(PeluqueroService peluqueroService) {
        this.peluqueroService = peluqueroService;
    }

    /**
     * @return nombres de los peluqueros, en el orden en que están registrados
     */
    public List<String> nombres() {
        return peluqueroService.listarTodos().stream().map(PeluqueroResponseDTO::getName).toList();
    }

    /**
     * @param nombre nombre exacto del peluquero (sin distinguir mayúsculas)
     * @return su id, o vacío si no existe
     */
    public Optional<Long> idDe(String nombre) {
        return peluqueroService.listarTodos().stream()
                .filter(peluquero -> peluquero.getName().equalsIgnoreCase(nombre))
                .map(PeluqueroResponseDTO::getId)
                .findFirst();
    }
}
