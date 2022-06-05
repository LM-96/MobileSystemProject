package it.unibo.kBluez.pybluez

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import it.unibo.kBluez.model.*
import it.unibo.kBluez.utils.addLevelTab
import it.unibo.kBluez.utils.availableText
import it.unibo.kBluez.utils.getNullable
import it.unibo.kBluez.utils.stringReceiveChannel
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.selects.select
import mu.KotlinLogging
import java.time.LocalDate
import java.util.*

class PythonWrapperReader(
    private val wrapperProcess: Process,
    private val scope : CoroutineScope = GlobalScope
) {

    private val pIn = Channel<String>()
    private val pErr = wrapperProcess.errorStream.stringReceiveChannel(scope)
    private val log = KotlinLogging.logger(javaClass.simpleName + "[PID=${wrapperProcess.pid()}]")

    suspend fun readState() : PythonWrapperState = readWhileJsonObjectHasAndMap("state") {
        PythonWrapperState.valueOf(it.get("state").asString.uppercase())
    }

    suspend fun readLookupResult() : BluetoothLookupResult = readWhileJsonObjectHasAndMap("lookup_res") {
        if(it.has("errName")) {
            BluetoothLookupResult.ofError(it.get("errArgs").asString)
        } else BluetoothLookupResult.ofName(it.get("lookup_res").asString)
    }

    suspend fun readScanResult() : List<BluetoothDevice> = readWhileJsonObjectHasAndMap("scan_res") {
        val res = mutableListOf<BluetoothDevice>()
        val devices = it.get("scan_res").asJsonArray
        var info : JsonObject
        for(dev in devices) {
            info = dev.asJsonObject
            res.add(BluetoothDevice(
                info.get("address").asString,
                info.get("name").asString,
                info.get("classCode").asInt))
        }
        res
    }

    suspend fun readFindServicesResult() : List<BluetoothService> = readWhileJsonObjectHasAndMap("find_services_res") {
        val res = mutableListOf<BluetoothService>()
        val devices = it.get("find_services_res").asJsonArray
        var info : JsonObject
        for(dev in devices) {
            info = dev.asJsonObject
            res.add(BluetoothService(
                info.get("host").asString,
                BluetoothServiceProtocol.valueOf(info.get("protocol").asString.uppercase()),
                info.getNullable("port")?.asInt, info.getNullable("name")?.asString,
                info.getNullable("provider")?.asString,
                info.getNullable("service-classes")?.asJsonArray
                    ?.map { c -> c.asString }?.toList() ?: listOf(),
                info.get("profiles")?.asJsonArray?.map { p -> p.asJsonArray }
                    ?.map { p -> BluetoothServiceProfile(p[0].asString, p[1].asInt) }
                    ?.toList() ?: listOf(),
                info.getNullable("service-id")?.asString
            ))
        }
        res
    }

    @Throws(PythonBluezWrapperException::class)
    private suspend fun <T> readWhileJsonObjectHasAndMap(
        key : String,
        mapper : (JsonObject) -> T
    ) : T {
        var jsonEl : JsonElement
        var jsonObj : JsonObject
        var res : T? = null

        while(res == null) {

            jsonEl = jRead()
            //Throw the exception if something goes wrong

            try {
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

        return res
    }

    @Throws(PythonBluezWrapperException::class)
    private suspend fun jRead() : JsonElement {
        var res : JsonElement? = null
        while(res == null) {
            select<Unit> {
                pIn.onReceive {
                    try {
                        res = JsonParser.parseString(it)
                    }
                    catch (_ : JsonParseException) {}
                    catch (_ : JsonSyntaxException) {}
                }

                pErr.onReceive {
                    throw PythonBluezWrapperException("\n${parseJErrors(it).addLevelTab(2)}")
                }
            }
        }

        return res!!
    }

    suspend fun checkErrors() : String? {
        val errs : String?

        try {
            errs = pErr.availableText()
            println("checkErrors() | errors = $errs")
        } catch (_ : Exception) {
            return null
        }

        if(errs == null)
            return null
        return parseJErrors(errs)
    }

    private fun parseJErrors(str : String) : String {
        try {
            val jsonEl = JsonParser.parseString(str)
            val jsonObj = jsonEl.asJsonObject
            if (!jsonObj.has("err"))
                throw PythonBluezWrapperException("Invalid error: $str")

            return jsonObj.get("err").asString.trim()

        } catch (jpe : JsonParseException) {
            throw PythonBluezWrapperException("Invalid error: $str")
        } catch (jse : JsonSyntaxException) {
            throw PythonBluezWrapperException("Invalid error: $str")
        }
    }

}