package cl.inacap.riego

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import cl.inacap.riego.ui.RiegoApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val boardUrl = intent.getStringExtra("board_url")
        if (!boardUrl.isNullOrBlank()) {
            getSharedPreferences("riego", MODE_PRIVATE)
                .edit()
                .putString("board_url", boardUrl)
                .apply()
        }

        setContent {
            RiegoApp(usuarioExtra = intent.getStringExtra("usuario"))
        }
    }
}
