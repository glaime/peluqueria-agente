package com.up.peluqueria.agente.definicion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LectorFrontmatterTest {

    private static final String ORIGEN = "classpath:agente/prueba.md";

    private static final String DOCUMENTO = """
            ---
            name: recepcionista
            skills:
              - interpretar-pedido
            parameters:
              temperature: 0.5
            ---

            # Rol
            Sos la recepcionista.
            """;

    private final LectorFrontmatter lector = new LectorFrontmatter();

    @Test
    @DisplayName("leer: con un documento válido separa la cabecera YAML del cuerpo")
    void leer_documentoValido_separaFrontmatterYCuerpo() {
        // Arrange
        String contenido = DOCUMENTO;

        // Act
        DocumentoMarkdown documento = lector.leer(contenido, ORIGEN);

        // Assert
        assertThat(documento.frontmatter()).containsEntry("name", "recepcionista")
                .containsEntry("skills", java.util.List.of("interpretar-pedido"))
                .containsEntry("parameters", java.util.Map.of("temperature", 0.5));
        assertThat(documento.cuerpo()).isEqualTo("# Rol\nSos la recepcionista.");
    }

    @Test
    @DisplayName("leer: con saltos de línea de Windows (CRLF) obtiene el mismo resultado")
    void leer_saltosDeLineaCrlf_obtieneElMismoResultado() {
        // Arrange
        String contenido = DOCUMENTO.replace("\n", "\r\n");

        // Act
        DocumentoMarkdown documento = lector.leer(contenido, ORIGEN);

        // Assert
        assertThat(documento.frontmatter()).containsEntry("name", "recepcionista");
        assertThat(documento.cuerpo()).isEqualTo("# Rol\nSos la recepcionista.");
    }

    @Test
    @DisplayName("leer: ignora la marca BOM que algunos editores agregan al inicio")
    void leer_conBom_ignoraLaMarca() {
        // Arrange
        String contenido = "\uFEFF" + DOCUMENTO;

        // Act
        DocumentoMarkdown documento = lector.leer(contenido, ORIGEN);

        // Assert
        assertThat(documento.frontmatter()).containsEntry("name", "recepcionista");
    }

    @Test
    @DisplayName("leer: un '---' dentro del cuerpo no corta la cabecera ni el cuerpo")
    void leer_separadorEnElCuerpo_loConservaEnElCuerpo() {
        // Arrange
        String contenido = """
                ---
                name: a
                ---
                Primera parte
                ---
                Segunda parte
                """;

        // Act
        DocumentoMarkdown documento = lector.leer(contenido, ORIGEN);

        // Assert
        assertThat(documento.frontmatter()).containsOnlyKeys("name");
        assertThat(documento.cuerpo()).isEqualTo("Primera parte\n---\nSegunda parte");
    }

    @Test
    @DisplayName("leer: con cabecera y sin cuerpo devuelve el cuerpo vacío")
    void leer_sinCuerpo_devuelveCuerpoVacio() {
        // Arrange
        String contenido = "---\nname: a\n---\n";

        // Act
        DocumentoMarkdown documento = lector.leer(contenido, ORIGEN);

        // Assert
        assertThat(documento.cuerpo()).isEmpty();
    }

    @ParameterizedTest(name = "[{index}] contenido nulo, vacío o en blanco")
    @DisplayName("leer: con contenido nulo, vacío o en blanco lanza DefinicionInvalidaException")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\n\n"})
    void leer_contenidoNuloOVacio_lanzaExcepcion(String contenido) {
        // Arrange (el contenido llega por parámetro)

        // Act + Assert
        assertThatThrownBy(() -> lector.leer(contenido, ORIGEN))
                .isInstanceOf(DefinicionInvalidaException.class)
                .hasMessageContaining(ORIGEN)
                .hasMessageContaining("el archivo está vacío");
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @DisplayName("leer: sin cabecera '---' bien delimitada lanza DefinicionInvalidaException")
    @ValueSource(strings = {
            "# Solo markdown\nSin cabecera",
            "name: a\n---\ncuerpo",
            "---\nname: a\ncuerpo sin cerrar la cabecera",
            "\n---\nname: a\n---\ncuerpo"
    })
    void leer_sinCabeceraDelimitada_lanzaExcepcion(String contenido) {
        // Arrange (el contenido llega por parámetro)

        // Act + Assert
        assertThatThrownBy(() -> lector.leer(contenido, ORIGEN))
                .isInstanceOf(DefinicionInvalidaException.class)
                .hasMessageContaining("el archivo debe comenzar con una cabecera YAML delimitada por líneas '---'");
    }

    @Test
    @DisplayName("leer: con YAML mal formado lanza DefinicionInvalidaException con la causa")
    void leer_yamlMalFormado_lanzaExcepcionConCausa() {
        // Arrange
        String contenido = "---\nname: [sin cerrar\n---\ncuerpo";

        // Act + Assert
        assertThatThrownBy(() -> lector.leer(contenido, ORIGEN))
                .isInstanceOf(DefinicionInvalidaException.class)
                .hasMessageContaining("la cabecera YAML es inválida")
                .hasCauseInstanceOf(org.yaml.snakeyaml.error.YAMLException.class);
    }

    @ParameterizedTest(name = "cabecera que no es un mapa: {0}")
    @DisplayName("leer: si la cabecera no es un conjunto de pares clave: valor lanza DefinicionInvalidaException")
    @ValueSource(strings = {"- uno\n- dos", "solo un texto", "42"})
    void leer_cabeceraQueNoEsMapa_lanzaExcepcion(String yaml) {
        // Arrange
        String contenido = "---\n" + yaml + "\n---\ncuerpo";

        // Act + Assert
        assertThatThrownBy(() -> lector.leer(contenido, ORIGEN))
                .isInstanceOf(DefinicionInvalidaException.class)
                .hasMessageContaining("la cabecera YAML debe ser un conjunto de pares clave: valor");
    }

    @Test
    @DisplayName("leer: con una clave repetida lanza DefinicionInvalidaException")
    void leer_claveDuplicada_lanzaExcepcion() {
        // Arrange
        String contenido = "---\nname: a\nname: b\n---\ncuerpo";

        // Act + Assert
        assertThatThrownBy(() -> lector.leer(contenido, ORIGEN))
                .isInstanceOf(DefinicionInvalidaException.class)
                .hasMessageContaining("la cabecera YAML es inválida");
    }

    @Test
    @DisplayName("leer: rechaza la etiqueta !!javax.script.ScriptEngineManager (CVE-2022-1471)")
    void leer_etiquetaScriptEngineManager_seRechaza() {
        // Arrange
        String contenido = """
                ---
                name: !!javax.script.ScriptEngineManager [!!java.net.URLClassLoader [[!!java.net.URL ["http://localhost:1/"]]]]
                ---
                cuerpo
                """;

        // Act + Assert
        assertThatThrownBy(() -> lector.leer(contenido, ORIGEN))
                .isInstanceOf(DefinicionInvalidaException.class)
                .hasMessageContaining("la cabecera YAML es inválida")
                .hasCauseInstanceOf(org.yaml.snakeyaml.error.YAMLException.class);
    }

    @ParameterizedTest(name = "etiqueta {0}")
    @DisplayName("leer: rechaza cualquier etiqueta que intente instanciar clases Java")
    @ValueSource(strings = {
            "!!java.lang.ProcessBuilder [[calc]]",
            "!!java.util.Date []",
            "!!com.up.peluqueria.PeluqueriaApplication {}"
    })
    void leer_etiquetasDeClases_seRechazan(String valor) {
        // Arrange
        String contenido = "---\nname: " + valor + "\n---\ncuerpo";

        // Act + Assert
        assertThatThrownBy(() -> lector.leer(contenido, ORIGEN))
                .isInstanceOf(DefinicionInvalidaException.class)
                .hasMessageContaining("la cabecera YAML es inválida");
    }

    @Test
    @DisplayName("leer: más alias de los permitidos (billion laughs) lanza DefinicionInvalidaException")
    void leer_demasiadosAlias_lanzaExcepcion() {
        // Arrange
        String contenido = """
                ---
                a: &a [x, x]
                b: [*a, *a, *a, *a, *a, *a, *a, *a, *a, *a, *a, *a]
                ---
                cuerpo
                """;

        // Act + Assert
        assertThatThrownBy(() -> lector.leer(contenido, ORIGEN))
                .isInstanceOf(DefinicionInvalidaException.class)
                .hasMessageContaining("la cabecera YAML es inválida");
    }

    @Test
    @DisplayName("leer: una cabecera con anidamiento excesivo lanza DefinicionInvalidaException")
    void leer_anidamientoExcesivo_lanzaExcepcion() {
        // Arrange
        String contenido = "---\na: " + "[".repeat(30) + "]".repeat(30) + "\n---\ncuerpo";

        // Act + Assert
        assertThatThrownBy(() -> lector.leer(contenido, ORIGEN))
                .isInstanceOf(DefinicionInvalidaException.class)
                .hasMessageContaining("la cabecera YAML es inválida");
    }

    @Test
    @DisplayName("leer: una cabecera mayor a 64 KB lanza DefinicionInvalidaException")
    void leer_cabeceraDemasiadoGrande_lanzaExcepcion() {
        // Arrange
        String contenido = "---\na: " + "x".repeat(70_000) + "\n---\ncuerpo";

        // Act + Assert
        assertThatThrownBy(() -> lector.leer(contenido, ORIGEN))
                .isInstanceOf(DefinicionInvalidaException.class)
                .hasMessageContaining("la cabecera YAML es inválida");
    }
}
