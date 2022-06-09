package it.unibo.kBluez.io

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.selects.select
import mu.KotlinLogging
import java.util.*

open class FanOutChannelRouter<T, O> (
    private val sourceChan : ReceiveChannel<T>,
    scope : CoroutineScope = GlobalScope,
    routerName : String = "",
    private val mapper : (T) -> Optional<O>
): CloseableChannelRouter<O> {

    companion object {
        const val START_ROUTING_REQ = 0
        const val ADD_ROUTE_REQ = 1
        const val REMOVE_ROUTE_REQ = 2
        const val GET_ROUTE_REQ = 3
        const val TERMINATE_REQ = 4

        const val OK_RES = 0
        const val ERR_RES = 1
    }

    private val log = KotlinLogging.logger("${this::class.simpleName!!}[$routerName]")
    private val requestChan = Channel<ChannelRouterRequest<T>>()
    private val ackChan = Channel<Unit>()
    private val job = scope.launch {
        var mapped : Optional<O>
        var mappedElement : O
        var terminated = false
        var started = false
        val routes = mutableMapOf<String, ChannelRoute<O>>()
        var iterator : MutableIterator<MutableMap.MutableEntry<String, ChannelRoute<O>>>
        var current : MutableMap.MutableEntry<String, ChannelRoute<O>>
        while(isActive && !terminated) {
            select<Unit> {
                log.info("new select cycle")

                requestChan.onReceive {
                    log.info("received request [$it]")
                    when(it.cmd) {
                        START_ROUTING_REQ -> {
                            started = true
                            it.interactionChan.send(ChannelRouterResponse(OK_RES))
                            log.info("started routing")
                        }
                        ADD_ROUTE_REQ -> {
                            when(it.obj) {
                                is ChannelRoute<*> -> {
                                    try {
                                        routes[it.obj.name] = it.obj as ChannelRoute<O>
                                        it.interactionChan.send(ChannelRouterResponse(OK_RES))
                                        log.info("added new route [${it.obj.name}]")
                                    } catch (e : Exception) {
                                        it.interactionChan.send(ChannelRouterResponse(ERR_RES, e))
                                        log.error("unable to add route with request $it")
                                        log.catching(e)
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
                                        log.info("removed route [${it.obj!!.name}]")
                                    } catch (e : Exception) {
                                        it.interactionChan.send(ChannelRouterResponse(ERR_RES, e))
                                        log.error("unable to remove route with request $it")
                                    }
                                }
                            }
                        }
                        GET_ROUTE_REQ -> {
                            if(it.obj is String) {
                                it.interactionChan.send(ChannelRouterResponse(OK_RES, routes[it.obj]))
                                log.info("get route [${it.obj}]")
                            } else {
                                it.interactionChan.send(ChannelRouterResponse(
                                    ERR_RES,
                                    IllegalArgumentException("The key must be a string")
                                ))
                                log.error("unable to get router with request [$it] : key is not a String but ${it.obj?.javaClass}")
                            }
                        }
                        TERMINATE_REQ -> {
                            terminated = true
                            it.interactionChan.send((ChannelRouterResponse(OK_RES)))
                            log.info("termination requested")
                        }
                    }
                }

                if(started) {
                    try {
                        sourceChan.onReceive {
                            log.info("incoming message : $it")
                            mapped = mapper(it)
                            if(mapped.isPresent) {
                                mappedElement = mapped.get()
                                log.info("message {$it}(${it!!::class.java}) mapped into {$mappedElement}(${mappedElement!!::class.java})")
                                iterator = routes.iterator()
                                while(iterator.hasNext()) {
                                    current = iterator.next()
                                    if(current.value.passage(mappedElement))
                                        try {
                                            current.value.channel.send(mappedElement)
                                            log.info("message [$it] routed to ${current.key}")
                                        } catch (csce : ClosedSendChannelException) {
                                            log.warn("unable to route message to ${current.key}: channel closed")
                                            log.catching(csce)
                                            iterator.remove()
                                            log.info("route \'${current.key}\' removed")
                                        } catch (ce : CancellationException) {
                                            iterator.remove()
                                            log.warn("unable to route message to ${current.key}: job cancelled")
                                            log.catching(ce)
                                            log.info("route \'${current.key}\' removed")
                                        }
                                }
                            } else {
                                log.info("message $it is ignored by mapper")
                            }
                        }
                    } catch (crce : ClosedReceiveChannelException) {
                        log.info("source channel is closed: closing router")
                        log.catching(crce)
                        routes.values.forEach { it.channel.close(crce) }
                        routes.clear()
                        terminated = true
                        log.info("routes are now closed and removed. Terminating...")
                    } catch (ce : CancellationException) {
                        log.info("source channel job is cancelled: closing router")
                        log.catching(ce)
                        routes.values.forEach { it.channel.close(ce) }
                        routes.clear()
                        terminated = true
                        log.info("routes are now closed and removed. Terminating...")
                    }
                }
            }
        }

        routes.values.forEach { it.channel.close() }
        routes.clear()

        ackChan.send(Unit)
        log.info("terminated")
    }

    override suspend fun start() {
        performRequest(ChannelRouterRequest(START_ROUTING_REQ))
    }

    override suspend fun started() : ChannelRoute<O> {
        start()
        return this
    }

    override suspend fun newRoute(name : String,
                                  routeChannel : Channel<O>,
                                  passage : (O) -> Boolean) : ChannelRoute<O> {
        val route = ChannelRoute(name, passage, routeChannel)
        val res = performRequest(ChannelRouterRequest<T>(ADD_ROUTE_REQ, route))
        when(res.responseCode) {
            OK_RES -> return route
            ERR_RES -> throw res.obj as Exception
        }

        return route
    }

    override suspend fun getRoute(name : String) : ChannelRoute<O>? {
        val res = performRequest(ChannelRouterRequest(GET_ROUTE_REQ, name))
        when(res.responseCode) {
            OK_RES -> return res.obj as ChannelRoute<O>?
            ERR_RES -> throw res.obj as Exception
        }

        return null
    }

    override suspend fun removeRoute(name : String) : ChannelRoute<O>? {
        val res = performRequest(ChannelRouterRequest(REMOVE_ROUTE_REQ, name))
        when(res.responseCode) {
            OK_RES -> return (res.obj as ChannelRoute<O>?)
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