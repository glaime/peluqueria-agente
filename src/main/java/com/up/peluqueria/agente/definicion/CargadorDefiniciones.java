package com.up.peluqueria.agente.definicion;

import org.jspecify.annotations.Nullable;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Carga y valida la configuración declarativa del agente:
 * el {@code .agent.md} y el {@code SKILL.md} de cada skill que declara.
 *
 * <p>La validación es estricta: claves desconocidas (por ejemplo un error de tipeo como
 * {@code temprature}), valores fuera de rango o skills sin su archivo impiden que la
 * aplicación arranque, con un mensaje que lista todos los problemas.</p>
 */
public class CargadorDefiniciones {

    private static final Pattern NOMBRE_KEBAB = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");
    private static final Pattern NOMBRE_MODELO = Pattern.compile("^[A-Za-z0-9._:/-]+$");
    private static final Pattern DURACION_OLLAMA = Pattern.compile("^-?\\d+(?:ms|s|m|h)?$");

    private static final Set<String> CLAVES_AGENTE = Set.of("name", "description", "model", "parameters", "skills");
    private static final Set<String> CLAVES_PARAMETROS =
            Set.of("temperature", "top_p", "num_ctx", "num_predict", "seed", "keep_alive");
    private static final Set<String> CLAVES_SKILL =
            Set.of("name", "description", "type", "input", "output", "output_schema");

    private final ResourceLoader resourceLoader;
    private final LectorFrontmatter lector;

    public CargadorDefiniciones(ResourceLoader resourceLoader, LectorFrontmatter lector) {
        this.resourceLoader = resourceLoader;
        this.lector = lector;
    }

    /**
     * Carga el agente y todas las skills que declara.
     *
     * @param ubicacionAgente  ubicación del {@code .agent.md} (ej. {@code classpath:agente/recepcionista.agent.md})
     * @param directorioSkills carpeta que contiene una subcarpeta con un {@code SKILL.md} por skill
     * @return el catálogo validado
     */
    public CatalogoAgente cargar(String ubicacionAgente, String directorioSkills) {
        DefinicionAgente agente = interpretarAgente(leerRecurso(ubicacionAgente), ubicacionAgente);

        Map<String, DefinicionSkill> skills = new LinkedHashMap<>();
        for (String nombre : agente.skills()) {
            String ubicacionSkill = directorioSkills + "/" + nombre + "/SKILL.md";
            skills.put(nombre, interpretarSkill(leerRecurso(ubicacionSkill), ubicacionSkill, nombre));
        }
        return new CatalogoAgente(agente, skills);
    }

    /**
     * Interpreta el contenido de un {@code .agent.md}.
     *
     * @param contenido texto completo del archivo
     * @param origen    ubicación del archivo, para los mensajes de error
     * @return la definición validada
     */
    public DefinicionAgente interpretarAgente(String contenido, String origen) {
        DocumentoMarkdown documento = lector.leer(contenido, origen);
        Map<String, Object> datos = documento.frontmatter();
        Problemas problemas = new Problemas();

        problemas.clavesDesconocidas(datos, CLAVES_AGENTE, "");
        String nombre = problemas.textoObligatorio(datos, "name");
        String descripcion = problemas.textoObligatorio(datos, "description");
        String modelo = problemas.textoObligatorio(datos, "model");
        ParametrosModelo parametros = interpretarParametros(datos.get("parameters"), problemas);
        List<String> skills = problemas.listaDeNombres(datos, "skills");

        if (nombre != null && !NOMBRE_KEBAB.matcher(nombre).matches()) {
            problemas.agregar("'name' debe estar en kebab-case (ej. recepcionista o asistente-turnos)");
        }
        if (modelo != null && !NOMBRE_MODELO.matcher(modelo).matches()) {
            problemas.agregar("'model' tiene caracteres no permitidos: " + modelo);
        }
        if (documento.cuerpo().isBlank()) {
            problemas.agregar("falta el cuerpo con las instrucciones del agente debajo de la cabecera");
        }

        problemas.lanzarSiHay(origen);
        return new DefinicionAgente(nombre, descripcion, modelo, parametros, skills, documento.cuerpo(), origen);
    }

