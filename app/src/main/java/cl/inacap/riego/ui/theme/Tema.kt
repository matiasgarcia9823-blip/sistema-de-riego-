package cl.inacap.riego.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Teal = Color(0xFF0E7C73)
val TealClaro = Color(0xFF1AA6A6)
val Fondo = Color(0xFFE7F2F4)
val Titulo = Color(0xFF16324F)
val Texto = Color(0xFF5C6B7A)
val ErrorColor = Color(0xFFB42318)
val RiegoOn = Color(0xFF1FA37A)

private val colores = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    background = Fondo,
    surface = Color.White,
)

@Composable
fun TemaRiego(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colores, content = content)
}
