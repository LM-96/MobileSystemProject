package it.unibo.kBluez.socket

import it.unibo.kBluez.model.BluetoothServiceProtocol
import it.unibo.kBluez.pybluez.PythonWrapperReader
import it.unibo.kBluez.pybluez.PythonWrapperState
import it.unibo.kBluez.pybluez.PythonWrapperWriter
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import java.io.InputStream
import java.io.OutputStream

class PyBluezSocket internal constructor(
     private val reader : PythonWrapperReader,
     private val writer : PythonWrapperWriter,
     private val protocol : BluetoothServiceProtocol,
     private val uuid : String,
     private val scope : CoroutineScope = GlobalScope
) {


    companion object {
        internal suspend fun newPyBluezSocket(reader : PythonWrapperReader,
                                              writer: PythonWrapperWriter,
                                              protocol : BluetoothServiceProtocol,
                                              scope : CoroutineScope = GlobalScope) : PyBluezSocket
        {
            writer.writeNewSocketCommand(protocol)
            reader.ensureState(PythonWrapperState.CREATING_SOCKET)
            val uuid = reader.readNewSocketUUID()
            reader.ensureState(PythonWrapperState.IDLE)

            return PyBluezSocket(reader, writer, protocol, uuid, scope)
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
        reader.ensureState(PythonWrapperState.BINDING_SOCKET)
        reader.ensureState(PythonWrapperState.IDLE)
        localPort = port
    }

    suspend fun listen(backlog : Int = 1) {
        writer.writeSocketListenCommand(uuid, backlog)
        reader.ensureState(PythonWrapperState.LISTENING_SOCKET)
        reader.ensureState(PythonWrapperState.IDLE)
    }

    suspend fun accept() : PyBluezSocket {
        writer.writeSocketAcceptCommand(uuid)
        reader.ensureState(PythonWrapperState.ACCEPTING_SOCKET)
        val clientInfo = reader.readSocketAcceptResult()
        reader.ensureState(PythonWrapperState.IDLE)

        val clientSock = PyBluezSocket(reader, writer, clientInfo.third, clientInfo.first)
        clientSock.remoteAddress = clientInfo.first

        return clientSock
    }

    suspend fun receive(bufsize : Int = 1024) : ByteArray {
        writer.writeSocketReceiveCommand(uuid, bufsize)
        reader.ensureState(PythonWrapperState.RECEIVING_SOCKET)
        var received = reader.readSocketReceive()
        reader.ensureState(PythonWrapperState.IDLE)
        return received
    }

    suspend fun close() {
        writer.writeSocketCloseCommand(uuid)
        reader.ensureState(PythonWrapperState.CLOSING_SOCKET)
        reader.ensureState(PythonWrapperState.IDLE)
    }

    suspend fun connect(address : String, port : Int) {
        writer.writeSocketConnectCommand(uuid, address, port)
        reader.ensureState(PythonWrapperState.CONNECTING_SOCKET)
        reader.ensureState(PythonWrapperState.IDLE)
        inputStream = BluetoothInputStream(uuid, reader)
        outputStream = BluetoothOutputStream(uuid, writer)
    }

    suspend fun send(data : ByteArray, offset : Int = 0, length : Int = data.size) {
        writer.writeSocketSendCommand(uuid, data, offset, length)
        reader.ensureState(PythonWrapperState.SENDING_SOCKET)
        reader.ensureState(PythonWrapperState.IDLE)
    }

}