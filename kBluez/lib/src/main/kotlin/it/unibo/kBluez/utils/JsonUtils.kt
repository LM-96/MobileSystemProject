package it.unibo.kBluez.utils

import com.google.gson.JsonElement
import com.google.gson.JsonObject

fun JsonObject.getNullable(key : String) : JsonElement? {
    val value: JsonElement = this.get(key) ?: return null

    if (value.isJsonNull) {
        return null
    }

    return value
}

fun JsonObject.getInt(key : String) : Int {
    return try {
        get(key).asInt
    } catch (e : Exception) {
        get(key).asString.toInt()
    }
}

fun JsonObject.getNullableInt(key : String) : Int? {
    val value: JsonElement = this.get(key) ?: return null
    if (value.isJsonNull) {
        return null
    }

    return try {
        get(key).asInt
    } catch (e : Exception) {
        get(key).asString.toInt()
    }
}