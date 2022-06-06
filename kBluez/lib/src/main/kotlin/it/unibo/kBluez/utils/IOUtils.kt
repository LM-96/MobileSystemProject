package it.unibo.kBluez.utils

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.selects.select
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Map

class BufferedReaderChannelWrapper(
    private val reader : BufferedReader,
    scope : CoroutineScope = GlobalScope
) : Closeable, AutoCloseable {

    private val channel = Channel<String>()
    private val job = scope.launch(Dispatchers.IO) {
        var line : String?
        try {
            do{
                println("Reader | waiting...")
                line = reader.readLine()
                println("Reader | readed line: $line")
                if(line != null) {
                    channel.send(line)
                }
            } while (line != null)
        } catch (ioe : IOException) {
            channel.close(ioe)
        } catch (_: CancellationException) {
            //Exit cycle in case of channel cancellation
        } catch (_ : ClosedSendChannelException) {
            //Exit cycle in case of channel close
        }

        //Needed in case of reaching EOF in the stream
        if(!channel.isClosedForSend) {
            channel.close()
        }
    }

    fun getReceiveChannel() : ReceiveChannel<String> {
        return channel
    }

    override fun close() {
        if(!channel.isClosedForSend)
            channel.close()
        runBlocking { job.join() }
    }
}

class OutputStreamStringChannelWrapper(
    private val writer : BufferedWriter,
    private val scope : CoroutineScope
) : Closeable, AutoCloseable {

    private val channel = Channel<String>()
    private val job = scope.launch(Dispatchers.IO) {
        var line : String
        try {
            while(true) {
                println("Writer | waiting...")
                line = channel.receive()
                println("Writer | writing: $line")
                writer.write(line)
                writer.newLine()
                writer.flush()
            }
        } catch (_ : ClosedReceiveChannelException) {
            //Exit cycle in case of channel closed
            writer.close()
        } catch (ioe : IOException) {
            channel.close(ioe)
        } catch (_ : CancellationException) {
            //Exit cycle in case of chanel cancellation
            writer.close()
        }

        if(!channel.isClosedForReceive) {
            channel.close()
        }
    }

    fun getSendChannel() : SendChannel<String> {
        return channel
    }

    override fun close() {
        if(!channel.isClosedForReceive)
            channel.close()
        runBlocking { job.join() }
    }
}

fun InputStream.stringReceiveChannel(scope : CoroutineScope = GlobalScope) : ReceiveChannel<String> {
    return this.bufferedReader().stringReceiveChannel(scope)
}

fun BufferedReader.stringReceiveChannel(scope : CoroutineScope = GlobalScope) : ReceiveChannel<String> {
    return BufferedReaderChannelWrapper(this, scope).getReceiveChannel()
}

fun OutputStream.stringSendChannel(scope : CoroutineScope = GlobalScope) : SendChannel<String> {
    return this.bufferedWriter().stringSendChannel(scope)
}

fun BufferedWriter.stringSendChannel(scope : CoroutineScope = GlobalScope) : SendChannel<String> {
    return OutputStreamStringChannelWrapper(this, scope).getSendChannel()
}

suspend fun ReceiveChannel<String>.availableText() : String? {
    if(isEmpty)
        return null

    var text = ""
    while(!isEmpty)
        text += (receive() + "\n")
    return text.trim()
}

suspend fun ReceiveChannel<String>.receiveTextUntilClosed() : String? {
    var text = ""
    try {
        while(true)
            text += (receive() + "\n")
    } catch (_ : ClosedReceiveChannelException) {
        return if(text.isBlank())
            null
        else text.trim()
    }

}

data class ChannelRoute<T>(
    val name : String,
    val passage : (T) -> Boolean,
    val channel : Channel<T> = Channel()
)

data class ChannelRouterResponse(
    val responseCode : Int,
    val obj : Any? = null
)

data class ChannelRouterRequest<T>(
    val cmd : Int,
    val obj : Any? = null,
    val interactionChan : Channel<ChannelRouterResponse> = Channel()
)

