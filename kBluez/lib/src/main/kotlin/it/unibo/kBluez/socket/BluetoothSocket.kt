package it.unibo.kBluez.socket

import it.unibo.kBluez.model.BluetoothServiceProtocol
import kotlinx.coroutines.*
import java.io.Closeable
import kotlin.coroutines.coroutineContext

interface BluetoothSocket : Closeable, AutoCloseable {

    val protocol : BluetoothServiceProtocol

    suspend fun getLocalHost() : String?
    suspend fun getLocalPort() : Int?
    suspend fun getRemoteHost() : String?
    suspend fun getRemotePort() : Int?


    suspend fun connect(address : String, port : Int)
    suspend fun bind(port : Int? = null)
    suspend fun listen(backlog : Int = 1)
    suspend fun accept() : BluetoothSocket
    suspend fun send(data : ByteArray, offset : Int = 0, length : Int = data.size)
    suspend fun shutdown()
    suspend fun receive(bufsize : Int = 1024) : ByteArray

    suspend fun advertiseService(name : String, uuid : String)
    suspend fun stopAdvertising()

    suspend fun asyncAccept(scope : CoroutineScope, block : suspend BluetoothSocket.() -> Unit) : BluetoothSocket {
        val accepted = accept()
        scope.launch {
            accepted.block()
        }
        return accepted
    }

    suspend fun asyncAcceptAll(scope : CoroutineScope, afterAccept : suspend AcceptContinuation.(BluetoothSocket) -> Unit) {
        while(scope.isActive) {
            val accepted = AcceptContinuation(accept(), scope)
            accepted.afterAccept(this)
        }
    }

}

class AcceptContinuation(private val acceptedSocket : BluetoothSocket,
                         private val scope : CoroutineScope
) {
    suspend fun withAcceptedSocket(block : suspend BluetoothSocket.() -> Unit) : BluetoothSocket {
        scope.launch {
            acceptedSocket.block()
        }
        return acceptedSocket
    }
}