package it.unibo.kBluez.io

import java.io.Closeable

interface CloseableChannelRouter<T> : ChannelRouter<T>, Closeable, AutoCloseable {
}