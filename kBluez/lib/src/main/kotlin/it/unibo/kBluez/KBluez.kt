package it.unibo.kBluez

import it.unibo.kBluez.model.BluetoothDevice
import it.unibo.kBluez.model.BluetoothService
import java.io.Closeable
import java.util.*

interface KBluez : Closeable, AutoCloseable {

    fun scan() : List<BluetoothDevice>
    fun lookup(address : String) : Optional<String>
    fun findServices(name : String? = null,
                     uuid : UUID? = null,
                     address : String? = null) : List<BluetoothService>

}