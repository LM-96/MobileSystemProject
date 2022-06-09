package it.unibo.kBluez.io

import kotlinx.coroutines.channels.Channel

internal data class ChannelRouterRequest<T>(
    val cmd : Int,
    val obj : Any? = null,
    val interactionChan : Channel<ChannelRouterResponse> = Channel()
)