package cl.inacap.riego.enlace

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class EnlaceHttp(
    base: String,
    private val clave: String = ClaveCompartida.HMAC,
) : Enlace {
    private val base = normalizar(base)
    private val mutex = Mutex()
    private var contador = System.currentTimeMillis()
    private var ultimoValido: Triple<String, Long, String>? = null

    override suspend fun leer(): Lectura? = withContext(Dispatchers.IO) {
        runCatching {
            val con = abrir("${base}lectura")
            try {
                val codigo = con.responseCode
                val linea = cuerpo(con, codigo)
                if (codigo != 200) error("http $codigo")
                parsear(linea)
            } finally {
                con.disconnect()
            }
        }.onFailure {
            Log.w("HZB", "lectura fallida: ${it.message}")
        }.getOrNull()
    }

    override suspend fun enviar(accion: String): RespuestaCmd = mutex.withLock {
        withContext(Dispatchers.IO) {
            contador += 1
            val n = contador
            val firma = firmar("CMD;$accion;$n")
            ultimoValido = Triple(accion, n, firma)
            despachar(accion, n, firma)
        }
    }

    override suspend fun enviarFirmaAlterada(accion: String): RespuestaCmd = mutex.withLock {
        withContext(Dispatchers.IO) {
            contador += 1
            val n = contador
            val firma = firmar("CMD;$accion;$n")
            despachar(accion, n, alterar(firma))
        }
    }

    override suspend fun enviarContadorRepetido(): RespuestaCmd? = mutex.withLock {
        val guardado = ultimoValido ?: return@withLock null
        withContext(Dispatchers.IO) {
            despachar(guardado.first, guardado.second, guardado.third)
        }
    }

    private fun despachar(accion: String, n: Long, firma: String): RespuestaCmd {
        val con = abrir("${base}cmd?accion=$accion&n=$n&sig=$firma")
        try {
            val codigo = con.responseCode
            val texto = cuerpo(con, codigo)
            Log.i("HZB", "cmd $accion -> $codigo $texto")
            return RespuestaCmd(codigo, texto)
        } finally {
            con.disconnect()
        }
    }

    private fun parsear(linea: String): Lectura {
        val partes = linea.trim().split(";")
        require(partes.size == 5 && partes[0] == "LEC") { "formato" }
        val datos = partes.subList(1, 4).joinToString(";")
        val suma = datos.sumOf { it.code } % 256
        require(partes[4].toInt() == suma) { "checksum" }
        return Lectura(
            valor = partes[1].toFloat(),
            umbral = partes[2].toFloat(),
            actuador = partes[3] == "ON",
            ts = System.currentTimeMillis(),
        )
    }

    private fun firmar(mensaje: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(clave.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(mensaje.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun alterar(firma: String): String {
        val reemplazo = if (firma[0] == '0') '1' else '0'
        return reemplazo + firma.substring(1)
    }

    private fun abrir(url: String): HttpURLConnection {
        val con = URL(url).openConnection() as HttpURLConnection
        con.requestMethod = "GET"
        con.connectTimeout = 3_000
        con.readTimeout = 3_000
        return con
    }

    private fun cuerpo(con: HttpURLConnection, codigo: Int): String {
        val stream = if (codigo < 400) con.inputStream else con.errorStream
        return stream?.bufferedReader()?.use { it.readText() }?.trim().orEmpty()
    }

    private fun normalizar(url: String): String {
        val limpia = url.trim().ifBlank { ClaveCompartida.URL_PLACA }
        return if (limpia.endsWith("/")) limpia else "$limpia/"
    }
}
