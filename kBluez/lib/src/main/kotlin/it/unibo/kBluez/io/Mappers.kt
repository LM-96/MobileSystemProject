package it.unibo.kBluez.io

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.*

/**
 * Returns a mapper from JsonObject to String that enable the passage for the json
 * that have at least one of the key passed as params
 */
fun jsonObjectToStringAllowedKeyFilter(vararg keys : String) : (JsonObject) -> Optional<String> {
    return {
        try {
            for(key in keys) {
                if(it.has(key))
                    Optional.of(it.toString())
            }
            Optional.empty()
        } catch (e : Exception) {
            Optional.empty()
        }
    }
}

/**
 * Returns a mapper from String to JsonObjet that enable the passage for the json
 * that have at least one of the key passed as params
 */
fun stringToJsonObjectAllowedKeyFilter(vararg keys : String) : (String) -> Optional<JsonObject> {
    return {
        try {
            val jsonObj = JsonParser.parseString(it).asJsonObject
            for(key in keys) {
                if(jsonObj.has(key))
                    Optional.of(it)
            }
            Optional.empty()
        } catch (e : Exception) {
            Optional.empty()
        }
    }
}

/**
 * Returns a mapper from JsonObject to String that deny the passage for the json
 * that have at least one of the key passed as params
 */
fun jsonObjectToStringDeniedKeyFilter(vararg keys : String) : (JsonObject) -> Optional<String> {
    return {
        try {
            for(key in keys) {
                if(it.has(key))
                    Optional.empty<String>()
            }
            Optional.of(it.toString())
        } catch (e : Exception) {
            Optional.empty()
        }
    }
}

/**
 * Returns a mapper from String to JsonObjet that enable the passage for the json
 * that have at least one of the key passed as params
 */
fun stringToJsonObjectDeniedKeyFilter(vararg keys : String) : (String) -> Optional<JsonObject> {
    return {
        try {
            val jsonObj = JsonParser.parseString(it).asJsonObject
            for(key in keys) {
                if(jsonObj.has(key))
                    Optional.empty<JsonObject>()
            }
            Optional.of(jsonObj)
        } catch (e : Exception) {
            Optional.empty()
        }
    }
}