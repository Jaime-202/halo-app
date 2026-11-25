package com.lifedashboard.domain.food

/** Valores leídos de una etiqueta nutricional, por 100 g / 100 ml. Null = no encontrado. */
data class LabelValues(
    val kcal: Float? = null,
    val protein: Float? = null,
    val carbs: Float? = null,
    val sugar: Float? = null,
    val fat: Float? = null,
    val fiber: Float? = null,
    val salt: Float? = null,
) {
    val foundCount: Int get() = listOfNotNull(kcal, protein, carbs, sugar, fat, fiber, salt).size
}

/**
 * Interpreta el texto reconocido de una tabla nutricional española
 * ("Valor energético 1550 kJ / 370 kcal", "Grasas 7,0 g", "de las cuales saturadas 1,2 g"…).
 *
 * Toma el primer número tras cada palabra clave: en las etiquetas europeas la primera
 * columna es siempre "por 100 g". El usuario revisa los valores antes de guardar.
 */
object NutritionLabelParser {

    private val number = Regex("""(\d+(?:[.,]\d+)?)""")

    fun parse(rawText: String): LabelValues {
        val text = rawText
            .lowercase()
            .replace('\n', ' ')
            .replace(Regex("""\s+"""), " ")
            // OCR habitual: "0" leída como "o" delante de una coma ("o,5 g")
            .replace(Regex("""(?<=\s)o(?=[.,]\d)"""), "0")

        return LabelValues(
            kcal = kcal(text),
            protein = after(text, listOf("proteínas", "proteinas", "proteína", "proteina", "protein")),
            carbs = after(text, listOf("hidratos de carbono", "carbohidratos", "carbohydrate", "hidratos")),
            sugar = after(text, listOf("azúcares", "azucares", "azúcar", "sugars")),
            fat = afterExcluding(text, listOf("grasas", "grasa", "fat"), listOf("saturad", "trans", "mono", "poli")),
            fiber = after(text, listOf("fibra alimentaria", "fibra", "fibre", "fiber")),
            salt = after(text, listOf("sal ", "sal:", "salt")),
        )
    }

    /** kcal: número seguido de "kcal"; si solo hay kJ, se convierte. */
    private fun kcal(text: String): Float? {
        Regex("""(\d+(?:[.,]\d+)?)\s*kcal""").find(text)?.let { return it.groupValues[1].toFloatEs() }
        Regex("""(\d+(?:[.,]\d+)?)\s*kj""").find(text)?.let { m ->
            return m.groupValues[1].toFloatEs()?.let { it / 4.184f }
        }
        return null
    }

    private fun after(text: String, keys: List<String>): Float? {
        for (key in keys) {
            var idx = text.indexOf(key)
            while (idx >= 0) {
                val window = text.substring(idx + key.length, minOf(text.length, idx + key.length + 40))
                val value = number.find(window)?.groupValues?.get(1)?.toFloatEs()
                if (value != null && value < 1000f) return value
                idx = text.indexOf(key, idx + 1)
            }
        }
        return null
    }

    private fun afterExcluding(text: String, keys: List<String>, excludeNext: List<String>): Float? {
        for (key in keys) {
            var idx = text.indexOf(key)
            while (idx >= 0) {
                val rest = text.substring(idx + key.length, minOf(text.length, idx + key.length + 40))
                val skip = excludeNext.any { rest.trimStart().startsWith(it) } ||
                    // "de las cuales saturadas" justo antes
                    text.substring(maxOf(0, idx - 20), idx).contains("cuales")
                if (!skip) {
                    val value = number.find(rest)?.groupValues?.get(1)?.toFloatEs()
                    if (value != null && value < 1000f) return value
                }
                idx = text.indexOf(key, idx + 1)
            }
        }
        return null
    }

    private fun String.toFloatEs(): Float? = replace(',', '.').toFloatOrNull()
}