    /**
     * Interpreta el contenido de un {@code SKILL.md}.
     *
     * @param contenido      texto completo del archivo
     * @param origen         ubicación del archivo, para los mensajes de error
     * @param nombreEsperado nombre con el que la declara el agente (y nombre de su carpeta)
     * @return la definición validada
     */
    public DefinicionSkill interpretarSkill(String contenido, String origen, String nombreEsperado) {
        DocumentoMarkdown documento = lector.leer(contenido, origen);
        Map<String, Object> datos = documento.frontmatter();
        Problemas problemas = new Problemas();

        problemas.clavesDesconocidas(datos, CLAVES_SKILL, "");
        String nombre = problemas.textoObligatorio(datos, "name");
        String descripcion = problemas.textoObligatorio(datos, "description");
        String tipoTexto = problemas.textoObligatorio(datos, "type");

        if (nombre != null && !nombre.equals(nombreEsperado)) {
            problemas.agregar("'name' es '" + nombre + "' pero el agente la declara como '" + nombreEsperado + "'");
        }

        TipoSkill tipo = null;
        if (tipoTexto != null) {
            tipo = TipoSkill.desde(tipoTexto).orElse(null);
            if (tipo == null) {
                problemas.agregar("'type' debe ser 'llm' o 'deterministic', no '" + tipoTexto + "'");
            }
        }

        Map<String, Object> esquema = null;
        Object esquemaCrudo = datos.get("output_schema");
        if (tipo == TipoSkill.LLM) {
            esquema = validarEsquema(esquemaCrudo, problemas);
        } else if (tipo == TipoSkill.DETERMINISTA && esquemaCrudo != null) {
            problemas.agregar("'output_schema' solo corresponde a skills de tipo 'llm'");
        }

        if (documento.cuerpo().isBlank()) {
            problemas.agregar("falta el cuerpo con las instrucciones de la skill debajo de la cabecera");
        }

        problemas.lanzarSiHay(origen);
        return new DefinicionSkill(nombre, descripcion, tipo, esquema, documento.cuerpo(), origen);
    }

    private ParametrosModelo interpretarParametros(@Nullable Object crudo, Problemas problemas) {
        if (!(crudo instanceof Map<?, ?> mapa)) {
            problemas.agregar("falta la sección 'parameters' con los parámetros del modelo");
            return null;
        }
        Map<String, Object> datos = new LinkedHashMap<>();
        mapa.forEach((k, v) -> datos.put(String.valueOf(k), v));
        problemas.clavesDesconocidas(datos, CLAVES_PARAMETROS, "parameters.");

        Double temperature = problemas.decimal(datos, "temperature", true, 0.0, 2.0);
        Double topP = problemas.decimal(datos, "top_p", false, 0.01, 1.0);
        Integer numCtx = problemas.entero(datos, "num_ctx", true, 512, 131_072);
        Integer numPredict = problemas.entero(datos, "num_predict", true, 1, 4_096);
        Integer seed = problemas.entero(datos, "seed", false, Integer.MIN_VALUE, Integer.MAX_VALUE);
        String keepAlive = problemas.textoOpcional(datos, "keep_alive");

        if (keepAlive != null && !DURACION_OLLAMA.matcher(keepAlive).matches()) {
            problemas.agregar("'parameters.keep_alive' debe ser una duración como 30s, 10m o 1h");
        }
        if (temperature == null || numCtx == null || numPredict == null) {
            return null;
        }
        return new ParametrosModelo(temperature, topP, numCtx, numPredict, seed, keepAlive);
    }

    private static @Nullable Map<String, Object> validarEsquema(@Nullable Object crudo, Problemas problemas) {
        if (!(crudo instanceof Map<?, ?> mapa)) {
            problemas.agregar("las skills de tipo 'llm' deben declarar 'output_schema' (JSON Schema de la salida)");
            return null;
        }
        Map<String, Object> esquema = new LinkedHashMap<>();
        mapa.forEach((k, v) -> esquema.put(String.valueOf(k), v));

        if (!"object".equals(esquema.get("type"))) {
            problemas.agregar("'output_schema.type' debe ser 'object'");
        }
        if (!(esquema.get("properties") instanceof Map<?, ?> propiedades) || propiedades.isEmpty()) {
            problemas.agregar("'output_schema.properties' debe definir al menos un campo");
        }
        return esquema;
    }

