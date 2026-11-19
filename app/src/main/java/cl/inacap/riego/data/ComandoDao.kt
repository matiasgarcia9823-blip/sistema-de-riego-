package cl.inacap.riego.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ComandoDao {
    @Insert
    suspend fun insertar(comando: ComandoPendiente): Long

    @Query("SELECT * FROM comando_pendiente WHERE sincronizada = 0 ORDER BY id ASC LIMIT 1")
    suspend fun siguiente(): ComandoPendiente?

    @Query("SELECT * FROM comando_pendiente WHERE id = :id LIMIT 1")
    suspend fun buscar(id: Long): ComandoPendiente?

    @Query("UPDATE comando_pendiente SET sincronizada = 1 WHERE id = :id")
    suspend fun marcar(id: Long)
}
