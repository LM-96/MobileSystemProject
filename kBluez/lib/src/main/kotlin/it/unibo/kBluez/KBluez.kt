package it.unibo.kBluez

import it.unibo.kBluez.model.BluetoothDevice
import it.unibo.kBluez.model.BluetoothLookupResult
import it.unibo.kBluez.model.BluetoothService
import java.io.Closeable
import java.util.*

interface KBluez : Closeable, AutoCloseable {

    suspend fun scan() : List<BluetoothDevice>
    suspend fun lookup(address : String) : BluetoothLookupResult
    suspend fun findServices(name : String? = null,
                     uuid : UUID? = null,
                     address : String? = null) : List<BluetoothService>

}