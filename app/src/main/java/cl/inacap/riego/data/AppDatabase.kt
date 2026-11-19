package cl.inacap.riego.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Usuario::class, LecturaGuardada::class, ComandoPendiente::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun usuarioDao(): UsuarioDao

    abstract fun lecturaDao(): LecturaDao

    abstract fun comandoDao(): ComandoDao

    companion object {
        @Volatile
        private var instancia: AppDatabase? = null

        fun obtener(context: Context): AppDatabase {
            return instancia ?: synchronized(this) {
                instancia ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "riego.db",
                ).fallbackToDestructiveMigration().build().also { instancia = it }
            }
        }
    }
}
