package it.unibo.kBluez.io

import kotlinx.coroutines.channels.Channel

//T: the type that pass the lines of the router and that exit or enters the single line
interface ChannelRouter<T> {

    suspend fun start()
    suspend fun started() : ChannelRouter<T>
    suspend fun newRoute(name : String,
                         routeChannel : Channel<T> = Channel(),
                         passage : (T) -> Boolean = {true}) : ChannelRoute<T>
    suspend fun getRoute(name : String) : ChannelRoute<T>?
    suspend fun removeRoute(name : String) : ChannelRoute<T>?


}