package com.up.peluqueria.agente.definicion;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Separa un archivo Markdown con frontmatter YAML en metadatos y cuerpo.
 *
 * <pre>
 * ---
 * name: recepcionista
 * model: qwen2.5:3b
 * ---
 * # Rol y propósito
 * ...
 * </pre>
 *
 * <p><b>Seguridad:</b> el YAML se interpreta con {@link SafeConstructor}, que solo crea
 * tipos básicos (mapas, listas, textos y números). Un YAML con etiquetas como
 * {@code !!javax.script.ScriptEngineManager} no puede instanciar clases arbitrarias
 * (vulnerabilidad CVE-2022-1471 de SnakeYAML). Además se limitan los alias, la profundidad
 * y el tamaño para evitar ataques de denegación de servicio del tipo "billion laughs".</p>
 */
public class LectorFrontmatter {

    private static final Pattern ESTRUCTURA = Pattern.compile(
            "\\A---[ \\t]*\\n(.*?)\\n---[ \\t]*(?:\\n|\\z)(.*)\\z", Pattern.DOTALL);

    private static final int MAX_ALIAS = 10;
    private static final int MAX_PROFUNDIDAD = 20;
    private static final int MAX_CARACTERES_YAML = 64 * 1024;

    /**
     * Lee el contenido de un archivo Markdown con frontmatter.
     *
     * @param contenido texto completo del archivo
     * @param origen    ubicación del archivo, usada en los mensajes de error
     * @return el documento separado en frontmatter y cuerpo
     * @throws DefinicionInvalidaException si falta la cabecera o el YAML es inválido
     */
    public DocumentoMarkdown leer(String contenido, String origen) {
        if (contenido == null || contenido.isBlank()) {
            throw new DefinicionInvalidaException(origen, List.of("el archivo está vacío"));
        }

        String texto = normalizarSaltosDeLinea(contenido);
        Matcher matcher = ESTRUCTURA.matcher(texto);
        if (!matcher.matches()) {
            throw new DefinicionInvalidaException(origen, List.of(
                    "el archivo debe comenzar con una cabecera YAML delimitada por líneas '---'"));
        }

        Map<String, Object> frontmatter = interpretarYaml(matcher.group(1), origen);
        return new DocumentoMarkdown(frontmatter, matcher.group(2));
    }

    private static String normalizarSaltosDeLinea(String contenido) {
        String texto = contenido.replace("\r\n", "\n").replace('\r', '\n');
        // Algunos editores de Windows agregan una marca BOM al inicio del archivo.
        return texto.startsWith("﻿") ? texto.substring(1) : texto;
    }

    private static Map<String, Object> interpretarYaml(String yamlTexto, String origen) {
        LoaderOptions opciones = new LoaderOptions();
        opciones.setAllowDuplicateKeys(false);
        opciones.setMaxAliasesForCollections(MAX_ALIAS);
        opciones.setNestingDepthLimit(MAX_PROFUNDIDAD);
        opciones.setCodePointLimit(MAX_CARACTERES_YAML);

        Object datos;
        try {
            datos = new Yaml(new SafeConstructor(opciones)).load(yamlTexto);
        } catch (YAMLException e) {
            throw new DefinicionInvalidaException(origen, "la cabecera YAML es inválida: " + e.getMessage(), e);
        }

        if (!(datos instanceof Map<?, ?> mapa)) {
            throw new DefinicionInvalidaException(origen, List.of(
                    "la cabecera YAML debe ser un conjunto de pares clave: valor"));
        }

        Map<String, Object> resultado = new LinkedHashMap<>();
        mapa.forEach((clave, valor) -> resultado.put(String.valueOf(clave), valor));
        return resultado;
    }
}
