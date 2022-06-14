package it.unibo.kBluez.bridging

import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import it.unibo.kBluez.model.BluetoothServiceProtocol
import it.unibo.kBluez.model.NetworkProtocol
import it.unibo.kBluez.utils.getNullable
import it.unibo.kBluez.utils.getNullableInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import mu.KotlinLogging
import java.nio.file.Files
import java.nio.file.Paths

object BridgeConfigurator {

    var bridgeScope : CoroutineScope = GlobalScope

    private const val BRIDGE_JSON_FILE = "bridge.json"
    private val LOG = KotlinLogging.logger("BridgeConfigurator")

    fun loadBridgesFromJsonFile() : Map<String, BluetoothBridge> {
        val bridgeMap = mutableMapOf<String, BluetoothBridge>()
        var path = Paths.get("./$BRIDGE_JSON_FILE")
        LOG.info("loading bridges...")
        val jsonLines = if(Files.exists(path)) {
            LOG.info("bridge loaded from executable path")
            Files.readAllLines(path)
        } else {
            LOG.info("bridge loaded from resources")
            javaClass.getResourceAsStream("/$BRIDGE_JSON_FILE")?.bufferedReader()?.readLines()
        }
        LOG.info("found ${jsonLines?.size ?: "0"} lines to analyze")
        if(jsonLines != null) {
            var bridge : BluetoothBridge
            var bridgeName : String?
            var bluetoothPort : Int?
            var bluetoothProtocol : String?
            var bluetoothProtocolEnum : BluetoothServiceProtocol
            var bluetoothSvcUuid : String?
            var bluetoothSvcName : String?
            var netHost : String?
            var netProcolEnum : NetworkProtocol = NetworkProtocol.TCP
            var netPort : Int?
            var netProtocol : String?
            var jsonObj : JsonObject
            jsonLines.forEach { line ->
                try {
                    LOG.info("loading bridge: \"$line\"")
                    jsonObj = JsonParser.parseString(line).asJsonObject

                    bridgeName = jsonObj.getNullable(BRIDGE_NAME_KEY)?.asString
                    if(bridgeName == null) {
                        LOG.error("missing bridge name")
                        throw IllegalArgumentException("missing bridge name")
                    }

                    bluetoothPort = jsonObj.getNullableInt(BLUETOOTH_PORT_KEY)
                    if(bluetoothPort == null) {
                        LOG.warn("missing bluetooth port: a random port will be used")
                    }

                    bluetoothProtocol = jsonObj.getNullable(BLUETOOTH_PROTOCOL)?.asString
                    if(bluetoothProtocol == null) {
                        LOG.error("missing bluetooth protocol")
                        throw IllegalArgumentException("missing bluetooth procol")
                    }
                    try {
                        bluetoothProtocolEnum = BluetoothServiceProtocol.valueOf(bluetoothProtocol!!)
                    } catch (iae : IllegalArgumentException) {
                        LOG.error("bluetooth protocol \'$bluetoothProtocol\' does not exitst")
                        throw IllegalArgumentException("bluetooth protocol \'$bluetoothProtocol\' does not exitst")
                    }

                    bluetoothSvcUuid = jsonObj.getNullable(BLUETOOTH_SERVICE_UUID_KEY)?.asString
                    bluetoothSvcName = jsonObj.getNullable(BLUETOOTH_SERVICE_NAME_KEY)?.asString
                    if((bluetoothSvcName != null && bluetoothSvcUuid == null)) {
                        LOG.error("the uuid of the service cannot be null if the name is specified")
                        throw IllegalArgumentException("the uuid of the service cannot be null if the name is specified")
                    }
                    if((bluetoothSvcName == null && bluetoothSvcUuid != null)) {
                        LOG.error("the name of the service cannot be null if the uuid is specified")
                        throw IllegalArgumentException("the name of the service cannot be null if the uuid is specified")
                    }

                    netHost = jsonObj.getNullable(NET_HOST_ADDRESS_KEY)?.asString
                    if(netHost == null) {
                        LOG.error("net host cannot be null")
                        throw (IllegalArgumentException("net host cannot be null"))
                    }

                    netPort = jsonObj.getNullableInt(NET_HOST_PORT_KEY)
                    if(netPort == null) {
                        LOG.error("net port cannot be null")
                        throw (IllegalArgumentException("net port cannot be null"))
                    }

                    netProtocol = jsonObj.getNullable(NET_PROTOCOL_KEY)?.asString
                    if(netProtocol == null) {
                        LOG.error("missing net protocol")
                        throw IllegalArgumentException("missing net procol")
                    }
                    try {
                        netProcolEnum = NetworkProtocol.valueOf(netProtocol!!)
                    } catch (iae : IllegalArgumentException) {
                        LOG.error("net protocol \'$netProtocol\' does not exitst")
                        throw IllegalArgumentException("net protocol \'$netProtocol\' does not exitst")
                    }

                    bridge = BluetoothBridge(bridgeName!!, bluetoothPort, bluetoothProtocolEnum,
                        netHost!!, netPort!!, netProcolEnum, bridgeScope, Dispatchers.IO,
                        bluetoothSvcName, bluetoothSvcUuid)
                    LOG.info("created bridge ${bridge.brigeName}")

                    bridgeMap[bridge.brigeName] = bridge
                    LOG.info("bridge added to the internal map")

                } catch (jpe : JsonParseException) {
                    LOG.error("unable to parse json: json parse error")
                    LOG.catching(jpe)
                } catch (jse : JsonSyntaxException) {
                    LOG.error("unable to parse json: json syntax error")
                    LOG.catching(jse)
                } catch (ise : IllegalStateException) {
                    LOG.error("unable to parse json: no json object found")
                    LOG.catching(ise)
                } catch (iae : IllegalArgumentException) {
                    LOG.error("problems with bridge specification")
                    LOG.catching(iae)
                }
            }
        }

        return bridgeMap
    }

    suspend fun Map<String, BluetoothBridge>.startAll() {
        forEach { (t, u) ->
            u.start()
        }
    }

}