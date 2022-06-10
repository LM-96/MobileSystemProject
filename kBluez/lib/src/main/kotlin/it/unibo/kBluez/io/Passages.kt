package it.unibo.kBluez.io

import com.google.gson.JsonObject
import java.util.*

/**
 * Returns a filter that enable the passage for the jsons
 * that have at least one of the key passed as params
 */
fun allowedKeyFilterPassage(vararg keys : String) : (JsonObject) -> Boolean = { jsonObj ->
    try {
        val iterator = keys.iterator()
        var opened = false
        while(iterator.hasNext() && !opened) {
            if(jsonObj.has(iterator.next()))
                opened = true
        }
        opened
    } catch (e : Exception) {
        false
    }
}

/**
 * Returns a filter that enable the passage for the jsons
 * that have the key and the valued passed as param
 */
fun allowedKeyStringFilterPassage(key : String, value : String) : (JsonObject) -> Boolean = { jsonObj ->
    try {
        var res : Boolean = false
        if(jsonObj.has(key))
            if(jsonObj.get(key).asString == value)
                res = true

        res
    } catch (e : Exception) {
        false
    }
}

/**
 * Returns a filter that deny the passage for the jsons
 * that have at least one of the key passed as params
 */
fun deniedKeyFilterPassage(vararg keys : String) : (JsonObject) -> Boolean = { jsonObj ->
        try {
            val iterator = keys.iterator()
            var opened = true
            while(iterator.hasNext() && opened) {
                if(jsonObj.has(iterator.next()))
                    opened = false
            }
            opened
        } catch (e : Exception) {
            false
        }
    }