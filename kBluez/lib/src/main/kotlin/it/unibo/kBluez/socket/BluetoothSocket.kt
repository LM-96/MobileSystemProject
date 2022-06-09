package it.unibo.kBluez.socket

import java.io.Closeable

interface BluetoothSocket : Closeable, AutoCloseable {

    suspend fun getHost() : String?
    suspend fun getPort() : Int?
    suspend fun getRemoteHost() : String?
    suspend fun getRemotePort() : Int?

    suspend fun connect(address : String, port : Int)
    suspend fun bind(port : Int? = null)
    suspend fun listen(backlog : Int)
    suspend fun accept()
    suspend fun send(data : ByteArray)
    suspend fun shutdown()
    suspend fun receive(bufsize : Int = 1024) : ByteArray



}