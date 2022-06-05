package it.unibo.kBluez

import it.unibo.kBluez.pybluez.PyKBluez
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope

object KBluezFactory {

    fun getKBluez(name : String = "PY_BLUEZ", scope : CoroutineScope = GlobalScope) : KBluez {
        when(name.uppercase()) {
            "PY_BLUEZ" -> return PyKBluez(scope)
            else -> throw IllegalArgumentException("Unsupported KBluez type")
        }
    }

}