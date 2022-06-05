package it.unibo.kBluez.model

import java.util.Optional

data class BluetoothLookupResult(
    val name : Optional<String>,
    val error : Optional<String>
) {
    companion object {
        fun ofError(err : String) = BluetoothLookupResult(Optional.empty(), Optional.of(err))
        fun ofName(name : String) = BluetoothLookupResult(Optional.of(name), Optional.empty())
    }
}