package com.up.peluqueria.agente.config;

import com.up.peluqueria.agente.definicion.CargadorDefiniciones;
import com.up.peluqueria.agente.definicion.CatalogoAgente;
import com.up.peluqueria.agente.definicion.LectorFrontmatter;
import com.up.peluqueria.agente.modelo.ClienteModelo;
import com.up.peluqueria.agente.modelo.ClienteModeloOllama;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Arma el agente a partir de su configuración declarativa.
 *
 * <p>El modelo, sus parámetros y las skills habilitadas no están escritos en el código:
 * se leen del {@code .agent.md} al arrancar. Cambiar de modelo o de temperatura
 * es editar ese archivo y reiniciar.</p>
 */
@Configuration
@EnableConfigurationProperties(AgenteProperties.class)
public class AgenteConfig {

    @Bean
    public CatalogoAgente catalogoAgente(ResourceLoader resourceLoader, AgenteProperties propiedades) {
        CargadorDefiniciones cargador = new CargadorDefiniciones(resourceLoader, new LectorFrontmatter());
        return cargador.cargar(propiedades.definicion(), propiedades.directorioSkills());
    }

    /**
     * Reloj con la zona horaria de la peluquería. Inyectarlo (en lugar de usar
     * {@code LocalDate.now()}) permite que los tests fijen el día de "hoy".
     */
    @Bean
    public Clock relojAgente(AgenteProperties propiedades) {
        return Clock.system(ZoneId.of(propiedades.zonaHoraria()));
    }

    @Bean
    public ClienteModelo clienteModelo(ChatClient.Builder chatClientBuilder, CatalogoAgente catalogo) {
        return new ClienteModeloOllama(chatClientBuilder.build(), catalogo.agente());
    }
}
