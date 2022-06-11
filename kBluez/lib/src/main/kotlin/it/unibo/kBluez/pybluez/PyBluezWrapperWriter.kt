package it.unibo.kBluez.pybluez

import com.google.gson.JsonObject
import it.unibo.kBluez.model.BluetoothServiceProtocol
import it.unibo.kBluez.utils.stringSendChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.SendChannel
import mu.KotlinLogging
import java.io.Closeable
import java.nio.charset.StandardCharsets
import java.util.UUID

class PyBluezWrapperWriter(
    private val pOut : SendChannel<JsonObject>,
    scope : CoroutineScope = GlobalScope
) : Closeable, AutoCloseable {

    private val log = KotlinLogging.logger(javaClass.simpleName + "[${hashCode()}]")

    suspend fun writeCommand(cmd : String, vararg args : Pair<String, String>) {
        if(pOut.isClosedForSend)
            throw PyBluezWrapperException("This writer has been closed")

        val jsonCmd = JsonObject()
        jsonCmd.addProperty("cmd", cmd)
        for(arg in args)
            jsonCmd.addProperty(arg.first, arg.second)
        log.info("writeCommand() | jsonCmd = $jsonCmd")
        pOut.send(jsonCmd)
    }

    suspend fun writeScanCommand() {
        writeCommand(Commands.SCAN_CMD)
    }

    suspend fun writeLookupCommand(address : String) {
        writeCommand(Commands.LOOKUP_CMD, "address" to address)
    }

    suspend fun writeTerminateCommand() {
        writeCommand(Commands.TERMINATE_CMD)
    }

    suspend fun writeNewSocketCommand(protocol : BluetoothServiceProtocol) {
        writeCommand(Commands.NEW_SOCKET_CMD, "protocol" to protocol.name)
    }

    suspend fun writeGetAvailablePortCommand(protocol: BluetoothServiceProtocol) {
        writeCommand(Commands.GET_AVAILABLE_PORT, "protocol" to protocol.name)
    }

    suspend fun writeSocketBindCommand(uuid : String, port : Int?) {
        if(port != null)
            writeCommand(Commands.SOCKET_BIND_CMD, "sock_uuid" to uuid,
                "port" to port.toString())
        else
            writeCommand(Commands.SOCKET_BIND_CMD, "sock_uuid" to uuid)
    }

    suspend fun writeSocketListenCommand(uuid: String, backlog : Int? = null) {
        if(backlog != null)
            writeCommand(Commands.SOCKET_LISTEN_CMD, "sock_uuid" to uuid,
                "backlog" to backlog.toString())
        else
            writeCommand(Commands.SOCKET_LISTEN_CMD, "sock_uuid" to uuid)
    }

    suspend fun writeSocketAcceptCommand(uuid : String) {
        writeCommand(Commands.SOCKET_ACCEPT_CMD, "sock_uuid" to uuid)
    }

    suspend fun writeSocketReceiveCommand(uuid : String, bufsize : Int = 1024) {
        writeCommand(Commands.SOCKET_RECEIVE_CMD, "sock_uuid" to uuid,
            "bufsize" to bufsize.toString())
    }

    suspend fun writeSocketCloseCommand(uuid : String) {
        writeCommand(Commands.SOCKET_CLOSE_CMD, "sock_uuid" to uuid)
    }

    suspend fun writeSocketShutdownCommand(uuid : String) {
        writeCommand(Commands.SOCKET_SHUTDOWN_CMD, "sock_uuid" to uuid)
    }

    suspend fun writeSocketConnectCommand(uuid : String, address : String, port : Int) {
        writeCommand(Commands.SOCKET_CONNECT_CMD, "sock_uuid" to uuid,
            "address" to address, "port" to port.toString())
    }

    suspend fun writeSocketSendCommand(uuid : String, data : ByteArray,
                                       offset : Int = 0,
                                       length : Int = data.size) {

        writeCommand(Commands.SOCKET_SEND_CMD, "sock_uuid" to uuid,
            "data" to String(
                data.sliceArray(offset until offset+length), StandardCharsets.UTF_8)
        )
    }

    suspend fun writeGetActiveActorsCommand() {
        writeCommand(Commands.GET_ACTIVE_ACTORS)
    }

    suspend fun writeSocketSetL2CapMtuCommand(uuid: String, mtu : Int) {
        writeCommand(Commands.SOCKET_SET_L2CAP_MTU_CMD, "sock_uuid" to uuid, "mtu" to mtu.toString())
    }

    suspend fun writeSocketGetLocalAddressCommand(uuid: String) {
        writeCommand(Commands.SOCKET_GET_LOCAL_ADDRESS_CMD, "sock_uuid" to uuid)
    }

    suspend fun writeSocketGetRemoteAddressCommand(uuid: String) {
        writeCommand(Commands.SOCKET_GET_REMOTE_ADDRESS_CMD, "sock_uuid" to uuid)
    }

    suspend fun writeSocketAdvertiseService(uuid: String, serviceName : String, serviceUuid : String) {
        writeCommand(Commands.SOCKET_ADVERTISE_SERVICE_CMD, "sock_uuid" to uuid,
            "service_name" to serviceName, "service_uuid" to serviceUuid
        )
    }

    suspend fun writeSocketStopAdvertising(uuid: String) {
        writeCommand(Commands.SOCKET_STOP_ADVERTISING_CMD, "sock_uuid" to uuid
        )
    }

    suspend fun writeSocketStopAdvertisingCommand(uuid : String) {
        writeCommand(Commands.SOCKET_STOP_ADVERTISING_CMD, "sock_uuid" to uuid)
    }

    suspend fun writeFindServicesCommand(name : String? = null,
                                 uuid : UUID? = null,
                                 address : String? = null) {
        val args = mutableListOf<Pair<String, String>>()
        if(name != null)
            args.add("name" to name)
        if(uuid != null)
            args.add("uuid" to uuid.toString())
        if(address != null)
            args.add("address" to address)
        writeCommand(Commands.FIND_SERVICES, *(args.toTypedArray()))
    }

    override fun close() {
        pOut.close()
    }

}