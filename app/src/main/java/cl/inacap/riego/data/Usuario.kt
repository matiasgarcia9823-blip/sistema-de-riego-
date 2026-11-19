package cl.inacap.riego.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "usuario")
data class Usuario(
    @PrimaryKey val usuario: String,
    val clave: String,
)
