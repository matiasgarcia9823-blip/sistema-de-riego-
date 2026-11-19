package cl.inacap.riego.enlace

data class Lectura(
    val valor: Float,
    val umbral: Float,
    val actuador: Boolean,
    val ts: Long,
)

data class RespuestaCmd(
    val codigo: Int,
    val cuerpo: String,
)
