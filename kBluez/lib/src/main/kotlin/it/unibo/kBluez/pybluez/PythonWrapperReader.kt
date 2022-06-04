package it.unibo.kBluez.pybluez

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import it.unibo.kBluez.model.BluetoothDevice
import it.unibo.kBluez.model.BluetoothService
import it.unibo.kBluez.model.BluetoothServiceProfile
import it.unibo.kBluez.model.BluetoothServiceProtocol
import mu.KotlinLogging
import java.util.*

class PythonWrapperReader(private val wrapperProcess: Process) {

    private val reader = wrapperProcess.inputStream.bufferedReader()
    private val log = KotlinLogging.logger(javaClass.simpleName + "[PID=${wrapperProcess.pid()}]")

    fun readState() : PythonWrapperState {
        var jsonEl : JsonElement
        var jsonObj : JsonObject
        var res : PythonWrapperState? = null

        while(res == null) {
            try {
                jsonEl = jRead()
                if(jsonEl.isJsonObject) {
                    jsonObj = jsonEl.asJsonObject
                    if (jsonObj.has("state"))
                        res = PythonWrapperState.valueOf(jsonObj.get("status").asString.uppercase())
                }
            } catch (e : Exception) {
                log.catching(e)
                //Simply ignore the readed value
            }
        }

        return res
    }

    fun readLookupResult() : String {

        var jsonEl : JsonElement
        var jsonObj : JsonObject
        var res : String? = null

        while(res == null) {
            if(!wrapperProcess.isAlive)
                throw PythonBluezWrapperException("Unable to read from wrapper: process is closed")

            try {
                jsonEl = jRead()
                if(jsonEl.isJsonObject) {
                    jsonObj = jsonEl.asJsonObject
                    if (jsonObj.has("lookup_res")) {
                        if(jsonObj.has("errName")) {
                            throw PythonBluezWrapperException("Error on lookup: [${jsonObj.get("errName")}: " +
                                    "${jsonObj.get("errArgs")}")
                        }
                        res = jsonObj.get("lookup_res").asString
                    }

                }
            } catch (e : Exception) {
                log.catching(e)
                //Simply ignore the readed value
            }
        }

        return res
    }

    fun readScanResult() : List<BluetoothDevice> {
        var jsonEl : JsonElement
        var jsonObj : JsonObject
        var res : List<BluetoothDevice>? = null

        while(res == null) {
            if(!wrapperProcess.isAlive)
                throw PythonBluezWrapperException("Unable to read from wrapper: process is closed")

            try {
                jsonEl = jRead()
                if(jsonEl.isJsonObject) {
                    jsonObj = jsonEl.asJsonObject
                    if (jsonObj.has("scan_res")) {
                        res = mutableListOf<BluetoothDevice>()
                        val devices = jsonObj.get("scan_res").asJsonArray
                        var info : JsonObject
                        for(dev in devices) {
                            info = dev.asJsonObject
                            res.add(BluetoothDevice(
                                info.get("address").asString,
                                info.get("name").asString,
                                info.get("classCode").asInt))
                        }
                    }

                }
            } catch (e : Exception) {
                log.catching(e)
                //Simply ignore the readed value
            }
        }

        return res
    }

    fun readFindServicesResult() : List<BluetoothService> {
        var jsonEl : JsonElement
        var jsonObj : JsonObject
        var res : List<BluetoothService>? = null

        while(res == null) {
            if(!wrapperProcess.isAlive)
                throw PythonBluezWrapperException("Unable to read from wrapper: process is closed")

            try {
                jsonEl = jRead()
                if(jsonEl.isJsonObject) {
                    jsonObj = jsonEl.asJsonObject
                    if (jsonObj.has("scan_res")) {
                        res = mutableListOf<BluetoothService>()
                        val devices = jsonObj.get("find_services_res").asJsonArray
                        var info : JsonObject
                        for(dev in devices) {
                            info = dev.asJsonObject
                            res.add(BluetoothService(
                                info.get("host").asString,
                                BluetoothServiceProtocol.valueOf(info.get("protocol").asString.uppercase()),
                                info.get("port")?.asInt, info.get("name")?.asString,
                                info.get("provider")?.asString, UUID.fromString(info.get("service-classes")?.asString),
                                info.get("profiles")?.asJsonArray?.map { it.asJsonArray }
                                    ?.map { BluetoothServiceProfile(UUID.fromString(it[0].asString), it[1].asInt) }
                                    ?.toList() ?: mutableListOf(),
                                info.get("service-id")?.asString
                            ))
                        }
                    }

                }
            } catch (e : Exception) {
                log.catching(e)
                //Simply ignore the readed value
            }
        }

        return res
    }

    private fun <T> readWhileJsonObjectHasAndMap(
        key : String,
        mapper : (JsonObject) -> T
    ) : T {
        var jsonEl : JsonElement
        var jsonObj : JsonObject
        var res : T? = null
        readErrors()

        while(res == null && wrapperProcess.isAlive) {
            try {
                jsonEl = jRead()
                if(jsonEl.isJsonObject) {
                    jsonObj = jsonEl.asJsonObject
                    if(jsonObj.has(key)) {
                        res = mapper(jsonObj)
                    }
                }
            } catch (e : Exception) {
                log.catching(e)
            }

        }

        if (res == null && !wrapperProcess.isAlive)
            throw PythonBluezWrapperException("Unable to read from wrapper: process is closed")
        val errors = readErrors()
        if(readErrors() != null)
            throw PythonBluezWrapperException("Errors executing python: $errors")
        return res!!
    }

    private fun jRead() : JsonElement {
        var line : String = ""
        var res : JsonElement? = null
        while(res == null && wrapperProcess.isAlive) {
            try {
                line = reader.readLine()
                log.info("Readed line: $line")
                res = JsonParser.parseString(line)
            } catch (e : Exception) {
                log.catching(e)
                if(!wrapperProcess.isAlive) {
                    log.error("Process exited. Exit Code = ${wrapperProcess.exitValue()}")
                    val errors = readErrors()
                    if(errors != null)
                        log.error("Errors executing python: $errors")
                }
            }
        }
        if(res == null && !wrapperProcess.isAlive)
            throw PythonBluezWrapperException("Unable to read from process output. Unexpected close")

        return res!!
    }

    private fun readErrors() : String? {
        val res = wrapperProcess.errorStream
            .bufferedReader()
            .readLines()
            .joinToString("\n")
        if(res.isBlank())
            return null

        return res
    }

}