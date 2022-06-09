package it.unibo.kBluez.pybluez

import com.google.gson.JsonObject
import it.unibo.kBluez.socket.BluetoothSocket
import kotlinx.coroutines.channels.Channel

class PyBluezSocket internal constructor(
    private val uuid : String,
    private val sockIn : Channel<JsonObject>,
    private val sockOut : Channel<JsonObject>,
    private val sockErr : Channel<JsonObject>
) : BluetoothSocket {

    private var host : String? = null
    private var localPort : Int? = null
    private var remoteHost : String? = null
    private var remotePort : Int? = null

    override suspend fun getHost(): String? {
        return host
    }

    override suspend fun getPort(): Int? {
        return localPort
    }

    override suspend fun getRemoteHost(): String? {
        return remoteHost
    }

    override suspend fun getRemotePort(): Int? {
        return remotePort
    }

    override suspend fun connect(address: String, port: Int) {
    }

    override suspend fun bind(port: Int?) {
        TODO("Not yet implemented")
    }

    override suspend fun listen(backlog: Int) {
        TODO("Not yet implemented")
    }

    override suspend fun accept() {
        TODO("Not yet implemented")
    }

    override suspend fun send(data: ByteArray) {
        TODO("Not yet implemented")
    }

    override suspend fun shutdown() {
        TODO("Not yet implemented")
    }

    override suspend fun receive(bufsize: Int): ByteArray {
        TODO("Not yet implemented")
    }

    override fun close() {
        TODO("Not yet implemented")
    }


}