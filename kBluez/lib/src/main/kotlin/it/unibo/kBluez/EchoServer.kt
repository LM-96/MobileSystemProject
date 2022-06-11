package it.unibo.kBluez

import it.unibo.kBluez.model.BluetoothServiceProtocol
import kotlinx.coroutines.*

val tt = "\t\t###### MAIN | "

fun printTt(line : String) {
    println("$tt $line")
}

fun main(args : Array<String>) {
    /*Runtime.getRuntime().addShutdownHook(object : Thread() {
        override fun run() {
            KBLUEZ.close()
        }
    })*/
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

        serverSock.asyncAcceptAll(this) {
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