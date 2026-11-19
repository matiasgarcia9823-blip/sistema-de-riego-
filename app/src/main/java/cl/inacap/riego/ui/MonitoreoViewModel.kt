package cl.inacap.riego.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cl.inacap.riego.data.AppDatabase
import cl.inacap.riego.data.ComandoDao
import cl.inacap.riego.data.ComandoPendiente
import cl.inacap.riego.data.LecturaDao
import cl.inacap.riego.data.LecturaGuardada
import cl.inacap.riego.enlace.ClaveCompartida
import cl.inacap.riego.enlace.Enlace
import cl.inacap.riego.enlace.EnlaceHttp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt

class MonitoreoViewModel(
    private val enlace: Enlace,
    private val lecturas: LecturaDao,
    private val comandos: ComandoDao,
) : ViewModel() {
    private val _humedad = MutableStateFlow<Float?>(null)
    val humedad: StateFlow<Float?> = _humedad

    private val _umbral = MutableStateFlow(40f)
    val umbral: StateFlow<Float> = _umbral

    private val _manual = MutableStateFlow(false)
    val manual: StateFlow<Boolean> = _manual

    private val _estado = MutableStateFlow("Conectando...")
    val estado: StateFlow<String> = _estado

    private val _historial = MutableStateFlow<List<LecturaGuardada>>(emptyList())
    val historial: StateFlow<List<LecturaGuardada>> = _historial

    private var sesion: Job? = null
    private var usuarioMovioUmbral = false
    private var umbralAplicado = false
    private var ajustando = false
    private val envio = Mutex()

    fun iniciar() {
        if (sesion?.isActive == true) return
        val job = SupervisorJob(viewModelScope.coroutineContext.job)
        sesion = job
        val alcance = CoroutineScope(viewModelScope.coroutineContext + job)
        alcance.launch {
            lecturas.observar().collect { recientes ->
                _historial.value = recientes.asReversed()
            }
        }
        alcance.launch { bucleLectura() }
        alcance.launch { secuenciaDemo() }
    }

    fun detener() {
        sesion?.cancel()
        sesion = null
        _estado.value = "Conectando..."
    }

    fun moverUmbral(valor: Float) {
        ajustando = true
        usuarioMovioUmbral = true
        _umbral.value = valor.coerceIn(0f, 100f)
    }

    fun confirmarUmbral() {
        val valor = _umbral.value.roundToInt().coerceIn(0, 100)
        ajustando = false
        encolar("UMBRAL:$valor")
    }

    fun fijarManual(encendido: Boolean) {
        _manual.value = encendido
        encolar(if (encendido) "ACT:ON" else "ACT:AUTO")
    }

    private fun encolar(accion: String) {
        alcanceActivo()?.launch {
            comandos.insertar(
                ComandoPendiente(
                    accion = accion,
                    creada = System.currentTimeMillis(),
                ),
            )
            despacharPendientes()
        }
    }

    private fun alcanceActivo(): CoroutineScope? {
        val job = sesion
        if (job == null || !job.isActive) return null
        return CoroutineScope(viewModelScope.coroutineContext + job)
    }

    private suspend fun bucleLectura() {
        var espera = 1_000L
        while (currentCoroutineContext().isActive) {
            val lectura = enlace.leer()
            if (lectura != null) {
                _humedad.value = lectura.valor
                lecturas.insertar(
                    LecturaGuardada(
                        valor = lectura.valor,
                        umbral = lectura.umbral,
                        actuador = lectura.actuador,
                        ts = lectura.ts,
                        sincronizada = true,
                    ),
                )
                if (!ajustando && !usuarioMovioUmbral && !umbralAplicado) {
                    _umbral.value = lectura.umbral.coerceIn(0f, 100f)
                    umbralAplicado = true
                }
                _estado.value = "Conectado"
                despacharPendientes()
                espera = 1_000L
                delay(2_000L)
            } else {
                _estado.value = "Reconectando..."
                delay(espera)
                espera = minOf(espera * 2, 30_000L)
            }
        }
    }

    private suspend fun secuenciaDemo() {
        val valor = _umbral.value.roundToInt().coerceIn(0, 100)
        val idUmbral = comandos.insertar(
            ComandoPendiente(accion = "UMBRAL:$valor", creada = System.currentTimeMillis()),
        )
        val idAct = comandos.insertar(
            ComandoPendiente(accion = "ACT:ON", creada = System.currentTimeMillis()),
        )
        while (currentCoroutineContext().isActive) {
            despacharPendientes()
            val umbralListo = comandos.buscar(idUmbral)?.sincronizada == true
            val actoListo = comandos.buscar(idAct)?.sincronizada == true
            if (umbralListo && actoListo) break
            delay(1_000L)
        }
        if (!currentCoroutineContext().isActive) return
        enviarHastaCodigo("firma") { enlace.enviarFirmaAlterada("ACT:ON") }
        enviarHastaCodigo("repetido") { enlace.enviarContadorRepetido() }
    }

    private suspend fun despacharPendientes() {
        if (!envio.tryLock()) return
        try {
            while (currentCoroutineContext().isActive) {
                val pendiente = comandos.siguiente() ?: return
                try {
                    val respuesta = enlace.enviar(pendiente.accion)
                    if (respuesta.codigo == 200 && respuesta.cuerpo.startsWith("ACK")) {
                        comandos.marcar(pendiente.id)
                        if (pendiente.accion == "ACT:ON") _manual.value = true
                        if (pendiente.accion == "ACT:AUTO") _manual.value = false
                    } else if (respuesta.codigo == 401) {
                        comandos.marcar(pendiente.id)
                    } else {
                        return
                    }
                } catch (error: IOException) {
                    Log.w("HZB", "cmd ${pendiente.accion} sin enlace: ${error.message}")
                    return
                }
            }
        } finally {
            envio.unlock()
        }
    }

    private suspend fun enviarHastaCodigo(
        marca: String,
        envioUnico: suspend () -> cl.inacap.riego.enlace.RespuestaCmd?,
    ) {
        while (coroutineContext.isActive) {
            try {
                val respuesta = envioUnico() ?: return
                if (respuesta.codigo == 401 && marca == "firma") return
                if (respuesta.codigo == 409 && marca == "repetido") return
                if (respuesta.cuerpo.contains(marca)) return
            } catch (error: IOException) {
                Log.w("HZB", "cmd de prueba sin enlace: ${error.message}")
            }
            delay(1_000L)
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val base = AppDatabase.obtener(context)
                    val guardada = context.getSharedPreferences("riego", Context.MODE_PRIVATE)
                        .getString("board_url", null)
                    val enlace = EnlaceHttp(guardada ?: ClaveCompartida.URL_PLACA)
                    @Suppress("UNCHECKED_CAST")
                    return MonitoreoViewModel(enlace, base.lecturaDao(), base.comandoDao()) as T
                }
            }
        }
    }
}
