package it.unibo.kBluez.pybluez

import it.unibo.kBluez.model.BluetoothServiceProtocol
import it.unibo.kBluez.socket.BluetoothSocket
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging

class PyBluezSocket internal constructor(
    private val uuid : String,
    override val protocol : BluetoothServiceProtocol,
    private val pyKBluez : PyKBluez,
    private val reader : PyBluezWrapperReader,
    private val writer : PyBluezWrapperWriter
) : BluetoothSocket {

    private val log = KotlinLogging.logger("${javaClass.simpleName}_$uuid")

    override suspend fun getLocalHost(): String? {
        return try {
            log.info("asking for local address...")
            writer.writeSocketGetLocalAddressCommand(uuid)
            val host = reader.readSocketGetLocalAddressResult().first
            log.info("received address: $host")
            host
        } catch (e : Exception) {
            log.catching(e)
            null
        }
    }

    override suspend fun getLocalPort(): Int? {
        return try {
            log.info("asking for local port...")
            writer.writeSocketGetLocalAddressCommand(uuid)
            val port = reader.readSocketGetLocalAddressResult().second
            log.info("received port: $port")
            port
        } catch (e : Exception) {
            log.catching(e)
            null
        }
    }

    override suspend fun getRemoteHost(): String? {
        return try {
            log.info("asking for remote host...")
            writer.writeSocketGetRemoteAddressCommand(uuid)
            val host = reader.readSocketGetRemoteAddressResult().first
            log.info("received address: $host")
            host
        } catch (e : Exception) {
            log.catching(e)
            null
        }
    }

    override suspend fun getRemotePort(): Int? {
        return try {
            log.info("asking for remote port...")
            writer.writeSocketGetRemoteAddressCommand(uuid)
            val port = reader.readSocketGetRemoteAddressResult().second
            log.info("received port: $port")
            port
        } catch (e : Exception) {
            log.catching(e)
            null
        }
    }

    override suspend fun connect(address: String, port: Int) {
        log.info("connecting...")
        writer.writeSocketConnectCommand(uuid, address, port)
        val res = reader.readSocketConnectResponse()
        log.info("connect response: $res")
    }

    override suspend fun bind(port: Int?) {
        log.info("binding...")
        writer.writeSocketBindCommand(uuid, port)
        val res = reader.readSocketBindResponse()
        log.info("bind response: $res")
    }

    override suspend fun listen(backlog: Int) {
        log.info("listening...")
        writer.writeSocketListenCommand(uuid, backlog)
        val res = reader.readSocketListenResponse()
        log.info("listen response: $res")
    }

    override suspend fun accept() : BluetoothSocket {
        log.info("accepting...")
        writer.writeSocketAcceptCommand(uuid)
        val clientInfo = reader.readSocketAcceptResult()
        log.info("accepted connection: $clientInfo")
        return pyKBluez.instantiateSocket(clientInfo.first, protocol)
    }

    override suspend fun send(data: ByteArray, offset : Int, length : Int) {
        log.info("sending ${length - offset} bytes of data...")
        writer.writeSocketSendCommand(uuid, data, offset, length)
        val res = reader.readSocketSendResponse()
        log.info("send response: $res")
    }

    override suspend fun shutdown() {
        log.info("shutting down...")
        writer.writeSocketShutdownCommand(uuid)
        val res = reader.readSocketShutdownResponse()
        log.info("shudown response: $res")
    }

    override suspend fun receive(bufsize: Int): ByteArray {
        log.info("receiving data [bufsize=$bufsize]...")
        writer.writeSocketReceiveCommand(uuid, bufsize)
        val res = reader.readSocketReceive()
        log.info("received $res bytes")
        return res
    }

    override suspend fun advertiseService(name: String, uuid: String) {
        log.info("advertising service [name=$name, uuid=$uuid]...")
        writer.writeSocketAdvertiseService(this.uuid, name, uuid)
        val res = reader.readSocketAdvertiseServiceResult()
        log.info("advertise service res: $res")
    }

    override suspend fun stopAdvertising() {
        log.info("stop advertising service...")
        writer.writeSocketStopAdvertising(uuid)
        val res = reader.readSocketStopAdvertisingResult()
        log.info("stop advertising res: $res")
    }

    override fun close() {
        runBlocking {
            log.info("closing...")
            writer.writeSocketCloseCommand(uuid)
            val res = reader.readSocketCloseResponse()
            log.info("close result: $res")
        }
    }


}