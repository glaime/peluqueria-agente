package com.up.peluqueria.agente.skill.interpretar;

import com.up.peluqueria.agente.tiempo.Textos;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Vincula el peluquero que propone el modelo con uno de los peluqueros reales.
 *
 * <ul>
 *   <li>Tolera tildes, mayúsculas y abreviaturas: "tobi" → Tobias, "santi" → Santiago.</li>
 *   <li>Descarta nombres que no aparecen en el mensaje (el modelo los inventó).</li>
 *   <li>Si el modelo no extrajo el nombre pero el mensaje nombra a un peluquero, lo toma del mensaje.</li>
 * </ul>
 */
public final class ResolutorPeluquero {

    private static final int LARGO_MINIMO_ABREVIATURA = 3;

    private ResolutorPeluquero() {
    }

    /**
     * @param peluquero          nombre de un peluquero existente, si se pudo resolver
     * @param mencionadoDesconocido nombre que dijo el cliente y no corresponde a nadie
     * @param correccion         descripción del ajuste realizado, si hubo alguno
     */
    public record Resultado(
            @Nullable String peluquero,
            @Nullable String mencionadoDesconocido,
            @Nullable String correccion) {

        static Resultado vacio() {
            return new Resultado(null, null, null);
        }
    }

    /**
     * @param delModelo        nombre que extrajo el modelo; puede estar vacío
     * @param mensaje          mensaje original del cliente
     * @param peluqueros       peluqueros que trabajan en la peluquería
     * @param peluqueroEnCurso peluquero del pedido en curso, si lo hay
     * @return el peluquero resuelto
     */
    public static Resultado resolver(@Nullable String delModelo, String mensaje, List<String> peluqueros,
                                     @Nullable String peluqueroEnCurso) {
        String candidato = Textos.normalizar(delModelo);
        String mensajeNormalizado = Textos.normalizar(mensaje);

        if (!candidato.isEmpty()) {
            boolean estaEnElMensaje = apareceEnMensaje(candidato, mensajeNormalizado);
            boolean esElDelPedido = peluqueroEnCurso != null && Textos.normalizar(peluqueroEnCurso).equals(candidato);
            if (!estaEnElMensaje && !esElDelPedido) {
                return new Resultado(null, null,
                        "se descartó el peluquero '" + delModelo.strip() + "' porque no aparece en el mensaje");
            }
            return buscar(candidato, peluqueros)
                    .map(peluquero -> new Resultado(peluquero, null, null))
                    .orElseGet(() -> new Resultado(null, delModelo.strip(), null));
        }

        // Sin ayuda del modelo solo se acepta el nombre completo (evita que "un mate" se lea como Mateo).
        List<String> palabrasMensaje = palabras(mensajeNormalizado);
        List<String> nombradosEnMensaje = peluqueros.stream()
                .filter(p -> palabrasMensaje.contains(Textos.normalizar(p)))
                .toList();
        if (nombradosEnMensaje.size() == 1) {
            String peluquero = nombradosEnMensaje.get(0);
            return new Resultado(peluquero, null,
                    "se tomó el peluquero '" + peluquero + "' del mensaje porque el modelo no lo extrajo");
        }
        return Resultado.vacio();
    }

    /**
     * Busca un peluquero por nombre exacto, por alguna palabra del texto o por abreviatura única.
     */
    static Optional<String> buscar(String textoNormalizado, List<String> peluqueros) {
        List<String> palabras = palabras(textoNormalizado);

        for (String peluquero : peluqueros) {
            String nombre = Textos.normalizar(peluquero);
            if (nombre.equals(textoNormalizado) || palabras.contains(nombre)) {
                return Optional.of(peluquero);
            }
        }

        for (String palabra : palabras) {
            if (palabra.length() < LARGO_MINIMO_ABREVIATURA) {
                continue;
            }
            List<String> coincidencias = peluqueros.stream()
                    .filter(p -> Textos.normalizar(p).startsWith(palabra))
                    .toList();
            if (coincidencias.size() == 1) {
                return Optional.of(coincidencias.get(0));
            }
        }
        return Optional.empty();
    }

    /**
     * El candidato aparece en el mensaje si alguna de sus palabras está en el mensaje,
     * o si el mensaje tiene una abreviatura del candidato ("santi" para "santiago").
     */
    static boolean apareceEnMensaje(String candidatoNormalizado, String mensajeNormalizado) {
        List<String> palabrasMensaje = palabras(mensajeNormalizado);
        for (String parte : palabras(candidatoNormalizado)) {
            if (parte.length() < LARGO_MINIMO_ABREVIATURA) {
                continue;
            }
            if (palabrasMensaje.contains(parte)) {
                return true;
            }
            boolean abreviado = palabrasMensaje.stream()
                    .anyMatch(p -> p.length() >= LARGO_MINIMO_ABREVIATURA && parte.startsWith(p));
            if (abreviado) {
                return true;
            }
        }
        return false;
    }

    private static List<String> palabras(String textoNormalizado) {
        return textoNormalizado.isEmpty() ? List.of() : Arrays.asList(textoNormalizado.split(" "));
    }
}
