package cl.inacap.riego.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cl.inacap.riego.data.AppDatabase
import cl.inacap.riego.data.Cuentas
import cl.inacap.riego.data.Usuario
import cl.inacap.riego.data.UsuarioDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class LoginViewModel(private val dao: UsuarioDao) : ViewModel() {
    private val _listo = MutableStateFlow(false)
    val listo: StateFlow<Boolean> = _listo

    private val _sesion = MutableStateFlow<String?>(null)
    val sesion: StateFlow<String?> = _sesion

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _comprobando = MutableStateFlow(false)
    val comprobando: StateFlow<Boolean> = _comprobando

    fun preparar(usuarioExtra: String?) {
        viewModelScope.launch {
            if (dao.buscar(Cuentas.DEMO) == null) {
                dao.insertar(Usuario(Cuentas.DEMO, Cuentas.CLAVE_DEMO))
            }
            if (!usuarioExtra.isNullOrBlank() && dao.buscar(usuarioExtra) != null) {
                _sesion.value = usuarioExtra
                _error.value = null
            }
            _listo.value = true
        }
    }

    fun entrar(usuario: String, clave: String) {
        viewModelScope.launch {
            _comprobando.value = true
            val fila = dao.buscar(usuario.trim())
            if (fila == null || fila.clave != clave) {
                _error.value = "Credenciales incorrectas"
                _sesion.value = null
            } else {
                _error.value = null
                _sesion.value = fila.usuario
            }
            _comprobando.value = false
        }
    }

    fun salir() {
        _sesion.value = null
        _error.value = null
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val dao = AppDatabase.obtener(context).usuarioDao()
                    @Suppress("UNCHECKED_CAST")
                    return LoginViewModel(dao) as T
                }
            }
        }
    }
}
