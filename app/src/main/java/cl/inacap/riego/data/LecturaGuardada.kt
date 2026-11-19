package cl.inacap.riego.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "lectura")
data class LecturaGuardada(
    val valor: Float,
    val umbral: Float,
    val actuador: Boolean,
    @PrimaryKey val ts: Long,
    val sincronizada: Boolean = true,
)
