package it.unibo.kBluez

import it.unibo.kBluez.bridging.BridgeConfigurator
import it.unibo.kBluez.utils.printTt
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.concurrent.thread

fun main(args : Array<String>) {
    printTt("loading bridges...")
    val bridges = BridgeConfigurator.loadBridgesFromJsonFile()
    printTt("loaded ${bridges.size} bridges")
    /*Runtime.getRuntime().addShutdownHook(thread {
        bridges.forEach {
            it.value.close()
        }
    })*/

    runBlocking {
        launch {
            bridges.forEach {
                printTt("opening bridges...")
                it.value.start()
                printTt("started bridge ${it.key} at port ${it.value.getPort()}")
            }
        }.join()
        printTt("all bridges started")
        printTt("ENTER to exit")
        readln()
        launch {
            printTt("closing bridges...")
            bridges.forEach {
                it.value.close()
                printTt("closed ${it.key}")
            }
        }
        printTt("all bridges closed")
    }
}