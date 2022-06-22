package it.unibo.kBluez

import it.unibo.kBluez.bridging.BridgeConfigurator
import it.unibo.kBluez.utils.printTt
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.concurrent.thread

fun main(args : Array<String>) {

    runBlocking {
        printTt("loading bridges...")
        BridgeConfigurator.bridgeScope = this
        val bridges = BridgeConfigurator.loadBridgesFromJsonFile()
        printTt("loaded ${bridges.size} bridges")
        val terminationChan = Channel<Unit>()
        launch {
            bridges.forEach {
                printTt("opening bridges...")
                it.value.start()
                printTt("started bridge ${it.key} at port ${it.value.getPort()}")
            }
        }.join()
        printTt("all bridges started")

            /*
        launch {
            printTt("closing bridges...")
            bridges.forEach {
                it.value.close()
                printTt("closed ${it.key}")
            }
        }
        printTt("all bridges closed")*/
    }
}