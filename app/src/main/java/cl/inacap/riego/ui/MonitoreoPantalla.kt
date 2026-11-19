package cl.inacap.riego.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import cl.inacap.riego.data.LecturaGuardada
import cl.inacap.riego.ui.theme.ErrorColor
import cl.inacap.riego.ui.theme.Fondo
import cl.inacap.riego.ui.theme.RiegoOn
import cl.inacap.riego.ui.theme.Teal
import cl.inacap.riego.ui.theme.TealClaro
import cl.inacap.riego.ui.theme.Texto
import cl.inacap.riego.ui.theme.Titulo
import kotlin.math.roundToInt

@Composable
fun MonitoreoPantalla(onSalir: () -> Unit) {
    val viewModel: MonitoreoViewModel = viewModel(
        factory = MonitoreoViewModel.factory(LocalContext.current),
    )
    val humedad by viewModel.humedad.collectAsState()
    val umbral by viewModel.umbral.collectAsState()
    val manual by viewModel.manual.collectAsState()
    val estado by viewModel.estado.collectAsState()
    val historial by viewModel.historial.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.iniciar()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Fondo),
    ) {
        BarraMonitoreo(onSalir = {
            viewModel.detener()
            onSalir()
        })
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 20.dp),
        ) {
            Text(
                text = "Monitoreo de humedad,\ncon activación de riego",
                color = Titulo,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 32.sp,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Solución para agricultura de precisión que optimiza el uso hídrico. Un sensor subterráneo analiza la humedad del sustrato y transmite métricas cuantificadas. El sistema opera en dos modos:",
                color = Texto,
                fontSize = 15.sp,
                lineHeight = 21.sp,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = estado,
                color = if (estado.startsWith("Reconectando")) ErrorColor else Teal,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(22.dp))
            MedidorHumedad(humedad = humedad)
            Spacer(modifier = Modifier.height(22.dp))
            TarjetaHistorial(lecturas = historial)
            Spacer(modifier = Modifier.height(14.dp))
            TarjetaUmbral(
                humedad = humedad,
                umbral = umbral,
                onUmbral = viewModel::moverUmbral,
                onSoltar = viewModel::confirmarUmbral,
            )
            Spacer(modifier = Modifier.height(14.dp))
            TarjetaRiego(
                encendido = manual,
                onCambio = viewModel::fijarManual,
            )
        }
    }
}

@Composable
private fun BarraMonitoreo(onSalir: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.horizontalGradient(listOf(Teal, TealClaro)))
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .align(Alignment.CenterStart)
                .clickable(onClick = onSalir),
            contentAlignment = Alignment.Center,
        ) {
            Chevron()
        }
        Text(
            text = "Monitoreo de Riego",
            modifier = Modifier.align(Alignment.Center),
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
        )
    }
}

@Composable
private fun Chevron() {
    Canvas(modifier = Modifier.size(18.dp)) {
        val trazo = 2.4.dp.toPx()
        val color = Color.White
        drawLine(color, Offset(size.width * 0.62f, size.height * 0.18f), Offset(size.width * 0.28f, size.height * 0.5f), trazo, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.28f, size.height * 0.5f), Offset(size.width * 0.62f, size.height * 0.82f), trazo, StrokeCap.Round)
    }
}

@Composable
private fun MedidorHumedad(humedad: Float?) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(148.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val grosor = 16.dp.toPx()
                val arco = Size(size.width - grosor, size.height - grosor)
                val origen = Offset(grosor / 2f, grosor / 2f)
                val estilo = Stroke(width = grosor, cap = StrokeCap.Round)
                drawArc(
                    color = Color(0xFFD7E4E8),
                    startAngle = 150f,
                    sweepAngle = 240f,
                    useCenter = false,
                    topLeft = origen,
                    size = arco,
                    style = estilo,
                )
                val fraccion = ((humedad ?: 0f) / 100f).coerceIn(0f, 1f)
                if (fraccion > 0f) {
                    drawArc(
                        brush = Brush.sweepGradient(listOf(TealClaro, Teal)),
                        startAngle = 150f,
                        sweepAngle = 240f * fraccion,
                        useCenter = false,
                        topLeft = origen,
                        size = arco,
                        style = estilo,
                    )
                }
            }
            Text(
                text = textoPorcentaje(humedad),
                color = Titulo,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text = "Humedad del sustrato",
            color = Texto,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
        Text(
            text = if (humedad == null) "Sin lectura" else textoPorcentaje(humedad),
            color = Texto,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun TarjetaHistorial(lecturas: List<LecturaGuardada>) {
    Tarjeta {
        Text(
            text = "Historial de humedad",
            color = Titulo,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (lecturas.isEmpty()) {
                "Todavía no hay lecturas guardadas en el teléfono."
            } else {
                "${lecturas.size} lecturas guardadas en el teléfono."
            },
            color = Texto,
            fontSize = 14.sp,
        )
        if (lecturas.size >= 2) {
            Spacer(modifier = Modifier.height(12.dp))
            Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                val valores = lecturas.map { it.valor.coerceIn(0f, 100f) }
                val paso = size.width / (valores.size - 1).coerceAtLeast(1)
                val trazo = Path()
                valores.forEachIndexed { indice, valor ->
                    val x = indice * paso
                    val y = size.height - (valor / 100f) * (size.height - 8.dp.toPx()) - 4.dp.toPx()
                    if (indice == 0) trazo.moveTo(x, y) else trazo.lineTo(x, y)
                }
                drawPath(
                    path = trazo,
                    color = Teal,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }
    }
}

@Composable
private fun TarjetaUmbral(
    humedad: Float?,
    umbral: Float,
    onUmbral: (Float) -> Unit,
    onSoltar: () -> Unit,
) {
    Tarjeta {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconoTeal { Engranaje() }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Configuración personalizada\ndel umbral de activación",
                color = Titulo,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 21.sp,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (humedad == null) "Humedad del sustrato" else "Humedad del ${textoPorcentaje(humedad)}",
            color = Texto,
            fontSize = 14.sp,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ControlUmbral(
                valor = umbral,
                onValor = onUmbral,
                onSoltar = onSoltar,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(12.dp))
            CajaValor(texto = umbral.roundToInt().toString())
        }
    }
}

@Composable
private fun TarjetaRiego(encendido: Boolean, onCambio: (Boolean) -> Unit) {
    Tarjeta {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconoTeal { Gota() }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Control manual remoto\ndel riego",
                color = Titulo,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 21.sp,
            )
        }
        Spacer(modifier = Modifier.height(18.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "Activar Riego", color = Titulo, fontSize = 16.sp)
            Interruptor(encendido = encendido, onCambio = onCambio)
        }
    }
}

