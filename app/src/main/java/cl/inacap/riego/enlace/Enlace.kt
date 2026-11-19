package cl.inacap.riego.enlace

interface Enlace {
    suspend fun leer(): Lectura?

    suspend fun enviar(accion: String): RespuestaCmd

    suspend fun enviarFirmaAlterada(accion: String): RespuestaCmd

    suspend fun enviarContadorRepetido(): RespuestaCmd?
}
