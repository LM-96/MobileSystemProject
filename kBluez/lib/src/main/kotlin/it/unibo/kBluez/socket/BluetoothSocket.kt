package it.unibo.kBluez.socket

import it.unibo.kBluez.model.BluetoothServiceProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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



}