package it.unibo.kBluez

import it.unibo.kBluez.model.BluetoothDevice
import java.io.Closeable
import java.util.*

interface KBluez : Closeable, AutoCloseable {

    fun scan() : List<BluetoothDevice>
    fun lookup(address : String) : Optional<String>

}