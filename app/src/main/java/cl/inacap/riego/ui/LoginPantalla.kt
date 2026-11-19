package cl.inacap.riego.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import cl.inacap.riego.ui.theme.ErrorColor
import cl.inacap.riego.ui.theme.Fondo
import cl.inacap.riego.ui.theme.Teal
import cl.inacap.riego.ui.theme.TealClaro
import cl.inacap.riego.ui.theme.TemaRiego
import cl.inacap.riego.ui.theme.Titulo

@Composable
fun RiegoApp(usuarioExtra: String?) {
    val viewModel: LoginViewModel = viewModel(factory = LoginViewModel.factory(LocalContext.current))
    val listo by viewModel.listo.collectAsState()
    val sesion by viewModel.sesion.collectAsState()
    val error by viewModel.error.collectAsState()
    val comprobando by viewModel.comprobando.collectAsState()

    LaunchedEffect(usuarioExtra) {
        viewModel.preparar(usuarioExtra)
    }

    TemaRiego {
        Surface(modifier = Modifier.fillMaxSize(), color = Fondo) {
            if (listo && sesion != null) {
                MonitoreoPantalla(onSalir = viewModel::salir)
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    Barra()
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        if (listo) {
                            FormularioLogin(
                                error = error,
                                comprobando = comprobando,
                                onEntrar = viewModel::entrar,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Barra() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.horizontalGradient(listOf(Teal, TealClaro)))
            .statusBarsPadding()
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Monitoreo de Riego",
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
        )
    }
}

@Composable
private fun FormularioLogin(
    error: String?,
    comprobando: Boolean,
    onEntrar: (String, String) -> Unit,
) {
    var usuario by remember { mutableStateOf("") }
    var clave by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text(
            text = "Ingreso",
            color = Titulo,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Sin sesión no se muestran los controles del riego.",
            color = Titulo,
            fontSize = 15.sp,
        )
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedTextField(
            value = usuario,
            onValueChange = { usuario = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Usuario") },
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = clave,
            onValueChange = { clave = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Clave") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        if (error != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = error, color = ErrorColor, fontSize = 14.sp)
        }
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = { onEntrar(usuario, clave) },
            enabled = !comprobando,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Teal),
        ) {
            Text("Entrar")
        }
    }
}
