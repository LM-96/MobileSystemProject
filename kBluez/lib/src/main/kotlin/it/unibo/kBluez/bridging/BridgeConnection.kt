package it.unibo.kBluez.bridging

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import it.unibo.kBluez.socket.BluetoothSocket
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.getAndUpdate
import mu.KotlinLogging
import java.io.Closeable
import java.nio.ByteBuffer

enum class BridgeState {
    STARTING, DISCONNECTED, CONNECTED, RECONNECTING, TERMINATED
}

enum class BridgeJobState {
    STARTED, LISTENING, RECEIVED, BRIDGED
}

class BridgeConnection(
    val name : String,
    private val bluetoothSocket : BluetoothSocket,
    private val inetAddress: SocketAddress,
    private val coroutineScope : CoroutineScope
) : Closeable, AutoCloseable {

    companion object {
        val RECONNECTION_ATTEMPTS = 5
        val RECONNECTION_DELAY = 1000L
    }

    private val selectorMgr = SelectorManager(Dispatchers.IO)
    private val log = KotlinLogging.logger("${javaClass.simpleName}[$name]")
    private lateinit var bluetoothToInet : Job
    private lateinit var inetToBluetooth : Job
    private var netSocketBuilder = aSocket(selectorMgr).tcp()
    private lateinit var netSocket : Socket
    private val bridgeState = MutableStateFlow(BridgeState.STARTING)

    suspend fun start() {
        coroutineScope.launch {
            netSocket = netSocketBuilder.connect(inetAddress)
        }.join()
        //Read from bluetooth and write to inet
        bluetoothToInet = coroutineScope.launch {
            bridgeState.emit(BridgeState.CONNECTED)
            var netOut = netSocket.openWriteChannel(true)

            var finished = false
            var received : ByteArray
            var bJobState = BridgeJobState.STARTED
            var string : String
            log.info("started bluetooth to inet job")
            while(!finished && isActive) {
                try {
                    bJobState = BridgeJobState.LISTENING
                    received = bluetoothSocket.receive()
                    bJobState = BridgeJobState.RECEIVED
                    string = received.decodeToString().trim()
                    log.info("received ${received.size} bytes from bluetooth [string: \"$string\"")
                    netOut.writeStringUtf8("$string\n")
                    netOut.flush()
                    bJobState = BridgeJobState.BRIDGED
                    log.info("bytes sent to net socket")
                } catch (e : Exception) {
                    log.catching(e)
                    if(bJobState == BridgeJobState.RECEIVED) {
                        log.info("[BT2INET] Connection is probably closed by the net socket. Trying to reconnect")
                        //Exception thrown by the net socket
                        if(awaitReconnection(RECONNECTION_ATTEMPTS,
                                RECONNECTION_DELAY))
                            netOut = netSocket.openWriteChannel(true)
                        else
                            finished = false
                    }
                }
            }
            log.info("terminated bluetooth to inet job")
        }

        inetToBluetooth = coroutineScope.launch {
            var netIn = netSocket.openReadChannel()
            var finished = false
            var readed : Int
            var buff : ByteBuffer = ByteBuffer.allocate(1024)
            var bJobState = BridgeJobState.STARTED

            log.info("started inet to bluetooth job")
            while(!finished && isActive) {
                try {
                    bJobState = BridgeJobState.LISTENING
                    readed = netIn.readAvailable(buff)
                    bJobState = BridgeJobState.RECEIVED
                    log.info("received ${readed} bytes from inet")
                    bluetoothSocket.send(buff.array(), 0, readed)
                    bJobState = BridgeJobState.BRIDGED
                    buff.clear()
                    log.info("bytes sent to bluetooth")
                } catch (e : Exception) {
                    log.catching(e)
                    if(bJobState == BridgeJobState.LISTENING) {
                        log.info("[INET2BT] Connection is probably closed by the net socket. Trying to reconnect")
                        //Exception thrown by the net socket
                        if(awaitReconnection(RECONNECTION_ATTEMPTS,
                                RECONNECTION_DELAY))
                            netIn = netSocket.openReadChannel()
                        else
                            finished = false
                    }
                }
            }
            log.info("terminated inet to bluetooth job")
        }
    }

    private suspend fun awaitReconnection(attempts: Int, delay: Long) : Boolean {
        if(bridgeState.compareAndSet(BridgeState.CONNECTED, BridgeState.RECONNECTING)) {
            log.info("Performing reconnection...")
            var afterReconnect = doReconnect(attempts, delay)
            log.info("Reconnection procedure end with state \'$afterReconnect\'")
            bridgeState.emit(afterReconnect)
        }
        return bridgeState
            .first { it == BridgeState.CONNECTED || it == BridgeState.DISCONNECTED } == BridgeState.CONNECTED
    }

    private suspend fun doReconnect(attempts : Int, delay : Long) : BridgeState {
        var attempt = 1
        var netSocket : Socket? = null
        while (attempt <= attempts && netSocket == null) {
            log.info("trying to reconnect [attempt=$attempt]")
            try {
                netSocket = netSocketBuilder.connect(inetAddress)
            } catch (e : Exception) {
                log.info("attempt failed ")
                attempt++
                delay(delay)
            }
        }

        if(netSocket != null) {
            log.info("reconnected")
            return BridgeState.CONNECTED
        }

        return BridgeState.DISCONNECTED
    }

    override fun close() {
        runBlocking(Dispatchers.IO) {
            bluetoothSocket.close()
            netSocket.close()
            withContext(Dispatchers.Default) {
                bluetoothToInet.join()
            }
        }
    }

}