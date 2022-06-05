package it.unibo.kBluez.pybluez

import com.google.gson.JsonObject
import it.unibo.kBluez.utils.stringSendChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import mu.KotlinLogging
import java.util.UUID

class PythonWrapperWriter(
    private val wrapperProcess : Process,
    scope : CoroutineScope = GlobalScope
) {

    //private val writer = wrapperProcess.outputStream.bufferedWriter()
    private val pOut = wrapperProcess.outputStream.stringSendChannel(scope)
    private val log = KotlinLogging.logger(javaClass.simpleName + "[PID=${wrapperProcess.pid()}]")

    suspend fun writeCommand(cmd : String, vararg args : Pair<String, String>) {
        if(!wrapperProcess.isAlive)
            throw PythonBluezWrapperException("Unable to write to wrapper: process is closed")

        val jsonCmd = JsonObject()
        jsonCmd.addProperty("cmd", cmd)
        for(arg in args)
            jsonCmd.addProperty(arg.first, arg.second)
        log.info("writeCommand() | jsonCmd = $jsonCmd")
        pOut.send(jsonCmd.toString())
    }

    suspend fun writeScanCommand() {
        writeCommand(Commands.SCAN_CMD)
    }

    suspend fun writeLookupCommand(address : String) {
        writeCommand(Commands.LOOKUP_CMD, "address" to address)
    }

    suspend fun writeTerminateCommand() {
        writeCommand(Commands.TERMINATE_CMD)
    }

    suspend fun writeFindServicesCommand(name : String? = null,
                                 uuid : UUID? = null,
                                 address : String? = null) {
        val args = mutableListOf<Pair<String, String>>()
        if(name != null)
            args.add("name" to name)
        if(uuid != null)
            args.add("uuid" to uuid.toString())
        if(address != null)
            args.add("address" to address)
        writeCommand(Commands.FIND_SERVICES, *(args.toTypedArray()))
    }

}