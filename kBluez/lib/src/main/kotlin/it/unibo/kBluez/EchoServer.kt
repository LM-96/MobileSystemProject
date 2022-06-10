package it.unibo.kBluez

import it.unibo.kBluez.model.BluetoothServiceProtocol
import it.unibo.kBluez.pybluez.PyBluezWrapperException
import it.unibo.kBluez.pybluez.PyKBluez
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

val tt = "\t\t###### MAIN | "

fun printTt(line : String) {
    println("$tt $line")
}

fun main(args : Array<String>) {
    runBlocking {
        val serverSock = PyKBluez.newBluetoothSocket(BluetoothServiceProtocol.RFCOMM)
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

        try {
            while (true) {
                printTt("accept cycle")
                serverSock.asyncAccept(this) {
                    try {
                        while(true) {
                            send(receive())
                        }
                    } catch (_ : PyBluezWrapperException) {
                        //simply exit
                    }
                }
            }
        } catch (_ : PyBluezWrapperException) {
            println("Closed")
        }
    }
}