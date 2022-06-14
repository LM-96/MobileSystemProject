package it.unibo.kBluez

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

fun main(args : Array<String>) {
    var counter = 1
    val selectorMgr = SelectorManager(Dispatchers.IO)
    runBlocking {
        val basicRobotSock = aSocket(selectorMgr).tcp().connect("169.254.130.110", 8020)
        basicRobotSock.openWriteChannel(true)
    }
}