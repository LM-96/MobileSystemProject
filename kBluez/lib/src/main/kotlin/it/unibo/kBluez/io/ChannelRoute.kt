package it.unibo.kBluez.io

import kotlinx.coroutines.channels.Channel

interface ChannelRoute<T> {

    suspend fun start()
    suspend fun started() : ChannelRoute<T>
    suspend fun newRoute(name : String,
                         routeChannel : Channel<T> = Channel(),
                         passage : (T) -> Boolean = {true}) : ChannelRoute<T>
    suspend fun getRoute(name : String) : ChannelRoute<T>?
    suspend fun removeRoute(name : String) : ChannelRoute<T>?


}