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