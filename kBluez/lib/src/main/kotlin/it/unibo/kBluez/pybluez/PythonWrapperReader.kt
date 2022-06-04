package it.unibo.kBluez.pybluez

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import it.unibo.kBluez.model.BluetoothDevice
import mu.KotlinLogging

class PythonWrapperReader(wrapperProcess: Process) {

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

    private fun jRead() : JsonElement {
        var line : String
        var res : JsonElement? = null
        while(res == null) {
            line = reader.readLine()
            log.info("Readed line: $line")
            try {
                res = JsonParser.parseString(line)
            } catch (e : Exception) {
                log.catching(e)
            }
        }
        return res
    }

}