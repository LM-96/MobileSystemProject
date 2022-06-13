package it.unibo.kBluez.collections

interface ConcurrentMap<K, V> {

    suspend fun size() : Int
    suspend fun entries() : MutableSet<MutableMap.MutableEntry<K, V>>
    suspend fun keys() : MutableSet<K>
    suspend fun values() : MutableCollection<V>
    suspend fun containsKey(key: K): Boolean
    suspend fun containsValue(value: V): Boolean
    suspend fun get(key: K): V?
    suspend fun isEmpty(): Boolean
    suspend fun clear()
    suspend fun put(key: K, value: V): V?
    suspend fun putAll(from: Map<out K, V>)
    suspend fun remove(key: K): V?

}