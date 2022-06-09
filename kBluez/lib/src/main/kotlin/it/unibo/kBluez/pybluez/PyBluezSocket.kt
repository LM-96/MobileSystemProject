package it.unibo.kBluez.pybluez

import it.unibo.kBluez.model.BluetoothServiceProtocol
import it.unibo.kBluez.socket.BluetoothSocket
import kotlinx.coroutines.runBlocking

class PyBluezSocket internal constructor(
    private val uuid : String,
    override val protocol : BluetoothServiceProtocol,
    private val pyKBluez : PyKBluez,
    private val reader : PyBluezWrapperReader,
    private val writer : PyBluezWrapperWriter
) : BluetoothSocket {

    override suspend fun getLocalHost(): String? {
        return try {
            writer.writeSocketGetLocalAddressCommand(uuid)
            reader.readSocketGetAddressResult().first
        } catch (e : Exception) {
            null
        }
    }

    override suspend fun getLocalPort(): Int? {
        return try {
            writer.writeSocketGetLocalAddressCommand(uuid)
            reader.readSocketGetAddressResult().second
        } catch (e : Exception) {
            null
        }
    }

    override suspend fun getRemoteHost(): String? {
        return try {
            writer.writeSocketGetRemoteAddressCommand(uuid)
            reader.readSocketGetAddressResult().first
        } catch (e : Exception) {
            null
        }
    }

    override suspend fun getRemotePort(): Int? {
        return try {
            writer.writeSocketGetRemoteAddressCommand(uuid)
            reader.readSocketGetAddressResult().second
        } catch (e : Exception) {
            null
        }
    }

    override suspend fun connect(address: String, port: Int) {
        writer.writeSocketConnectCommand(uuid, address, port)
        reader.readSocketConnectResponse()
    }

    override suspend fun bind(port: Int?) {
        writer.writeSocketBindCommand(uuid, port)
        reader.readSocketBindResponse()
    }

    override suspend fun listen(backlog: Int) {
        writer.writeSocketListenCommand(uuid, backlog)
        reader.readSocketListenResponse()
    }

    override suspend fun accept() : BluetoothSocket {
        writer.writeSocketAcceptCommand(uuid)
        val clientInfo = reader.readSocketAcceptResult()
        return pyKBluez.instantiateSocket(clientInfo.first, protocol)
    }

    override suspend fun send(data: ByteArray, offset : Int, length : Int) {
        writer.writeSocketSendCommand(uuid, data, offset, length)
        reader.readSocketSendResponse()
    }

    override suspend fun shutdown() {
        writer.writeSocketShutdownCommand(uuid)
        reader.readSocketShutdownResponse()
    }

    override suspend fun receive(bufsize: Int): ByteArray {
        writer.writeSocketReceiveCommand(uuid, bufsize)
        return reader.readSocketReceive()
    }

    override fun close() {
        runBlocking {
            writer.writeSocketCloseCommand(uuid)
            reader.readSocketCloseResponse()
        }
    }


}