    private String leerRecurso(String ubicacion) {
        Resource recurso = resourceLoader.getResource(ubicacion);
        if (!recurso.exists()) {
            throw new DefinicionInvalidaException(ubicacion, List.of("no se encontró el archivo"));
        }
        try {
            return recurso.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new DefinicionInvalidaException(ubicacion, "no se pudo leer el archivo", e);
        }
    }

    /**
     * Acumula los problemas de validación para informarlos todos juntos.
     */
    private static final class Problemas {

        private final List<String> lista = new ArrayList<>();

        void agregar(String problema) {
            lista.add(problema);
        }

        void lanzarSiHay(String origen) {
            if (!lista.isEmpty()) {
                throw new DefinicionInvalidaException(origen, lista);
            }
        }

        void clavesDesconocidas(Map<String, Object> datos, Set<String> permitidas, String prefijo) {
            for (String clave : datos.keySet()) {
                if (!permitidas.contains(clave)) {
                    agregar("clave desconocida '" + prefijo + clave + "' (permitidas: "
                            + String.join(", ", new TreeSet<>(permitidas)) + ")");
                }
            }
        }

        @Nullable String textoObligatorio(Map<String, Object> datos, String clave) {
            Object valor = datos.get(clave);
            if (valor == null) {
                agregar("falta la clave obligatoria '" + clave + "'");
                return null;
            }
            if (!(valor instanceof String texto) || texto.isBlank()) {
                agregar("'" + clave + "' debe ser un texto no vacío");
                return null;
            }
            return texto.strip();
        }

        @Nullable String textoOpcional(Map<String, Object> datos, String clave) {
            Object valor = datos.get(clave);
            if (valor == null) {
                return null;
            }
            // YAML interpreta "10m" como texto pero "30" como número: se aceptan ambos.
            if (valor instanceof String || valor instanceof Number) {
                return String.valueOf(valor).strip();
            }
            agregar("'" + clave + "' debe ser un texto");
            return null;
        }

        List<String> listaDeNombres(Map<String, Object> datos, String clave) {
            Object valor = datos.get(clave);
            if (!(valor instanceof List<?> elementos) || elementos.isEmpty()) {
                agregar("'" + clave + "' debe ser una lista con al menos un elemento");
                return List.of();
            }
            List<String> nombres = new ArrayList<>();
            Set<String> vistos = new HashSet<>();
            for (Object elemento : elementos) {
                String nombre = elemento == null ? "" : String.valueOf(elemento).strip();
                if (!NOMBRE_KEBAB.matcher(nombre).matches()) {
                    agregar("'" + clave + "' contiene un nombre inválido: '" + nombre + "' (usar kebab-case)");
                } else if (!vistos.add(nombre)) {
                    agregar("'" + clave + "' repite '" + nombre + "'");
                } else {
                    nombres.add(nombre);
                }
            }
            return nombres;
        }

        @Nullable Double decimal(Map<String, Object> datos, String clave, boolean obligatorio, double min, double max) {
            Object valor = datos.get(clave);
            if (valor == null) {
                if (obligatorio) {
                    agregar("falta el parámetro obligatorio 'parameters." + clave + "'");
                }
                return null;
            }
            if (!(valor instanceof Number numero)) {
                agregar("'parameters." + clave + "' debe ser numérico");
                return null;
            }
            double resultado = numero.doubleValue();
            if (resultado < min || resultado > max) {
                agregar("'parameters." + clave + "' debe estar entre " + min + " y " + max + " (vale " + resultado + ")");
                return null;
            }
            return resultado;
        }

        @Nullable Integer entero(Map<String, Object> datos, String clave, boolean obligatorio, int min, int max) {
            Object valor = datos.get(clave);
            if (valor == null) {
                if (obligatorio) {
                    agregar("falta el parámetro obligatorio 'parameters." + clave + "'");
                }
                return null;
            }
            if (!(valor instanceof Integer || valor instanceof Long)) {
                agregar("'parameters." + clave + "' debe ser un número entero");
                return null;
            }
            long resultado = ((Number) valor).longValue();
            if (resultado < min || resultado > max) {
                agregar("'parameters." + clave + "' debe estar entre " + min + " y " + max + " (vale " + resultado + ")");
                return null;
            }
            return (int) resultado;
        }
    }
}
