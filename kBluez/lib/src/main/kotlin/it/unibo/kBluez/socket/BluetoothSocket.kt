package it.unibo.kBluez.socket

import it.unibo.kBluez.model.BluetoothServiceProtocol
import java.io.Closeable

interface BluetoothSocket : Closeable, AutoCloseable {

    val protocol : BluetoothServiceProtocol

    suspend fun getLocalHost() : String?
    suspend fun getLocalPort() : Int?
    suspend fun getRemoteHost() : String?
    suspend fun getRemotePort() : Int?


    suspend fun connect(address : String, port : Int)
    suspend fun bind(port : Int? = null)
    suspend fun listen(backlog : Int)
    suspend fun accept() : BluetoothSocket
    suspend fun send(data : ByteArray, offset : Int = 0, length : Int = data.size)
    suspend fun shutdown()
    suspend fun receive(bufsize : Int = 1024) : ByteArray



}