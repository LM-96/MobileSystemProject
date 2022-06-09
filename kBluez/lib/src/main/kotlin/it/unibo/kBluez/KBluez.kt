package it.unibo.kBluez

import it.unibo.kBluez.model.BluetoothDevice
import it.unibo.kBluez.model.BluetoothLookupResult
import it.unibo.kBluez.model.BluetoothService
import it.unibo.kBluez.model.BluetoothServiceProtocol
import it.unibo.kBluez.socket.BluetoothSocket
import it.unibo.kBluez.socket.PyBluezSocketOld
import java.io.Closeable
import java.util.*

interface KBluez : Closeable, AutoCloseable {

    suspend fun scan() : List<BluetoothDevice>
    suspend fun lookup(address : String) : BluetoothLookupResult
    suspend fun findServices(name : String? = null,
                     uuid : UUID? = null,
                     address : String? = null) : List<BluetoothService>
    suspend fun newSocket(protocol : BluetoothServiceProtocol) : BluetoothSocket

}