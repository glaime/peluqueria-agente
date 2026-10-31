package com.up.peluqueria.agente.definicion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CargadorDefinicionesTest {

    private static final String ORIGEN = "classpath:agente/recepcionista.agent.md";
    private static final String ORIGEN_SKILL = "classpath:agente/skills/interpretar-pedido/SKILL.md";

    private static final String AGENTE_VALIDO = """
            ---
            name: recepcionista
            description: Recepcionista virtual de la peluquería.
            model: qwen2.5:3b
            parameters:
              temperature: 0.0
              seed: 42
              num_ctx: 4096
              num_predict: 200
              keep_alive: 10m
            skills:
              - interpretar-pedido
            ---

            # Rol y propósito
            Sos la recepcionista virtual.
            """;

    private static final String SKILL_LLM_VALIDA = """
            ---
            name: interpretar-pedido
            description: Convierte el mensaje en un pedido estructurado.
            type: llm
            output_schema:
              type: object
              properties:
                intencion:
                  type: string
                  enum: [SALUDO, RESERVAR_TURNO]
              required: [intencion]
            ---

            # Rol
            Sos el analizador de pedidos.
            """;

    private static final String SKILL_DETERMINISTA = """
            ---
            name: listar-horarios
            description: Calcula los horarios libres.
            type: deterministic
            ---

            # Rol
            Calcula horarios.
            """;

    @Mock
    private ResourceLoader resourceLoader;

    private final CargadorDefiniciones cargador = new CargadorDefiniciones(null, new LectorFrontmatter());

    // ---------------------------------------------------------------- agente válido

    @Test
    @DisplayName("interpretarAgente: con un agente válido carga nombre, modelo, parámetros y skills")
    void interpretarAgente_agenteValido_cargaTodosLosDatos() {
        // Arrange
        String contenido = AGENTE_VALIDO;

        // Act
        DefinicionAgente agente = cargador.interpretarAgente(contenido, ORIGEN);

        // Assert
        assertThat(agente.nombre()).isEqualTo("recepcionista");
        assertThat(agente.descripcion()).isEqualTo("Recepcionista virtual de la peluquería.");
        assertThat(agente.modelo()).isEqualTo("qwen2.5:3b");
        assertThat(agente.parametros()).isEqualTo(new ParametrosModelo(0.0, null, 4096, 200, 42, "10m"));
        assertThat(agente.skills()).containsExactly("interpretar-pedido");
        assertThat(agente.instrucciones()).startsWith("# Rol y propósito").contains("Sos la recepcionista virtual.");
        assertThat(agente.origen()).isEqualTo(ORIGEN);
    }

    @Test
    @DisplayName("interpretarAgente: los parámetros opcionales (top_p, seed, keep_alive) pueden faltar")
    void interpretarAgente_sinParametrosOpcionales_quedanNulos() {
        // Arrange
        String contenido = AGENTE_VALIDO
                .replace("  seed: 42\n", "")
                .replace("  keep_alive: 10m\n", "");

        // Act
        DefinicionAgente agente = cargador.interpretarAgente(contenido, ORIGEN);

        // Assert
        assertThat(agente.parametros().topP()).isNull();
        assertThat(agente.parametros().seed()).isNull();
        assertThat(agente.parametros().keepAlive()).isNull();
    }

    @Test
    @DisplayName("interpretarAgente: acepta top_p y un keep_alive numérico")
    void interpretarAgente_topPYKeepAliveNumerico_losCarga() {
        // Arrange
        String contenido = AGENTE_VALIDO
                .replace("keep_alive: 10m", "keep_alive: 30\n  top_p: 0.9");

        // Act
        DefinicionAgente agente = cargador.interpretarAgente(contenido, ORIGEN);

        // Assert
        assertThat(agente.parametros().topP()).isEqualTo(0.9);
        assertThat(agente.parametros().keepAlive()).isEqualTo("30");
    }

    // ---------------------------------------------------------------- agente inválido

    @Test
    @DisplayName("interpretarAgente: sin cabecera --- lanza DefinicionInvalidaException")
    void interpretarAgente_sinCabecera_lanzaExcepcion() {
        // Arrange
        String contenido = "# Rol\nSos la recepcionista.";

        // Act + Assert
        assertThatThrownBy(() -> cargador.interpretarAgente(contenido, ORIGEN))
                .isInstanceOf(DefinicionInvalidaException.class)
                .hasMessageContaining(ORIGEN)
                .hasMessageContaining("el archivo debe comenzar con una cabecera YAML delimitada por líneas '---'");
    }

    @Test
    @DisplayName("interpretarAgente: la clave desconocida 'temprature' se informa junto con la que falta")
    void interpretarAgente_claveDesconocidaTemprature_lanzaExcepcion() {
        // Arrange
        String contenido = AGENTE_VALIDO.replace("temperature: 0.0", "temprature: 0.0");

        // Act + Assert
        assertThatThrownBy(() -> cargador.interpretarAgente(contenido, ORIGEN))
                .isInstanceOfSatisfying(DefinicionInvalidaException.class, e -> {
                    assertThat(e.getOrigen()).isEqualTo(ORIGEN);
                    assertThat(e.getProblemas()).hasSize(2);
                    assertThat(e.getProblemas().get(0))
                            .startsWith("clave desconocida 'parameters.temprature' (permitidas: ");
                    assertThat(e.getProblemas().get(1))
                            .isEqualTo("falta el parámetro obligatorio 'parameters.temperature'");
                });
    }

    @Test
    @DisplayName("interpretarAgente: temperature 3.0 fuera de rango lanza DefinicionInvalidaException")
    void interpretarAgente_temperatureFueraDeRango_lanzaExcepcion() {
        // Arrange
        String contenido = AGENTE_VALIDO.replace("temperature: 0.0", "temperature: 3.0");

        // Act + Assert
        assertThatThrownBy(() -> cargador.interpretarAgente(contenido, ORIGEN))
                .isInstanceOfSatisfying(DefinicionInvalidaException.class, e ->
                        assertThat(e.getProblemas())
                                .containsExactly("'parameters.temperature' debe estar entre 0.0 y 2.0 (vale 3.0)"));
    }

    @Test
    @DisplayName("interpretarAgente: num_ctx 100 por debajo del mínimo lanza DefinicionInvalidaException")
    void interpretarAgente_numCtxMuyChico_lanzaExcepcion() {
        // Arrange
        String contenido = AGENTE_VALIDO.replace("num_ctx: 4096", "num_ctx: 100");

        // Act + Assert
        assertThatThrownBy(() -> cargador.interpretarAgente(contenido, ORIGEN))
                .isInstanceOfSatisfying(DefinicionInvalidaException.class, e ->
                        assertThat(e.getProblemas())
                                .containsExactly("'parameters.num_ctx' debe estar entre 512 y 131072 (vale 100)"));
    }

    @Test
    @DisplayName("interpretarAgente: skills repetidas lanza DefinicionInvalidaException")
    void interpretarAgente_skillsRepetidas_lanzaExcepcion() {
        // Arrange
        String contenido = AGENTE_VALIDO.replace("  - interpretar-pedido", "  - interpretar-pedido\n  - interpretar-pedido");

        // Act + Assert
        assertThatThrownBy(() -> cargador.interpretarAgente(contenido, ORIGEN))
                .isInstanceOfSatisfying(DefinicionInvalidaException.class, e ->
                        assertThat(e.getProblemas()).containsExactly("'skills' repite 'interpretar-pedido'"));
    }

    @Test
    @DisplayName("interpretarAgente: name con mayúsculas lanza DefinicionInvalidaException")
    void interpretarAgente_nameConMayusculas_lanzaExcepcion() {
        // Arrange
        String contenido = AGENTE_VALIDO.replace("name: recepcionista", "name: Recepcionista");

        // Act + Assert
        assertThatThrownBy(() -> cargador.interpretarAgente(contenido, ORIGEN))
                .isInstanceOfSatisfying(DefinicionInvalidaException.class, e ->
                        assertThat(e.getProblemas()).containsExactly(
                                "'name' debe estar en kebab-case (ej. recepcionista o asistente-turnos)"));
    }

    @ParameterizedTest(name = "{0} → {1}")
    @DisplayName("interpretarAgente: otros datos inválidos informan el problema correcto")
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
            "temperature: 0.0      | temperature: caliente    | 'parameters.temperature' debe ser numérico",
            "num_predict: 200      | num_predict: 5000        | 'parameters.num_predict' debe estar entre 1 y 4096 (vale 5000)",
            "num_ctx: 4096         | num_ctx: 4096.5          | 'parameters.num_ctx' debe ser un número entero",
            "keep_alive: 10m       | keep_alive: mucho        | 'parameters.keep_alive' debe ser una duración como 30s, 10m o 1h",
            "model: qwen2.5:3b     | model: qwen 2.5          | 'model' tiene caracteres no permitidos: qwen 2.5"
    })
    void interpretarAgente_datoInvalido_informaElProblema(String buscado, String reemplazo, String problema) {
        // Arrange
        String contenido = AGENTE_VALIDO.replace(buscado, reemplazo);

        // Act + Assert
        assertThatThrownBy(() -> cargador.interpretarAgente(contenido, ORIGEN))
                .isInstanceOfSatisfying(DefinicionInvalidaException.class, e ->
                        assertThat(e.getProblemas()).containsExactly(problema));
    }

    @Test
    @DisplayName("interpretarAgente: una lista de skills vacía lanza DefinicionInvalidaException")
    void interpretarAgente_skillsVacias_lanzaExcepcion() {
        // Arrange
        String contenido = AGENTE_VALIDO.replace("skills:\n  - interpretar-pedido", "skills: []");

        // Act + Assert
        assertThatThrownBy(() -> cargador.interpretarAgente(contenido, ORIGEN))
                .isInstanceOfSatisfying(DefinicionInvalidaException.class, e ->
                        assertThat(e.getProblemas())
                                .containsExactly("'skills' debe ser una lista con al menos un elemento"));
    }

    @Test
    @DisplayName("interpretarAgente: una skill con nombre que no es kebab-case lanza DefinicionInvalidaException")
    void interpretarAgente_skillConNombreInvalido_lanzaExcepcion() {
        // Arrange
        String contenido = AGENTE_VALIDO.replace("  - interpretar-pedido", "  - Interpretar_Pedido");

        // Act + Assert
        assertThatThrownBy(() -> cargador.interpretarAgente(contenido, ORIGEN))
                .isInstanceOfSatisfying(DefinicionInvalidaException.class, e ->
                        assertThat(e.getProblemas()).containsExactly(
                                "'skills' contiene un nombre inválido: 'Interpretar_Pedido' (usar kebab-case)"));
    }

    @Test
    @DisplayName("interpretarAgente: sin cuerpo, sin parámetros y sin name informa cada problema")
    void interpretarAgente_faltanDatosObligatorios_informaCadaProblema() {
        // Arrange
        String contenido = """
                ---
                description: Algo
                model: qwen2.5:3b
                skills: [interpretar-pedido]
                ---
                """;

        // Act + Assert
        assertThatThrownBy(() -> cargador.interpretarAgente(contenido, ORIGEN))
                .isInstanceOfSatisfying(DefinicionInvalidaException.class, e ->
                        assertThat(e.getProblemas()).containsExactlyInAnyOrder(
                                "falta la clave obligatoria 'name'",
                                "falta la sección 'parameters' con los parámetros del modelo",
                                "falta el cuerpo con las instrucciones del agente debajo de la cabecera"));
    }

    // ---------------------------------------------------------------- acumulación

    @Test
    @DisplayName("DefinicionInvalidaException: acumula varios problemas en una sola excepción")
    void interpretarAgente_variosErrores_losAcumulaEnUnaSolaExcepcion() {
        // Arrange
        String contenido = AGENTE_VALIDO
                .replace("name: recepcionista", "name: Recepcionista")
                .replace("temperature: 0.0", "temperature: 3.0")
                .replace("num_ctx: 4096", "num_ctx: 100")
                .replace("  - interpretar-pedido", "  - interpretar-pedido\n  - interpretar-pedido");

        // Act + Assert
        assertThatThrownBy(() -> cargador.interpretarAgente(contenido, ORIGEN))
                .isInstanceOfSatisfying(DefinicionInvalidaException.class, e -> {
                    assertThat(e.getProblemas()).hasSize(4).containsExactlyInAnyOrder(
                            "'parameters.temperature' debe estar entre 0.0 y 2.0 (vale 3.0)",
                            "'parameters.num_ctx' debe estar entre 512 y 131072 (vale 100)",
                            "'skills' repite 'interpretar-pedido'",
                            "'name' debe estar en kebab-case (ej. recepcionista o asistente-turnos)");
                    assertThat(e.getMessage())
                            .startsWith("Definición inválida en " + ORIGEN + ":")
                            .contains("  - 'parameters.temperature' debe estar entre 0.0 y 2.0 (vale 3.0)")
                            .contains("  - 'parameters.num_ctx' debe estar entre 512 y 131072 (vale 100)")
                            .contains("  - 'skills' repite 'interpretar-pedido'")
                            .contains("  - 'name' debe estar en kebab-case");
                });
    }

    @Test
    @DisplayName("DefinicionInvalidaException: el mensaje lista cada problema y la lista no se puede modificar")
    void definicionInvalidaException_variosProblemas_construyeMensajeYListaInmutable() {
        // Arrange
        List<String> problemas = List.of("primero", "segundo", "tercero");

        // Act
        DefinicionInvalidaException excepcion = new DefinicionInvalidaException(ORIGEN, problemas);

        // Assert
        assertThat(excepcion.getOrigen()).isEqualTo(ORIGEN);
        assertThat(excepcion.getProblemas()).containsExactly("primero", "segundo", "tercero");
        assertThat(excepcion.getMessage()).contains("  - primero", "  - segundo", "  - tercero");
        assertThatThrownBy(() -> excepcion.getProblemas().add("cuarto"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // ---------------------------------------------------------------- seguridad

    @Test
    @DisplayName("interpretarAgente: un YAML con !!javax.script.ScriptEngineManager se rechaza (CVE-2022-1471)")
    void interpretarAgente_etiquetaScriptEngineManager_seRechaza() {
        // Arrange
        String contenido = """
                ---
                name: recepcionista
                description: !!javax.script.ScriptEngineManager [!!java.net.URLClassLoader [[!!java.net.URL ["http://localhost:1/"]]]]
                model: qwen2.5:3b
                skills: [interpretar-pedido]
                ---
                cuerpo
                """;

        // Act + Assert
        assertThatThrownBy(() -> cargador.interpretarAgente(contenido, ORIGEN))
                .isInstanceOf(DefinicionInvalidaException.class)
                .hasMessageContaining("la cabecera YAML es inválida");
    }

    @Test
    @DisplayName("interpretarSkill: un YAML con !!javax.script.ScriptEngineManager se rechaza (CVE-2022-1471)")
    void interpretarSkill_etiquetaScriptEngineManager_seRechaza() {
        // Arrange
        String contenido = SKILL_LLM_VALIDA.replace("type: llm",
                "type: !!javax.script.ScriptEngineManager [!!java.net.URLClassLoader [[!!java.net.URL [\"http://localhost:1/\"]]]]");

        // Act + Assert
        assertThatThrownBy(() -> cargador.interpretarSkill(contenido, ORIGEN_SKILL, "interpretar-pedido"))
                .isInstanceOf(DefinicionInvalidaException.class)
                .hasMessageContaining("la cabecera YAML es inválida");
    }

    // ---------------------------------------------------------------- skills

    @Test
    @DisplayName("interpretarSkill: con una skill LLM válida carga tipo, esquema e instrucciones")
    void interpretarSkill_skillLlmValida_cargaTodosLosDatos() {
        // Arrange
        String contenido = SKILL_LLM_VALIDA;

        // Act
        DefinicionSkill skill = cargador.interpretarSkill(contenido, ORIGEN_SKILL, "interpretar-pedido");

        // Assert
        assertThat(skill.nombre()).isEqualTo("interpretar-pedido");
        assertThat(skill.tipo()).isEqualTo(TipoSkill.LLM);
        assertThat(skill.esquemaSalida()).containsEntry("type", "object").containsKey("properties");
        assertThat(skill.instrucciones()).contains("Sos el analizador de pedidos.");
        assertThat(skill.origen()).isEqualTo(ORIGEN_SKILL);
    }

    @Test
    @DisplayName("interpretarSkill: una skill determinista válida no lleva esquema de salida")
    void interpretarSkill_skillDeterministaValida_noTieneEsquema() {
        // Arrange
        String contenido = SKILL_DETERMINISTA;

        // Act
        DefinicionSkill skill = cargador.interpretarSkill(contenido, ORIGEN_SKILL, "listar-horarios");

        // Assert
        assertThat(skill.tipo()).isEqualTo(TipoSkill.DETERMINISTA);
        assertThat(skill.esquemaSalida()).isNull();
    }

    @Test
    @DisplayName("interpretarSkill: una skill LLM sin output_schema lanza DefinicionInvalidaException")
    void interpretarSkill_llmSinOutputSchema_lanzaExcepcion() {
        // Arrange
        String contenido = """
                ---
                name: interpretar-pedido
                description: Convierte el mensaje en un pedido.
                type: llm
                ---

                # Rol
                Sos el analizador.
                """;

        // Act + Assert
        assertThatThrownBy(() -> cargador.interpretarSkill(contenido, ORIGEN_SKILL, "interpretar-pedido"))
                .isInstanceOfSatisfying(DefinicionInvalidaException.class, e -> {
                    assertThat(e.getOrigen()).isEqualTo(ORIGEN_SKILL);
                    assertThat(e.getProblemas()).containsExactly(
                            "las skills de tipo 'llm' deben declarar 'output_schema' (JSON Schema de la salida)");
                });
    }

    @ParameterizedTest(name = "{0} → {1}")
    @DisplayName("interpretarSkill: otros datos inválidos informan el problema correcto")
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
            "name: interpretar-pedido | name: otra-skill | 'name' es 'otra-skill' pero el agente la declara como 'interpretar-pedido'",
            "type: llm                | type: magica     | 'type' debe ser 'llm' o 'deterministic', no 'magica'",
            "type: object             | type: array      | 'output_schema.type' debe ser 'object'"
    })
    void interpretarSkill_datoInvalido_informaElProblema(String buscado, String reemplazo, String problema) {
        // Arrange
        String contenido = SKILL_LLM_VALIDA.replace(buscado, reemplazo);

        // Act + Assert
        assertThatThrownBy(() -> cargador.interpretarSkill(contenido, ORIGEN_SKILL, "interpretar-pedido"))
                .isInstanceOfSatisfying(DefinicionInvalidaException.class, e ->
                        assertThat(e.getProblemas()).contains(problema));
    }

    @Test
    @DisplayName("interpretarSkill: una skill determinista con output_schema lanza DefinicionInvalidaException")
    void interpretarSkill_deterministaConOutputSchema_lanzaExcepcion() {
        // Arrange
        String contenido = SKILL_LLM_VALIDA.replace("type: llm", "type: deterministic");

        // Act + Assert
        assertThatThrownBy(() -> cargador.interpretarSkill(contenido, ORIGEN_SKILL, "interpretar-pedido"))
                .isInstanceOfSatisfying(DefinicionInvalidaException.class, e ->
                        assertThat(e.getProblemas()).containsExactly(
                                "'output_schema' solo corresponde a skills de tipo 'llm'"));
    }

    @Test
    @DisplayName("interpretarSkill: un output_schema sin propiedades lanza DefinicionInvalidaException")
    void interpretarSkill_esquemaSinPropiedades_lanzaExcepcion() {
        // Arrange
        String contenido = """
                ---
                name: interpretar-pedido
                description: Convierte el mensaje en un pedido.
                type: llm
                output_schema:
                  type: object
                  properties: {}
                ---

                # Rol
                Sos el analizador.
                """;

        // Act + Assert
        assertThatThrownBy(() -> cargador.interpretarSkill(contenido, ORIGEN_SKILL, "interpretar-pedido"))
                .isInstanceOfSatisfying(DefinicionInvalidaException.class, e ->
                        assertThat(e.getProblemas())
                                .containsExactly("'output_schema.properties' debe definir al menos un campo"));
    }

    @Test
    @DisplayName("interpretarSkill: una clave desconocida en la skill lanza DefinicionInvalidaException")
    void interpretarSkill_claveDesconocida_lanzaExcepcion() {
        // Arrange
        String contenido = SKILL_LLM_VALIDA.replace("type: llm", "type: llm\nversion: 2");

        // Act + Assert
        assertThatThrownBy(() -> cargador.interpretarSkill(contenido, ORIGEN_SKILL, "interpretar-pedido"))
                .isInstanceOfSatisfying(DefinicionInvalidaException.class, e ->
                        assertThat(e.getProblemas()).hasSize(1)
                                .first().asString().startsWith("clave desconocida 'version' (permitidas: "));
    }

    // ---------------------------------------------------------------- cargar (con ResourceLoader simulado)

    @Test
    @DisplayName("cargar: lee el agente y cada skill declarada desde el ResourceLoader")
    void cargar_agenteYSkillsValidos_armaElCatalogo() {
        // Arrange
        when(resourceLoader.getResource("classpath:agente/x.agent.md")).thenReturn(recurso(AGENTE_VALIDO));
        when(resourceLoader.getResource("classpath:agente/skills/interpretar-pedido/SKILL.md"))
                .thenReturn(recurso(SKILL_LLM_VALIDA));
        CargadorDefiniciones conLoader = cargador(resourceLoader);

        // Act
        CatalogoAgente catalogo = conLoader.cargar("classpath:agente/x.agent.md", "classpath:agente/skills");

        // Assert
        assertThat(catalogo.agente().nombre()).isEqualTo("recepcionista");
        assertThat(catalogo.skills()).containsOnlyKeys("interpretar-pedido");
        assertThat(catalogo.skills().get("interpretar-pedido").tipo()).isEqualTo(TipoSkill.LLM);
    }

    @Test
    @DisplayName("cargar: si falta el archivo de una skill declarada lanza DefinicionInvalidaException")
    void cargar_skillSinArchivo_lanzaExcepcion() {
        // Arrange
        Resource inexistente = mock(Resource.class);
        when(inexistente.exists()).thenReturn(false);
        when(resourceLoader.getResource("classpath:agente/x.agent.md")).thenReturn(recurso(AGENTE_VALIDO));
        when(resourceLoader.getResource("classpath:agente/skills/interpretar-pedido/SKILL.md")).thenReturn(inexistente);
        CargadorDefiniciones conLoader = cargador(resourceLoader);

        // Act + Assert
        assertThatThrownBy(() -> conLoader.cargar("classpath:agente/x.agent.md", "classpath:agente/skills"))
                .isInstanceOfSatisfying(DefinicionInvalidaException.class, e -> {
                    assertThat(e.getOrigen()).isEqualTo("classpath:agente/skills/interpretar-pedido/SKILL.md");
                    assertThat(e.getProblemas()).containsExactly("no se encontró el archivo");
                });
    }

    private static CargadorDefiniciones cargador(ResourceLoader loader) {
        return new CargadorDefiniciones(loader, new LectorFrontmatter());
    }

    private static Resource recurso(String contenido) {
        return new ByteArrayResource(contenido.getBytes(StandardCharsets.UTF_8));
    }
}
