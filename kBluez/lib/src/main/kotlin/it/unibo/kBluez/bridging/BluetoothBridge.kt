package it.unibo.kBluez.bridging

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import it.unibo.kBluez.KBluezFactory
import it.unibo.kBluez.model.BluetoothServiceProtocol
import it.unibo.kBluez.model.NetworkProtocol
import it.unibo.kBluez.socket.BluetoothSocket
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import mu.KotlinLogging
import java.io.Closeable

class BluetoothBridge(
    val brigeName : String,
    val bluetoothServicePort : Int? = null,
    val bluetoothServiceProtocol: BluetoothServiceProtocol,
    val host : String,
    private val port : Int,
    val netProtocol : NetworkProtocol,
    private val coroutineScope: CoroutineScope,
    private val ioDispatcher : CoroutineDispatcher = Dispatchers.IO,
    val bluetoothServiceName : String? = null,
    val bluetoothServiceUuid : String? = null
) : Closeable, AutoCloseable {

    private val log = KotlinLogging.logger("${javaClass.simpleName}[$brigeName]")

    init {
        if(bluetoothServiceName != null && bluetoothServiceUuid == null)
            throw IllegalArgumentException("The service UUID can not be null if a name was set")
        if(bluetoothServiceName == null && bluetoothServiceUuid != null)
            throw IllegalArgumentException("The service name can not be null if a UUID was set")
    }

    private lateinit var bluetoothSocket : BluetoothSocket
    val connections = mutableMapOf<String, BridgeConnection>()
    private lateinit var job : Job
    private val selectorMgr = SelectorManager(ioDispatcher)

    suspend fun start() {
        job = launchJob()
    }

    suspend fun launchJob() : Job {
        val ackChannel = Channel<Exception?>()
        val job = coroutineScope.launch {

            var startOk = false
            var connection : BridgeConnection
            var netSocket : Socket

            try {
                bluetoothSocket = KBluezFactory
                    .getKBluez(scope = this@BluetoothBridge.coroutineScope)
                    .requestNewSocket(bluetoothServiceProtocol)
                log.info("bluetooth server socket created")
                bluetoothSocket.bind(bluetoothServicePort)
                log.info("bluetooth server socket bind at port ${bluetoothSocket.getLocalPort()}")
                bluetoothSocket.listen()
                log.info("bluetooth server socket listen")

                if(bluetoothServiceName != null && bluetoothServiceUuid != null) {
                    bluetoothSocket.advertiseService(bluetoothServiceName, bluetoothServiceUuid)
                    log.info("advertised service \"$bluetoothServiceName\" with uuid \"$bluetoothServiceUuid\"")
                }
                startOk = true
                ackChannel.send(null)
            } catch (e : Exception) {
                ackChannel.send(e)
                log.catching(e)
                log.error("unable to start bridge $brigeName")
            }

            if(startOk) {
                log.info("starting...")
                var finished = false
                while(!finished) {
                    try {
                        log.info("waiting for bluetooth connections...")
                        bluetoothSocket.acceptCycle {
                            log.info("accepted connection at \"${it.getRemoteHost()}:${it.getRemotePort()}\"")
                            if(netProtocol == NetworkProtocol.TCP) {
                                try {
                                    log.info("creating tcp connection with [host=$host, port=$port]")
                                    netSocket = aSocket(selectorMgr)
                                        .tcp().connect(host, port)
                                    log.info("created TCP connection with context")
                                    connection = BridgeConnection(
                                        "${it.getRemoteHost()}:${it.getRemotePort()}",
                                        it, netSocket,
                                        coroutineScope
                                    )
                                    log.info("bridge connection configured")
                                    connections.put(connection.name, connection)
                                    connection.start()
                                    log.info("bridge started for \"${it.getRemoteHost()}:${it.getRemotePort()}\"")
                                } catch (e : Exception) {
                                    log.catching(e)
                                }
                            }

                            else {
                                log.warn("unsupported net procol \"$netProtocol\": skipped")
                            }
                        }
                    } catch (e : Exception) {
                        log.catching(e)
                        finished = true
                        log.info("terminating...")
                    }
                }

                connections.forEach { it.value.close() }
                connections.clear()
                log.info("closed connections")

            }



        }

        val exception = ackChannel.receive()
        if(exception != null)
            throw exception

        ackChannel.close()
        return job
    }

    suspend fun getPort() : Int? {
        return bluetoothSocket.getLocalPort()
    }

    override fun close() {
        bluetoothSocket.close()
    }


}