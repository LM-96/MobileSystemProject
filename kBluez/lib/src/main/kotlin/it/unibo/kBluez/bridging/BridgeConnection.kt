package it.unibo.kBluez.bridging

import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import it.unibo.kBluez.socket.BluetoothSocket
import kotlinx.coroutines.*
import mu.KotlinLogging
import java.io.Closeable

class BridgeConnection(
    val name : String,
    private val bluetoothSocket : BluetoothSocket,
    private val outChannel : ByteWriteChannel,
    private val coroutineScope : CoroutineScope
) : Closeable, AutoCloseable {

    private val log = KotlinLogging.logger("${javaClass.simpleName}[$name]")
    private lateinit var job : Job

    suspend fun start() {
        job = coroutineScope.launch {
            var finished = false
            var received : ByteArray
            log.info("started")
            while(!finished) {
                try {
                    received = bluetoothSocket.receive()
                    log.info("received ${received.size} bytes")
                    outChannel.writeFully(received)
                    log.info("bytes sent to net socket")
                } catch (e : Exception) {
                    finished = true
                    log.catching(e)
                }
            }
            log.info("terminated")
        }
    }

    override fun close() {
        runBlocking(Dispatchers.IO) {
            bluetoothSocket.close()
            outChannel.close()
            withContext(Dispatchers.Default) {
                job.join()
            }
        }
    }

}