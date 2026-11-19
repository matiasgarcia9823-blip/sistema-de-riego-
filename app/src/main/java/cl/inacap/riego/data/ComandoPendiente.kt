package cl.inacap.riego.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "comando_pendiente")
data class ComandoPendiente(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accion: String,
    val creada: Long,
    val sincronizada: Boolean = false,
)
