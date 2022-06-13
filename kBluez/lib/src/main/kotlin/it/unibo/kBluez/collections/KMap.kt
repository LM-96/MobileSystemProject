package it.unibo.kBluez.collections

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.Closeable

enum class MapBasicCmd {
    PUT, GET, CONTAINS_KEY, CONTAINS_VALUE, ENTRIES, KEYS,
    VALUES, CLEAR, PUT_ALL, REMOVE, TERMINATE, SIZE, FOR_EACH
}

enum class MapBasicResponse {
    EXECUTED, NOT_EXECUTED
}

class MapResponse(
    val basicResponse: MapBasicResponse,
    val params : Array<Any?> = arrayOf()
)

fun responseByException(e : Exception) : MapResponse {
    return MapResponse(MapBasicResponse.NOT_EXECUTED, arrayOf(e))
}

fun executedResponse() : MapResponse {
    return MapResponse(MapBasicResponse.EXECUTED)
}

class MapRequest(
    val cmd : MapBasicCmd,
    val responseChan : Channel<MapResponse>,
    val params : Array<Any?> = arrayOf()
)

class KMap<K, V>(scope : CoroutineScope) : ConcurrentMap<K, V>, Closeable, AutoCloseable
{

    private val reqChan = Channel<MapRequest>()
    private val job = scope.launch {
        var terminated = false
        var request : MapRequest
        val map = mutableMapOf<K, V>()

        var key : K
        var value : V

        while(!terminated) {
            try {
                request = reqChan.receive()
                try {
                    when(request.cmd) {
                        MapBasicCmd.PUT -> {
                            key = request.params[0] as K
                            value = request.params[1] as V
                            map[key] = value
                            request.responseChan.send(executedResponse())
                        }

                        MapBasicCmd.PUT_ALL -> {
                            map.putAll(request.params[0] as Map<out K, V>)
                            request.responseChan.send(executedResponse())
                        }

                        MapBasicCmd.SIZE -> {
                            request.responseChan.send(
                                MapResponse(MapBasicResponse.EXECUTED, arrayOf(map.size)))
                        }

                        MapBasicCmd.CONTAINS_VALUE -> {
                            request.responseChan.send(
                                MapResponse(MapBasicResponse.EXECUTED,
                                    arrayOf(map.containsValue(request.params[0] as V)))
                            )
                        }

                        MapBasicCmd.CONTAINS_KEY -> {
                            request.responseChan.send(
                                MapResponse(MapBasicResponse.EXECUTED,
                                    arrayOf(map.containsKey(request.params[0] as K)))
                            )
                        }

                        MapBasicCmd.CLEAR -> {
                            map.clear()
                            request.responseChan.send(executedResponse())
                        }

                        MapBasicCmd.ENTRIES -> {
                            request.responseChan.send(
                                MapResponse(MapBasicResponse.EXECUTED,
                                    arrayOf(map.entries)
                                )
                            )
                        }

                        MapBasicCmd.GET -> {
                            request.responseChan.send(
                                MapResponse(MapBasicResponse.EXECUTED,
                                    arrayOf(map[request.params[0] as K])
                                )
                            )
                        }

                        MapBasicCmd.KEYS -> {
                            request.responseChan.send(
                                MapResponse(MapBasicResponse.EXECUTED,
                                    arrayOf(map.keys)
                                )
                            )
                        }

                        MapBasicCmd.REMOVE -> {
                            request.responseChan.send(
                                MapResponse(MapBasicResponse.EXECUTED,
                                    arrayOf(map.remove(request.params[0] as K))
                                )
                            )
                        }

                        MapBasicCmd.VALUES -> {
                            request.responseChan.send(
                                MapResponse(MapBasicResponse.EXECUTED,
                                    arrayOf(map.values)
                                )
                            )
                        }

                        MapBasicCmd.FOR_EACH -> {
                            map.forEach(request.params[0] as (Map.Entry<K, V>) -> Unit)
                            request.responseChan.send(executedResponse())
                        }

                        MapBasicCmd.TERMINATE -> {
                            terminated = true
                            request.responseChan.send(executedResponse())
                        }
                    }
                } catch (e : Exception) {
                    request.responseChan.send(responseByException(e))
                }

            } catch (_ : ClosedReceiveChannelException) {
                terminated = true
            }
        }
    }

    private suspend fun request(cmd : MapBasicCmd, vararg params : Any?) : Array<Any?> {
        val chan = Channel<MapResponse>()
        reqChan.send(MapRequest(cmd, chan, params as Array<Any?>))
        val res = chan.receive()
        chan.close()

        if(res.basicResponse == MapBasicResponse.NOT_EXECUTED)
            throw res.params[0] as Exception

        return res.params
    }

    override suspend fun containsKey(key: K): Boolean {
        return request(MapBasicCmd.CONTAINS_KEY, key)[0] as Boolean
    }

    override suspend fun containsValue(value: V): Boolean {
        return request(MapBasicCmd.CONTAINS_VALUE, value)[0] as Boolean
    }

    override suspend fun get(key: K): V? {
        return request(MapBasicCmd.GET, key)[0] as V?
    }

    override suspend fun isEmpty(): Boolean {
        return request(MapBasicCmd.SIZE)[0] as Int == 0
    }

    override suspend fun size() : Int {
        return request(MapBasicCmd.SIZE)[0] as Int
    }

    override suspend fun entries() : MutableSet<MutableMap.MutableEntry<K, V>> {
        return request(MapBasicCmd.ENTRIES)[0] as MutableSet<MutableMap.MutableEntry<K, V>>
    }

    override suspend fun keys() : MutableSet<K> {
        return request(MapBasicCmd.KEYS)[0] as MutableSet<K>
    }

    override suspend fun values() : MutableCollection<V> {
        return request(MapBasicCmd.VALUES)[0] as MutableCollection<V>
    }

    override suspend fun clear() {
        request(MapBasicCmd.CLEAR)
    }

    override suspend fun put(key: K, value: V): V? {
        return request(MapBasicCmd.PUT, key, value)[0] as V?
    }

    override suspend fun putAll(from: Map<out K, V>) {
        request(MapBasicCmd.PUT_ALL, from)
    }

    override suspend fun remove(key: K): V? {
        return request(MapBasicCmd.REMOVE, key)[0] as V?
    }

    suspend fun forEach(action : (MutableMap.MutableEntry<K, V>) -> Unit) {
        request(MapBasicCmd.FOR_EACH, action)
    }

    suspend fun <R> map(mapper : (MutableMap.MutableEntry<K, V>) -> R) : List<R> {
        return entries().map(mapper)
    }

    override fun close() {
        runBlocking {
            request(MapBasicCmd.TERMINATE)
            job.join()
        }
    }
}

suspend fun <K, V> kMapOf(scope : CoroutineScope, vararg entries : Pair<K, V>) : KMap<K, V>{
    val res = KMap<K, V>(scope)
    res.putAll(entries.toMap())
    return res
}