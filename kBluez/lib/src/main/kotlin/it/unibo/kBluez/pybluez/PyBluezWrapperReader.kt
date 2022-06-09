package it.unibo.kBluez.pybluez

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import it.unibo.kBluez.model.*
import it.unibo.kBluez.utils.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.selects.select
import mu.KotlinLogging
import java.io.Closeable
import java.nio.charset.StandardCharsets

class PyBluezWrapperReader(
    private val pIn : ReceiveChannel<JsonObject>,
    private val pErr : ReceiveChannel<JsonObject>
) : Closeable, AutoCloseable {

    private val log = KotlinLogging.logger(javaClass.simpleName + "[${hashCode()}]")

    suspend fun readState() : PyBluezWrapperState = readWhileJsonObjectHasAndMap("state") {
        PyBluezWrapperState.valueOf(it.get("state").asString.uppercase())
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

    suspend fun readSocketReceive() : ByteArray =
        readWhileJsonObjectHasAndMap("received_data") {
            it.get("received_data").asString
            .substring(0, it.get("size").asInt)
            .toByteArray(StandardCharsets.UTF_8)
    }


    suspend fun readNewSocketUUID() : String =
        readWhileJsonObjectHasAndMap("new_socket_uuid") {
            it.get("new_socket_uuid").asString
        }

    suspend fun readSocketAcceptResult() :
            Triple<String, String, BluetoothServiceProtocol> =
        readWhileJsonObjectHasAndMap("client_sock_uuid") {
            val jsonObj = it.asJsonObject
            Triple(it.get("client_sock_uuid").asString,
                it.get("client_address").asString,
                BluetoothServiceProtocol.valueOf(it.get("client_proto").asString.uppercase())
            )
        }

    @Throws(PyBluezWrapperException::class)
    private suspend fun <T> readWhileJsonObjectHasAndMap(
        key : String,
        mapper : (JsonObject) -> T
    ) : T {
        var jsonEl : JsonElement
        var jsonObj : JsonObject
        var res : T? = null

        while(res == null) {

            select<Unit> {
                pIn.onReceive { jsonObj ->
                    if(jsonObj.has(key)) {
                        res = mapper(jsonObj)
                    }
                }

                pErr.onReceive {
                    throw PyBluezWrapperException("\n${parseJErrors(it).addLevelTab(2)}")
                }
            }

        }
        return res!!
    }

    suspend fun checkErrors() : String? {
        val errs : String?

        if(pErr.isEmpty) {
            return null
        }

        return parseJErrors(pErr.receive())
    }

    private fun parseJErrors(jsonObj: JsonObject) : String {
        if (!jsonObj.has("err"))
            throw PyBluezWrapperException("Invalid error: $jsonObj")

        return "${jsonObj.get("source").asString.trim()}: ${jsonObj.get("err").asString.trim()}"
    }

    suspend fun ensureState(expected : PyBluezWrapperState) {
        val last = readState()
        if(last != expected) {
            throw PyBluezWrapperException("Unexpected state $last [expected = $expected]")
        }
    }

    override fun close() {
        runBlocking {
            pIn.cancel()
            pErr.cancel()
        }
    }

}