class ChannelRouter<T>(
    private val sourceChan : ReceiveChannel<T>,
    scope : CoroutineScope = GlobalScope
) : Closeable, AutoCloseable {

    companion object {
        const val START_ROUTING_REQ = 0
        const val ADD_ROUTE_REQ = 1
        const val REMOVE_ROUTE_REQ = 2
        const val GET_ROUTE_REQ = 3
        const val TERMINATE_REQ = 4

        const val OK_RES = 0
        const val ERR_RES = 1
    }

    private val requestChan = Channel<ChannelRouterRequest<T>>()
    private val ackChan = Channel<Unit>()
    private val job = scope.launch {
        var terminated = false
        var started = false
        val routes = mutableMapOf<String, ChannelRoute<T>>()
        var iterator : MutableIterator<MutableMap.MutableEntry<String, ChannelRoute<T>>>
        var current : MutableMap.MutableEntry<String, ChannelRoute<T>>
        while(isActive && !terminated) {
            select<Unit> {
                println("Router | select")

                requestChan.onReceive {
                    when(it.cmd) {
                        START_ROUTING_REQ -> {
                            started = true
                            it.interactionChan.send(ChannelRouterResponse(OK_RES))
                        }
                        ADD_ROUTE_REQ -> {
                            when(it.obj) {
                                is ChannelRoute<*> -> {
                                    try {
                                        routes[it.obj.name] = it.obj as ChannelRoute<T>
                                        println("Router | added new route : ${it.obj.name}")
                                        it.interactionChan.send(ChannelRouterResponse(OK_RES))
                                    } catch (e : Exception) {
                                        it.interactionChan.send(ChannelRouterResponse(ERR_RES, e))
                                    }
                                }
                            }
                        }
                        REMOVE_ROUTE_REQ -> {
                            when(it.obj) {
                                is ChannelRoute<*> -> {
                                    try {
                                        routes.remove(it.obj!!.name)
                                        it.interactionChan.send(ChannelRouterResponse(OK_RES))
                                    } catch (e : Exception) {
                                        it.interactionChan.send(ChannelRouterResponse(ERR_RES, e))
                                    }
                                }
                            }
                        }
                        GET_ROUTE_REQ -> {
                            if(it.obj is String) {
                                it.interactionChan.send(ChannelRouterResponse(OK_RES, routes.remove(it.obj)))
                            } else {
                                it.interactionChan.send(ChannelRouterResponse(ERR_RES,
                                    IllegalArgumentException("The key must be a string")
                                ))
                            }
                        }
                        TERMINATE_REQ -> {
                            terminated = true
                            it.interactionChan.send((ChannelRouterResponse(OK_RES)))
                        }
                    }
                }

                if(started) {
                    try {
                        sourceChan.onReceive {
                            println("Router | incoming message : $it")
                            iterator = routes.iterator()
                            while(iterator.hasNext()) {
                                current = iterator.next()
                                if(current.value.passage(it))
                                    try {
                                        println("Router | routing message [$it] to ${current.key}")
                                        current.value.channel.send(it)
                                    } catch (csce : ClosedSendChannelException) {
                                        iterator.remove()
                                    } catch (ce : CancellationException) {
                                        iterator.remove()
                                    }
                            }
                        }
                    } catch (crce : ClosedReceiveChannelException) {
                        routes.values.forEach { it.channel.close(crce) }
                        routes.clear()
                        terminated = true
                    } catch (ce : CancellationException) {
                        routes.values.forEach { it.channel.close(ce) }
                        routes.clear()
                        terminated = true
                    }
                }
            }
        }

        ackChan.send(Unit)
    }

    suspend fun start() {
        performRequest(ChannelRouterRequest(START_ROUTING_REQ))
    }

    suspend fun started() : ChannelRouter<T> {
        start()
        return this
    }

    suspend fun newRoute(name : String,
                         routeChannel : Channel<T> = Channel(),
                         passage : (T) -> Boolean) : ReceiveChannel<T> {
        val route = ChannelRoute(name, passage, routeChannel)
        val res = performRequest(ChannelRouterRequest<T>(ADD_ROUTE_REQ, route))
        when(res.responseCode) {
            OK_RES -> return route.channel
            ERR_RES -> throw res.obj as Exception
        }

        return route.channel
    }

    suspend fun getRoute(name : String) : ChannelRoute<T>? {
        val res = performRequest(ChannelRouterRequest(GET_ROUTE_REQ, name))
        when(res.responseCode) {
            OK_RES -> return res.obj as ChannelRoute<T>?
            ERR_RES -> throw res.obj as Exception
        }

        return null
    }

    suspend fun removeRoute(name : String) : ChannelRoute<T>? {
        val res = performRequest(ChannelRouterRequest(REMOVE_ROUTE_REQ, name))
        when(res.responseCode) {
            OK_RES -> return (res.obj as ChannelRoute<T>?)
            ERR_RES -> throw res.obj as Exception
        }

        return null
    }

    private suspend fun performRequest(req : ChannelRouterRequest<T>) : ChannelRouterResponse {
        requestChan.send(req)
        return req.interactionChan.receive()
    }

    override fun close() {
        runBlocking {
            performRequest(ChannelRouterRequest(TERMINATE_REQ))
            ackChan.receive()
            job.join()
        }
    }
}

fun <T> ReceiveChannel<T>.newRouter(scope : CoroutineScope = GlobalScope) : ChannelRouter<T> {
    return ChannelRouter(this, scope)
}

class ChannelToStateFlowWrapper<I, O>(
    private val channel : ReceiveChannel<I>,
    initialValue : O,
    scope : CoroutineScope = GlobalScope,
    private val mapper : (I) -> O,
) : Closeable, AutoCloseable {

    private val terminationChan = Channel<Unit>()
    private val ackChan = Channel<Exception?>()
    private val internalStateFlow = MutableStateFlow(initialValue)
    val stateFlow = internalStateFlow.asStateFlow()

    private val job = scope.launch {
        var terminated = false
        while(isActive && !terminated) {
            select<Unit> {
                try {
                    channel.onReceive {
                        println("ChannelStateFlowWrapper | emitting $it on flow")
                        internalStateFlow.emit(mapper(it))
                    }
                } catch (_ : ClosedReceiveChannelException) {
                    terminated = true
                } catch (_ : CancellationException) {
                    terminated = true
                } catch (e : Exception) {
                    e.printStackTrace()
                    //Cathces the exception of the mapper
                }
                terminationChan.onReceive {
                    terminated = true
                    ackChan.send(null)
                }
            }
        }
    }

    override fun close() {
        runBlocking {
            terminationChan.send(Unit)
            ackChan.receive()
            job.join()
        }
    }

}

fun <T, O> ReceiveChannel<T>.asStateFlow(initialValue : O, scope : CoroutineScope = GlobalScope,
                                         mapper : (T) -> O) : StateFlow<O>
{
    return ChannelToStateFlowWrapper(this, initialValue, scope, mapper).stateFlow
}