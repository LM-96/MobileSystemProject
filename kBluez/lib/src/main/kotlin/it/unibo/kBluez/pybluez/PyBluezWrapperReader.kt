package it.unibo.kBluez.pybluez

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import it.unibo.kBluez.model.*
import it.unibo.kBluez.utils.*
import kotlinx.coroutines.channels.ChannelResult
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.selects.select
import mu.KotlinLogging
import java.io.Closeable
import java.io.IOException
import java.nio.charset.StandardCharsets

class PyBluezWrapperReader(
    private val pIn : ReceiveChannel<JsonObject>,
    private val pErr : ReceiveChannel<JsonObject>
) : Closeable, AutoCloseable {

    private val log = KotlinLogging.logger(javaClass.simpleName + "[${hashCode()}]")

    @Throws(PyBluezWrapperException::class)
    suspend fun ensureState(expected : PyBluezWrapperState) {
        val last = readState()
        if(last != expected) {
            throw PyBluezWrapperException("Unexpected state $last [expected = $expected]")
        }
    }

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

    suspend fun readSocketConnectResponse() : String =
        readWhileJsonObjectHasAndMap("connect_res") {
            it.get("connect_res").asString
        }

    suspend fun readSocketBindResponse() : String =
        readWhileJsonObjectHasAndMap("bind_res") {
            it.get("bind_res").asString
        }

    suspend fun readSocketListenResponse() : String =
        readWhileJsonObjectHasAndMap("listen_res") {
            it.get("listen_res").asString
        }

    suspend fun readSocketSendResponse() : String =
        readWhileJsonObjectHasAndMap("sent_res") {
            it.get("sent_res").asString
        }

    suspend fun readSocketShutdownResponse() : String =
        readWhileJsonObjectHasAndMap("shutdown_res") {
            it.get("shutdown_res").asString
        }

    suspend fun readSocketCloseResponse() : String =
        readWhileJsonObjectHasAndMap("close_res") {
            it.get("close_res").asString
        }

    suspend fun readSocketReceive() : ByteArray =
        readWhileJsonObjectHasAndMap("receive_res") {
            it.get("receive_res").asString
            .substring(0, it.get("size").asInt)
            .toByteArray(StandardCharsets.UTF_8)
    }

    suspend fun readGetActiveActorsRes() : Int =
        readWhileJsonObjectHasAndMap("active_actors_res") {
            it.get("active_actors_res").asString.toInt()
        }


    suspend fun readNewSocketUUID() : String =
        readWhileJsonObjectHasAndMap("new_socket_uuid") {
            it.get("new_socket_uuid").asString
        }

    suspend fun readSocketAcceptResult() :
            Triple<String, String, Int> =
        readWhileJsonObjectHasAndMap("accept_res") {
            Triple(it.get("accept_res").asString,
                it.get("accept_res_address").asString,
                it.get("accept_res_port").asInt
            )
        }

    suspend fun readSocketGetLocalAddressResult()  =
        readWhileJsonObjectHasAndMap("sock_local_address") {
            Pair(it.get("sock_local_address").asString, it.get("sock_local_port").asInt)
        }

    suspend fun readSocketGetRemoteAddressResult()  =
        readWhileJsonObjectHasAndMap("sock_remote_address") {
            Pair(it.get("sock_remote_address").asString, it.get("sock_remote_port").asInt)
        }

    suspend fun readSocketAdvertiseServiceResult() : String =
        readWhileJsonObjectHasAndMap("advertise_service_res") {
            it.get("advertise_service_res").asString
        }

    suspend fun readSocketStopAdvertisingResult() : String =
        readWhileJsonObjectHasAndMap("stop_asdvertising_res") {
            it.get("stop_asdvertising_res").asString
        }

    suspend fun readGetAvailablePort() : Int =
        readWhileJsonObjectHasAndMap("available_port") {
            it.get("available_port").asString.toInt()
        }



    @Throws(PyBluezWrapperException::class)
    private suspend fun <T> readWhileJsonObjectHasAndMap(
        key : String,
        mapper : (JsonObject) -> T
    ) : T {
        var jsonEl : JsonElement
        var jsonObj : JsonObject
        var res : T? = null
        var errs = checkErrors()
        if(errs != null)
            throw PyBluezWrapperException(errs)
            //consume some remaining errors

        while(res == null) {

            var exception : Exception? = null
            select<Unit> {
                pIn.onReceive { jsonObj ->
                    if(jsonObj.has(key)) {
                        res = mapper(jsonObj)
                    }
                }

                pErr.onReceive {
                    exception = errorStringToException("\n${parseJErrors(it).addLevelTab(2)}")
                }
            }
            if(exception != null) {
                throw exception!!
            }


        }
        return res!!
    }

    private fun errorStringToException(string : String) : Exception {
        return if(string.contains("BluetoothError")) {
            IOException(string)
        } else {
            PyBluezWrapperException(string)
        }
    }

    fun checkErrors() : String? {
        return pErr.tryReceive().getOrNull()?.parseErrors()
    }

    fun checkAllErrors() : List<String> {
        var receiveRes : ChannelResult<JsonObject>
        val res = mutableListOf<String>()
        do {
            receiveRes = pErr.tryReceive()
            if(receiveRes.isSuccess)
                res.add(receiveRes.getOrNull()!!.parseErrors())
        } while (receiveRes.isSuccess)

        return res
    }

    suspend fun skipRemaining() {
        while(!pIn.isEmpty)
            pIn.receive()
    }

    private fun JsonObject.parseErrors() : String {
        return parseJErrors(this)
    }

    private fun parseJErrors(jsonObj: JsonObject) : String {
        if (!jsonObj.has("err"))
            throw PyBluezWrapperException("Invalid error: $jsonObj")

        return "${jsonObj.get("source").asString.trim()}: ${jsonObj.get("err").asString.trim()}"
    }

    override fun close() {
        runBlocking {
            pIn.cancel()
            pErr.cancel()
        }
    }

}