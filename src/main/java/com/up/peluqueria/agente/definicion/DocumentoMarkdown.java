package com.up.peluqueria.agente.definicion;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Archivo Markdown ya separado en sus dos partes: la cabecera YAML (frontmatter)
 * y el cuerpo con las instrucciones en lenguaje natural.
 *
 * @param frontmatter metadatos declarados entre los delimitadores {@code ---}
 * @param cuerpo      texto Markdown que sigue a la cabecera
 */
public record DocumentoMarkdown(Map<String, Object> frontmatter, String cuerpo) {

    public DocumentoMarkdown {
        // LinkedHashMap conserva el orden y admite valores nulos (claves YAML sin valor).
        frontmatter = Collections.unmodifiableMap(new LinkedHashMap<>(frontmatter));
        cuerpo = cuerpo == null ? "" : cuerpo.strip();
    }
}
