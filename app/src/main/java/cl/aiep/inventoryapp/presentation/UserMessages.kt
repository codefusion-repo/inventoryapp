package cl.aiep.inventoryapp.presentation

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestoreException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Mensajes breves y seguros; los detalles internos no se muestran en la interfaz. */
object UserMessages {
    fun login(cause: Throwable): String {
        if (cause is FirebaseAuthException) {
            return when (cause.errorCode) {
                "ERROR_INVALID_EMAIL" -> "Revisa el formato de tu correo electrónico."
                "ERROR_INVALID_LOGIN_CREDENTIALS", "ERROR_WRONG_PASSWORD", "ERROR_USER_NOT_FOUND" ->
                    "No pudimos ingresar. Revisa tu correo y contraseña."
                "ERROR_USER_DISABLED" -> "Esta cuenta está deshabilitada. Consulta con quien administra el inventario."
                "ERROR_TOO_MANY_REQUESTS" -> "Hubo demasiados intentos. Espera unos minutos y vuelve a probar."
                "ERROR_OPERATION_NOT_ALLOWED" -> "El inicio de sesión con correo está deshabilitado. Contacta al administrador."
                else -> connectivity(cause) ?: "No pudimos iniciar sesión. Inténtalo nuevamente."
            }
        }
        return connectivity(cause) ?: "No pudimos iniciar sesión. Inténtalo nuevamente."
    }

    fun guest(cause: Throwable): String = when {
        cause is FirebaseAuthException && cause.errorCode == "ERROR_OPERATION_NOT_ALLOWED" ->
            "El acceso como invitado aún no está habilitado. Contacta al administrador."
        else -> connectivity(cause)
            ?: "El catálogo online no está disponible por ahora. Puedes revisar los productos guardados en este dispositivo."
    }

    fun profile(cause: Throwable): String = when {
        cause is FirebaseFirestoreException && cause.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            "No tenemos permiso para consultar tu perfil. Pide al administrador que revise tu acceso."
        else -> connectivity(cause)
            ?: "No pudimos comprobar tu perfil. Intenta ingresar otra vez."
    }

    fun inventory(cause: Throwable): String = when {
        cause is FirebaseFirestoreException && cause.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            "No tienes permiso para acceder al inventario online. Pide al administrador que revise tu perfil."
        else -> connectivity(cause)
            ?: "No pudimos actualizar el catálogo desde la nube. Seguiremos mostrando los datos guardados."
    }

    fun upload(cause: Throwable): String = when {
        cause is FirebaseFirestoreException && cause.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            "El servidor rechazó el cambio por permisos. Revisa tu cuenta y vuelve a intentarlo."
        cause is FirebaseFirestoreException && cause.code == FirebaseFirestoreException.Code.NOT_FOUND ->
            "Este producto ya no está disponible en la nube. Actualiza el catálogo antes de reintentar."
        else -> connectivity(cause)
            ?: "No pudimos enviar algunos cambios. Permanecen guardados en el dispositivo para reintentarlo."
    }

    fun localSave(cause: Throwable): String = when (cause) {
        is IllegalArgumentException -> "No pudimos completar el cambio. Revisa que el producto siga disponible."
        else -> "No pudimos guardar el cambio en este dispositivo. Inténtalo nuevamente."
    }

    private fun connectivity(cause: Throwable): String? = when {
        cause is FirebaseNetworkException || cause is UnknownHostException || cause is SocketTimeoutException ->
            "Parece que no hay conexión. Revisa tu internet y vuelve a intentarlo."
        cause is FirebaseFirestoreException && cause.code in setOf(
            FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED
        ) -> "La conexión está inestable. Puedes seguir trabajando y reintentar después."
        cause is IOException -> "No logramos conectar con el servidor. Revisa tu internet y vuelve a intentarlo."
        else -> null
    }
}
