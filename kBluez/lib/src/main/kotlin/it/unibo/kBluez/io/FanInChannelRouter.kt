package it.unibo.kBluez.io

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.selects.select
import mu.KotlinLogging
import java.util.*

//T: the type that exit from the single line of the router
//I: the type that enters the router
open class FanInChannelRouter<I, T> (
    private val outChan : SendChannel<T>,
    scope : CoroutineScope = GlobalScope,
    routerName : String = "",
    private val mapper : (I) -> Optional<T>
): CloseableChannelRouter<I> {

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
    private val requestChan = Channel<ChannelRouterRequest<I>>()
    private val ackChan = Channel<Unit>()
    private val job = scope.launch {
        var mapped : Optional<T>
        var mappedElement : T
        var terminated = false
        var started = false
        val routes = mutableMapOf<String, ChannelRoute<I>>()
        var iterator : MutableIterator<MutableMap.MutableEntry<String, ChannelRoute<I>>>
        var current : MutableMap.MutableEntry<String, ChannelRoute<I>>
        while(isActive && !terminated) {
            select<Unit> {
                log.info("new select cycle")

                requestChan.onReceive { req ->
                    log.info("received request [$req]")
                    when(req.cmd) {
                        START_ROUTING_REQ -> {
                            started = true
                            req.interactionChan.send(ChannelRouterResponse(OK_RES))
                            log.info("started routing")
                        }
                        ADD_ROUTE_REQ -> {
                            when(req.obj) {
                                is ChannelRoute<*> -> {
                                    try {
                                        routes[req.obj.name] = req.obj as ChannelRoute<I>
                                        req.interactionChan.send(ChannelRouterResponse(OK_RES))
                                        log.info("added new route [${req.obj.name}]")
                                    } catch (e : Exception) {
                                        req.interactionChan.send(ChannelRouterResponse(ERR_RES, e))
                                        log.error("unable to add route with request $req")
                                        log.catching(e)
                                    }
                                }
                            }
                        }
                        REMOVE_ROUTE_REQ -> {
                            when(req.obj) {
                                is ChannelRoute<*> -> {
                                    try {
                                        routes.remove(req.obj!!.name)
                                        req.interactionChan.send(ChannelRouterResponse(OK_RES))
                                        log.info("removed route [${req.obj!!.name}]")
                                    } catch (e : Exception) {
                                        req.interactionChan.send(ChannelRouterResponse(ERR_RES, e))
                                        log.error("unable to remove route with request $req")
                                    }
                                }
                            }
                        }
                        GET_ROUTE_REQ -> {
                            if(req.obj is String) {
                                req.interactionChan.send(ChannelRouterResponse(OK_RES, routes[req.obj]))
                                log.info("get route [${req.obj}]")
                            } else {
                                req.interactionChan.send(ChannelRouterResponse(
                                    ERR_RES,
                                    IllegalArgumentException("The key must be a string")
                                ))
                                log.error("unable to get router with request [$req] : key is not a String but ${req.obj?.javaClass}")
                            }
                        }
                        TERMINATE_REQ -> {
                            terminated = true
                            req.interactionChan.send((ChannelRouterResponse(OK_RES)))
                            log.info("termination requested")
                        }
                    }
                }

                if(started) {
                    routes.forEach { route ->
                        try {
                            route.value.channel.onReceive { msg ->
                                if(route.value.passage(msg)) {
                                    log.info("outcoming message from ${route.key}: $msg")
                                    mapped = mapper(msg)
                                    if(mapped.isPresent) {
                                        mappedElement = mapped.get()
                                        log.info("message {$msg}(${msg!!::class.java}) mapped into {$mappedElement}(${mappedElement!!::class.java})")
                                        outChan.send(mappedElement)
                                        log.info("message $msg forwarded to the out route")
                                    }
                                } else {
                                    log.info("passage denied for \'$msg\' incoming from route \'${route.value}\'")
                                }
                            }
                        } catch (crce : ClosedReceiveChannelException) {
                            log.info("route ${route.key} is closed")
                            log.catching(crce)
                            routes.remove(route.key)
                            log.info("removed route [${route.key}]")
                        } catch (csce : ClosedSendChannelException) {
                            log.info("out route is closed : closing router")
                            terminated = true
                        } catch (ce : CancellationException) {
                            log.info("out route job is cancelled: closing router")
                            log.catching(ce)
                            routes.values.forEach { it.channel.close(ce) }
                            routes.clear()
                            terminated = true
                            log.info("routes are now closed and removed. Terminating...")
                        }
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

    override suspend fun started() : ChannelRouter<I> {
        start()
        return this
    }

    override suspend fun newRoute(name : String,
                                  routeChannel : Channel<I>,
                                  passage : (I) -> Boolean
    ) : ChannelRoute<I> {
        val route = ChannelRoute(name, passage, routeChannel)
        val res = performRequest(ChannelRouterRequest(ADD_ROUTE_REQ, route))
        when(res.responseCode) {
            OK_RES -> return route
            ERR_RES -> throw res.obj as Exception
        }

        return route
    }

    override suspend fun getRoute(name : String) : ChannelRoute<I>? {
        val res = performRequest(ChannelRouterRequest(GET_ROUTE_REQ, name))
        when(res.responseCode) {
            OK_RES -> return res.obj as ChannelRoute<I>?
            ERR_RES -> throw res.obj as Exception
        }

        return null
    }

    override suspend fun removeRoute(name : String) : ChannelRoute<I>? {
        val res = performRequest(ChannelRouterRequest(REMOVE_ROUTE_REQ, name))
        when(res.responseCode) {
            OK_RES -> return (res.obj as ChannelRoute<I>?)
            ERR_RES -> throw res.obj as Exception
        }

        return null
    }

    private suspend fun performRequest(req : ChannelRouterRequest<I>) : ChannelRouterResponse {
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