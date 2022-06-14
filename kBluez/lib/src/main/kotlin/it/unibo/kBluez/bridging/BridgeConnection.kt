package it.unibo.kBluez.bridging

import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import it.unibo.kBluez.socket.BluetoothSocket
import kotlinx.coroutines.*
import mu.KotlinLogging
import java.io.Closeable
import java.nio.ByteBuffer

class BridgeConnection(
    val name : String,
    private val bluetoothSocket : BluetoothSocket,
    private val netSocket : Socket,
    private val coroutineScope : CoroutineScope
) : Closeable, AutoCloseable {

    private val log = KotlinLogging.logger("${javaClass.simpleName}[$name]")
    private lateinit var bluetoothToInet : Job
    private lateinit var inetToBluetooth : Job

    suspend fun start() {
        bluetoothToInet = coroutineScope.launch {
            val netOut = netSocket.openWriteChannel()

            var finished = false
            var received : ByteArray
            log.info("started bluetooth to inet job")
            while(!finished && isActive) {
                try {
                    received = bluetoothSocket.receive()
                    log.info("received ${received.size} bytes from bluetooth")
                    netOut.writeFully(received)
                    log.info("bytes sent to net socket")
                } catch (e : Exception) {
                    finished = true
                    log.catching(e)
                }
            }
            log.info("terminated bluetooth to inet job")
        }

        inetToBluetooth = coroutineScope.launch {
            val netIn = netSocket.openReadChannel()
            var finished = false
            var readed : Int
            var buff : ByteBuffer = ByteBuffer.allocate(1024)

            log.info("started inet to bluetooth job")
            while(!finished && isActive) {
                try {
                    readed = netIn.readAvailable(buff)
                    log.info("received ${readed} bytes from inet")
                    bluetoothSocket.send(buff.array(), 0, readed)
                    buff.clear()
                    log.info("bytes sent to bluetooth")
                } catch (e : Exception) {
                    finished = true
                    log.catching(e)
                }
            }
            log.info("terminated inet to bluetooth job")
        }
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