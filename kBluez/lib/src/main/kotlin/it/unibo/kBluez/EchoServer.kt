package it.unibo.kBluez

import it.unibo.kBluez.model.BluetoothServiceProtocol
import it.unibo.kBluez.utils.printTt
import kotlinx.coroutines.*
import kotlin.concurrent.thread

fun main(args : Array<String>) {
    runBlocking {
        /*Runtime.getRuntime().addShutdownHook(thread {
            this.cancel("Shutdown Hook")
        })*/

        val serverSock = newBluetoothSocket(BluetoothServiceProtocol.RFCOMM)
        printTt("created server socket")

        serverSock.bind()
        val localHost = serverSock.getLocalHost()
        val localPort = serverSock.getLocalPort()
        printTt("My address: $localHost")
        printTt("My port: $localPort")

        serverSock.listen()
        printTt("listen ok")

        val serviceUuid = "f8fb1e57-f72f-4098-9b66-61865cb5fd9f"
        serverSock.advertiseService("Echo Server", serviceUuid)
        printTt("advertise ok")

        serverSock.acceptAndLaunchCycle(this) {
            printTt("Accepted connection with ${it.getLocalHost()}:${it.getLocalPort()}")
            printTt("Active actors : ${KBLUEZ.activeActors()}")
            printTt("Active threads : ${Thread.activeCount()}")
            withAcceptedSocket {
                var received : String
                var working = true
                while (working && isActive) {
                    try {
                        received = this.receive().decodeToString()
                        printTt("Socket[${getLocalHost()}:${getLocalPort()}] received data: $received")
                        send(received.toByteArray())
                    } catch (e : Exception) {
                        working = false
                    }
                }
            }
        }
        this.cancel()
    }
}