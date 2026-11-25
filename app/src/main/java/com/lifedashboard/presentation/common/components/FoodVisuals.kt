package com.lifedashboard.presentation.common.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import java.util.Locale

/**
 * Tipo de alimento para dibujar una miniatura cuando no hay foto:
 * icono de Material + color propio de la familia de alimentos.
 */
enum class FoodKind(val icon: ImageVector, val color: Color, val label: String) {
    HUEVO(Icons.Outlined.Egg, Color(0xFFF2C14E), "Huevo"),
    CARNE(Icons.Outlined.KebabDining, Color(0xFFE0735A), "Carne"),
    PESCADO(Icons.Outlined.SetMeal, Color(0xFF5BA8E0), "Pescado"),
    LACTEO(Icons.Outlined.LocalDrink, Color(0xFF9FB8E8), "Lácteo"),
    FRUTA(Icons.Outlined.Spa, Color(0xFFF08A5D), "Fruta"),
    VERDURA(Icons.Outlined.Grass, Color(0xFF5DBB7A), "Verdura"),
    CEREAL(Icons.Outlined.BakeryDining, Color(0xFFD9A55B), "Pan y cereales"),
    ARROZ_PASTA(Icons.Outlined.RiceBowl, Color(0xFFE3C88F), "Arroz y pasta"),
    LEGUMBRE(Icons.Outlined.Grain, Color(0xFFB98A5C), "Legumbres"),
    PLATO(Icons.Outlined.RamenDining, Color(0xFFE59866), "Plato"),
    RAPIDA(Icons.Outlined.LunchDining, Color(0xFFE5A34B), "Comida rápida"),
    DULCE(Icons.Outlined.Cookie, Color(0xFFC98B6B), "Dulce"),
    GRASA(Icons.Outlined.WaterDrop, Color(0xFFB7C35A), "Aceites y frutos secos"),
    CAFE(Icons.Outlined.Coffee, Color(0xFFA97C5F), "Café e infusiones"),
    BEBIDA(Icons.Outlined.LocalBar, Color(0xFF7DC4C4), "Bebida"),
    OTRO(Icons.Outlined.Restaurant, Color(0xFF8AA7FF), "Comida");

    companion object {
        fun forName(name: String): FoodKind {
            val n = name.lowercase(Locale.ROOT)
            fun has(vararg w: String) = w.any { n.contains(it) }
            return when {
                has("huevo", "tortilla", "claras") -> HUEVO
                has("pollo", "pechuga", "pavo", "ternera", "cerdo", "lomo", "jamón", "jamon", "filete", "chuleta", "carne", "bacon", "chorizo", "salchicha", "hamburguesa de", "cordero", "conejo") -> CARNE
                has("atún", "atun", "salmón", "salmon", "merluza", "pescado", "bacalao", "gamba", "langostino", "marisco", "calamar", "pulpo", "sardina", "dorada", "lubina", "bonito") -> PESCADO
                has("café", "cafe", "té ", "infusión", "infusion", "cortado", "capuchino") -> CAFE
                has("leche", "yogur", "kéfir", "kefir", "queso", "nata", "requesón", "skyr") -> LACTEO
                has("plátano", "platano", "manzana", "naranja", "mandarina", "fresa", "arándano", "arandano", "frambuesa", "pera", "uva", "kiwi", "melón", "melon", "sandía", "sandia", "piña", "mango", "melocotón", "aguacate", "fruta", "limón", "limon") -> FRUTA
                has("ensalada", "lechuga", "espinaca", "tomate", "zanahoria", "brócoli", "brocoli", "calabacín", "calabacin", "pimiento", "cebolla", "champiñ", "seta", "verdura", "pepino", "berenjena", "judía verde", "acelga", "coliflor") -> VERDURA
                has("lenteja", "garbanzo", "alubia", "judía", "judia", "soja", "tofu", "hummus") -> LEGUMBRE
                has("arroz", "pasta", "macarr", "espagueti", "fideo", "quinoa", "cuscús", "cuscus", "patata", "boniato", "noodle") -> ARROZ_PASTA
                has("pan", "tostada", "avena", "cereal", "muesli", "copos", "biscote", "galleta maría", "baguette", "bocadillo", "wrap", "tortita") -> CEREAL
                has("pizza", "burger", "hamburguesa", "kebab", "patatas fritas", "nugget", "perrito") -> RAPIDA
                has("chocolate", "galleta", "bollo", "croissant", "magdalena", "bizcocho", "tarta", "helado", "donut", "cacao", "azúcar", "mermelada", "miel") -> DULCE
                has("aceite", "oliva", "nuez", "nueces", "almendra", "cacahuete", "pistacho", "anacardo", "mantequilla", "crema de cacahuete", "semillas") -> GRASA
                has("agua", "zumo", "refresco", "cerveza", "vino", "bebida", "batido", "isotónica", "cola") -> BEBIDA
                has("sopa", "caldo", "crema de", "puré", "pure", "guiso", "estofado", "paella", "lasaña", "plato", "menú", "cocido", "potaje") -> PLATO
                else -> OTRO
            }
        }
    }
}

/** Miniatura dibujada: degradado suave del color del alimento con su icono. */
@Composable
fun FoodKindTile(name: String, size: Dp, modifier: Modifier = Modifier) {
    val kind = FoodKind.forName(name)
    Box(
        modifier = modifier
            .size(size)
            .background(
                Brush.linearGradient(listOf(kind.color.copy(alpha = 0.34f), kind.color.copy(alpha = 0.14f)))
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(kind.icon, contentDescription = kind.label, tint = kind.color, modifier = Modifier.size(size * 0.5f))
    }
}
