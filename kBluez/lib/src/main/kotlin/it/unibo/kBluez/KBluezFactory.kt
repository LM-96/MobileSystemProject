package it.unibo.kBluez

import it.unibo.kBluez.pybluez.PyKBluez

object KBluezFactory {

    fun getKBluez(name : String = "PY_BLUEZ") : KBluez {
        when(name.uppercase()) {
            "PY_BLUEZ" -> return PyKBluez()
            else -> throw IllegalArgumentException("Unsupported KBluez type")
        }
    }

}