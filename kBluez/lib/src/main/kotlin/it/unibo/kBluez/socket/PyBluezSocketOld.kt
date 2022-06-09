package it.unibo.kBluez.socket

import it.unibo.kBluez.model.BluetoothServiceProtocol
import it.unibo.kBluez.pybluez.PyBluezWrapperReader
import it.unibo.kBluez.pybluez.PyBluezWrapperState
import it.unibo.kBluez.pybluez.PyBluezWrapperWriter
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import java.io.InputStream
import java.io.OutputStream

class PyBluezSocketOld internal constructor(
    private val reader : PyBluezWrapperReader,
    private val writer : PyBluezWrapperWriter,
    private val protocol : BluetoothServiceProtocol,
    private val uuid : String,
    private val scope : CoroutineScope = GlobalScope
) {


    companion object {
        internal suspend fun newPyBluezSocket(reader : PyBluezWrapperReader,
                                              writer: PyBluezWrapperWriter,
                                              protocol : BluetoothServiceProtocol,
                                              scope : CoroutineScope = GlobalScope) : PyBluezSocketOld
        {
            writer.writeNewSocketCommand(protocol)
            reader.ensureState(PyBluezWrapperState.CREATING_SOCKET)
            val uuid = reader.readNewSocketUUID()
            reader.ensureState(PyBluezWrapperState.IDLE)

            return PyBluezSocketOld(reader, writer, protocol, uuid, scope)
        }
    }

    var remoteAddress : String? = null
    private set

    var remotePort : Int? = null
    private set

    var localPort : Int? = null
    private set

    private var receiveInternalChan : Channel<ByteArray>? = null
    var receiveChannel : ReceiveChannel<ByteArray>? = null

    private var sendInternalChan : Channel<ByteArray>? = null
    var sendChannel : SendChannel<ByteArray>? = null

    var inputStream : InputStream? = null
    private set

    var outputStream : OutputStream? = null
    private set

    private var job : Job? = null

    private fun startJob() {
        job = scope.launch {
            try {
                while (isActive) {
                    send(sendInternalChan!!.receive())
                }
            } catch (_ : ClosedReceiveChannelException) { }
        }
    }


    suspend fun bind(port : Int) {
        writer.writeSocketBindCommand(uuid, port)
        reader.ensureState(PyBluezWrapperState.BINDING_SOCKET)
        reader.ensureState(PyBluezWrapperState.IDLE)
        localPort = port
    }

    suspend fun listen(backlog : Int = 1) {
        writer.writeSocketListenCommand(uuid, backlog)
        reader.ensureState(PyBluezWrapperState.LISTENING_SOCKET)
        reader.ensureState(PyBluezWrapperState.IDLE)
    }

    suspend fun accept() : PyBluezSocketOld {
        writer.writeSocketAcceptCommand(uuid)
        reader.ensureState(PyBluezWrapperState.ACCEPTING_SOCKET)
        val clientInfo = reader.readSocketAcceptResult()
        reader.ensureState(PyBluezWrapperState.IDLE)

        val clientSock = PyBluezSocketOld(reader, writer, clientInfo.third, clientInfo.first)
        clientSock.remoteAddress = clientInfo.first

        return clientSock
    }

    suspend fun receive(bufsize : Int = 1024) : ByteArray {
        writer.writeSocketReceiveCommand(uuid, bufsize)
        reader.ensureState(PyBluezWrapperState.RECEIVING_SOCKET)
        var received = reader.readSocketReceive()
        reader.ensureState(PyBluezWrapperState.IDLE)
        return received
    }

    suspend fun close() {
        writer.writeSocketCloseCommand(uuid)
        reader.ensureState(PyBluezWrapperState.CLOSING_SOCKET)
        reader.ensureState(PyBluezWrapperState.IDLE)
    }

    suspend fun connect(address : String, port : Int) {
        writer.writeSocketConnectCommand(uuid, address, port)
        reader.ensureState(PyBluezWrapperState.CONNECTING_SOCKET)
        reader.ensureState(PyBluezWrapperState.IDLE)
    }

    suspend fun send(data : ByteArray, offset : Int = 0, length : Int = data.size) {
        writer.writeSocketSendCommand(uuid, data, offset, length)
        reader.ensureState(PyBluezWrapperState.SENDING_SOCKET)
        reader.ensureState(PyBluezWrapperState.IDLE)
    }

}