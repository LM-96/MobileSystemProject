package it.unibo.kBluez.pybluez

import com.google.gson.JsonObject
import java.io.OutputStream

class PythonWrapperWriter(process : Process) {

    private val writer = process.outputStream.bufferedWriter()

    fun writeCommand(cmd : String, vararg args : Pair<String, String>) {
        val jsonCmd = JsonObject()
        jsonCmd.addProperty("cmd", cmd)
        for(arg in args)
            jsonCmd.addProperty(arg.first, arg.second)

        writer.write(jsonCmd.toString())
        writer.newLine()
        writer.flush()
    }

    fun writeScanCommand() {
        writeCommand(Commands.SCAN_CMD)
    }

    fun writeLookupCommand(address : String) {
        writeCommand(Commands.LOOKUP_CMD, "address" to address)
    }

    fun writeTerminateCommand() {
        writeCommand(Commands.TERMINATE_CMD)
    }

}