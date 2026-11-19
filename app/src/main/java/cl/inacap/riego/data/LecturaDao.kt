package cl.inacap.riego.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LecturaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertar(lectura: LecturaGuardada)

    @Query("SELECT * FROM lectura ORDER BY ts DESC LIMIT 40")
    fun observar(): Flow<List<LecturaGuardada>>
}
