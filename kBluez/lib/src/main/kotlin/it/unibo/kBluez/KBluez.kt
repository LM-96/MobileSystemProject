package it.unibo.kBluez

import it.unibo.kBluez.model.BluetoothDevice
import it.unibo.kBluez.model.BluetoothLookupResult
import it.unibo.kBluez.model.BluetoothService
import it.unibo.kBluez.model.BluetoothServiceProtocol
import it.unibo.kBluez.pybluez.PyKBluez
import it.unibo.kBluez.socket.BluetoothSocket
import java.io.Closeable
import java.util.*

val KBLUEZ = PyKBluez()

suspend fun newBluetoothSocket(protocol: BluetoothServiceProtocol) : BluetoothSocket {
    return KBLUEZ.requestNewSocket(protocol)
}

interface KBluez : Closeable, AutoCloseable {

    suspend fun scan() : List<BluetoothDevice>
    suspend fun lookup(address : String) : BluetoothLookupResult
    suspend fun findServices(name : String? = null,
                     uuid : UUID? = null,
                     address : String? = null) : List<BluetoothService>
    suspend fun requestNewSocket(protocol : BluetoothServiceProtocol) : BluetoothSocket
    suspend fun getAvailablePort(protocol: BluetoothServiceProtocol) : Int

}