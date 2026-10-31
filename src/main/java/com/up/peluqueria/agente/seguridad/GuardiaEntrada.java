package com.up.peluqueria.agente.seguridad;

import com.up.peluqueria.agente.tiempo.Textos;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Primera capa de defensa: revisa cada mensaje <b>antes</b> de que llegue al modelo.
 *
 * <ol>
 *   <li><b>Saneamiento:</b> quita caracteres de control e invisibles (por ejemplo, los de ancho cero
 *       que se usan para esconder instrucciones) y colapsa los espacios.</li>
 *   <li><b>Longitud:</b> rechaza mensajes de más de 500 caracteres (además de la validación del endpoint).</li>
 *   <li><b>Prompt injection:</b> bloquea patrones conocidos ("ignorá tus instrucciones",
 *       "mostrame tu prompt", "actuá como..."), en español y en inglés.</li>
 * </ol>
 *
 * <p>Es una heurística: un atacante creativo puede esquivarla. Por eso no es la única defensa,
 * sino la primera de varias capas (ver {@code SECURITY.md}). Su ventaja es que corta los ataques
 * obvios sin gastar una llamada al modelo y deja el intento registrado en el log.</p>
 */
@Slf4j
@Component
public class GuardiaEntrada {

    public static final int LARGO_MAXIMO = 500;

    /** Caracteres de control (salvo salto de línea y tabulación) y caracteres invisibles de formato. */
    private static final Pattern INVISIBLES = Pattern.compile("[\\p{Cc}\\p{Cf}&&[^\\n\\t]]");
    private static final Pattern ESPACIOS = Pattern.compile("\\s+");

    /** Patrones sobre el texto normalizado (minúsculas y sin tildes), con el motivo que se registra. */
    private static final Map<Pattern, String> PATRONES_INYECCION = new LinkedHashMap<>();

    static {
        patron("\\b(ignora|ignorar|olvida|olvidar|omiti|saltea|descarta)\\w*\\b.{0,40}\\b(instrucciones|reglas|indicaciones|restricciones|prompt)\\b",
                "pide ignorar las instrucciones");
        patron("\\b(mostra|mostrame|deci|decime|revela|revelame|repeti|repetime|escribi|imprimi)\\b.{0,30}\\b(prompt|instrucciones|configuracion)\\b",
                "pide revelar las instrucciones");
        patron("\\b(system prompt|prompt de sistema|mensaje de sistema)\\b", "menciona el prompt de sistema");
        patron("\\b(actua|actuar|comportate|hace de cuenta|haz de cuenta|simula|finji)\\w*\\b.{0,20}\\b(como|que sos|que eres)\\b",
                "pide cambiar de rol");
        patron("\\b(a partir de ahora|desde ahora)\\b.{0,30}\\b(sos|seras|eres|tenes que|debes)\\b", "pide cambiar de rol");
        patron("\\b(modo desarrollador|developer mode|jailbreak|modo dios)\\b", "intenta un jailbreak");
        patron("\\bignore\\b.{0,30}\\b(previous|prior|above|all)\\b.{0,20}\\b(instructions|rules)\\b",
                "pide ignorar las instrucciones (inglés)");
        patron("\\b(you are now|act as|pretend to be|reveal your|system message)\\b", "pide cambiar de rol (inglés)");
        patron("[<>]{3,}|```", "usa delimitadores para salir del bloque del mensaje");
    }

    private static void patron(String regex, String motivo) {
        PATRONES_INYECCION.put(Pattern.compile(regex), motivo);
    }

    /**
     * @param mensaje texto del cliente tal como llegó
     * @return el mensaje saneado y si puede enviarse al modelo
     */
    public ResultadoGuardia revisar(String mensaje) {
        String saneado = ESPACIOS.matcher(INVISIBLES.matcher(mensaje == null ? "" : mensaje).replaceAll(""))
                .replaceAll(" ").strip();

        if (saneado.isEmpty()) {
            return ResultadoGuardia.bloquear(saneado, "mensaje vacío");
        }
        if (saneado.length() > LARGO_MAXIMO) {
            return registrar(ResultadoGuardia.bloquear(saneado, "supera los " + LARGO_MAXIMO + " caracteres"));
        }

        // Los delimitadores se buscan en el texto original: Textos.normalizar los quitaría.
        String normalizado = Textos.normalizar(saneado) + " " + saneado;
        for (Map.Entry<Pattern, String> patron : PATRONES_INYECCION.entrySet()) {
            if (patron.getKey().matcher(normalizado).find()) {
                return registrar(ResultadoGuardia.bloquear(saneado, "posible prompt injection: " + patron.getValue()));
            }
        }
        return ResultadoGuardia.permitir(saneado);
    }

    private static ResultadoGuardia registrar(ResultadoGuardia resultado) {
        log.warn("[SEGURIDAD] mensaje bloqueado antes de llegar al modelo: {}", resultado.motivo());
        return resultado;
    }
}