@Composable
private fun Tarjeta(contenido: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        shadowElevation = 8.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp), content = { contenido() })
    }
}

@Composable
private fun IconoTeal(icono: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(listOf(TealClaro, Teal))),
        contentAlignment = Alignment.Center,
    ) {
        icono()
    }
}

@Composable
private fun Engranaje() {
    Canvas(modifier = Modifier.size(24.dp)) {
        val centro = Offset(size.width / 2f, size.height / 2f)
        drawCircle(color = Color.White, radius = size.minDimension * 0.28f, center = centro, style = Stroke(width = 2.2.dp.toPx()))
        drawCircle(color = Color.White, radius = size.minDimension * 0.08f, center = centro)
        val largo = size.minDimension * 0.16f
        repeat(8) { indice ->
            val angulo = Math.toRadians((indice * 45.0) - 90.0)
            val desde = size.minDimension * 0.34f
            val dx = kotlin.math.cos(angulo).toFloat()
            val dy = kotlin.math.sin(angulo).toFloat()
            drawLine(
                color = Color.White,
                start = Offset(centro.x + dx * desde, centro.y + dy * desde),
                end = Offset(centro.x + dx * (desde + largo), centro.y + dy * (desde + largo)),
                strokeWidth = 2.4.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun Gota() {
    Canvas(modifier = Modifier.size(24.dp)) {
        val camino = androidx.compose.ui.graphics.Path().apply {
            moveTo(size.width / 2f, size.height * 0.08f)
            cubicTo(
                size.width * 0.08f, size.height * 0.48f,
                size.width * 0.12f, size.height * 0.78f,
                size.width / 2f, size.height * 0.9f,
            )
            cubicTo(
                size.width * 0.88f, size.height * 0.78f,
                size.width * 0.92f, size.height * 0.48f,
                size.width / 2f, size.height * 0.08f,
            )
            close()
        }
        drawPath(camino, Color.White)
    }
}

@Composable
private fun ControlUmbral(
    valor: Float,
    onValor: (Float) -> Unit,
    onSoltar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(36.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val inicio = awaitFirstDown()
                    fun publicar(x: Float) {
                        val fraccion = (x / size.width).coerceIn(0f, 1f)
                        onValor(fraccion * 100f)
                    }
                    publicar(inicio.position.x)
                    drag(inicio.id) { cambio ->
                        publicar(cambio.position.x)
                        if (cambio.positionChange() != Offset.Zero) cambio.consume()
                    }
                    onSoltar()
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(36.dp)) {
            val centroY = size.height / 2f
            val radio = 11.dp.toPx()
            val izquierda = radio
            val derecha = size.width - radio
            val recorrido = (derecha - izquierda).coerceAtLeast(1f)
            val x = izquierda + recorrido * (valor / 100f)
            drawLine(Color(0xFFD7E4E8), Offset(izquierda, centroY), Offset(derecha, centroY), 6.dp.toPx(), StrokeCap.Round)
            drawLine(
                brush = Brush.horizontalGradient(listOf(Teal, TealClaro)),
                start = Offset(izquierda, centroY),
                end = Offset(x, centroY),
                strokeWidth = 6.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawCircle(Color.White, radio + 2.dp.toPx(), Offset(x, centroY))
            drawCircle(Teal, radio, Offset(x, centroY))
        }
    }
}

@Composable
private fun CajaValor(texto: String) {
    Box(
        modifier = Modifier
            .size(width = 52.dp, height = 40.dp)
            .border(1.dp, Color(0xFFE1E6EA), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = texto, color = Titulo, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun Interruptor(encendido: Boolean, onCambio: (Boolean) -> Unit) {
    val alineacion = if (encendido) Alignment.CenterEnd else Alignment.CenterStart
    Box(
        modifier = Modifier
            .size(width = 56.dp, height = 32.dp)
            .clip(RoundedCornerShape(50))
            .background(if (encendido) RiegoOn else Color(0xFFD5DDE3))
            .clickable { onCambio(!encendido) }
            .padding(3.dp),
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .align(alineacion)
                .shadow(2.dp, CircleShape)
                .background(Color.White, CircleShape),
        )
    }
}

private fun textoPorcentaje(valor: Float?): String {
    if (valor == null) return "—"
    val decimas = (valor * 10f).roundToInt().coerceIn(0, 1000)
    return if (decimas % 10 == 0) {
        "${decimas / 10}%"
    } else {
        "${decimas / 10}.${decimas % 10}%"
    }
}
