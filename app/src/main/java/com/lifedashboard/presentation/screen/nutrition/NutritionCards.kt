package com.lifedashboard.presentation.screen.nutrition

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.lifedashboard.data.local.entity.MealEntryEntity
import com.lifedashboard.data.remote.food.DetectedFoodItem
import com.lifedashboard.data.remote.food.FoodCatalogItem
import com.lifedashboard.data.remote.food.FoodCatalogService
import com.lifedashboard.data.remote.food.FoodSourceCategory
import com.lifedashboard.data.remote.food.RealFoodImageResolver
import com.lifedashboard.presentation.theme.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// Tarjetas de la pantalla Comida: cabecera, resumen, macros, coach y miniatura de alimento.

// ─── Hero Card de Macros y Balance Calórico Rediseñado ────────────────────────

// ─── Tarjeta de Sección de Comida Rediseñada ──────────────────────────────────

@Composable
fun FoodThumbnail(
    name: String,
    brand: String? = null,
    imageUrl: String? = null,
    size: androidx.compose.ui.unit.Dp = 46.dp,
    modifier: Modifier = Modifier
) {
    val resolvedUrl = remember(name, brand, imageUrl) {
        RealFoodImageResolver.resolve(name, brand, imageUrl)
    }
    val isPackaging = remember(resolvedUrl) {
        resolvedUrl?.contains("prod-mercadona.imgix.net") == true ||
        resolvedUrl?.contains("openfoodfacts") == true
    }

    var imageLoaded by remember(resolvedUrl) { mutableStateOf(false) }
    var imageFailed by remember(resolvedUrl) { mutableStateOf(false) }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (imageLoaded && isPackaging) Color.White
                else Indigo700.copy(alpha = 0.6f)
            )
            .border(
                1.dp,
                if (imageLoaded && isPackaging) Color(0xFFE2E8F0) else Indigo600.copy(alpha = 0.5f),
                RoundedCornerShape(12.dp)
            )
    ) {
        // Sin foto (o mientras carga): miniatura dibujada según el tipo de alimento
        if (resolvedUrl.isNullOrBlank() || imageFailed || !imageLoaded) {
            com.lifedashboard.presentation.common.components.FoodKindTile(name, size, Modifier.fillMaxSize())
        }

        if (!resolvedUrl.isNullOrBlank() && !imageFailed) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(resolvedUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = name,
                onSuccess = { imageLoaded = true },
                onError = { imageFailed = true },
                contentScale = if (isPackaging) ContentScale.Fit else ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (isPackaging) 3.dp else 0.dp)
                    .clip(RoundedCornerShape(10.dp))
            )
        }
    }
}

// ─── Componente: Coach Nutricional IA ──────────────────────────────────────────

// ─── Diálogo de Registro de Comida con IA mediante Texto ───────────────────────
