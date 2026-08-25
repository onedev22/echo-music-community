package com.amurayada.spotify

import kotlinx.serialization.json.*

internal fun JsonObject.obj(key: String): JsonObject? {
    val element = this[key]
    if (element is JsonObject) return element
    if (element is JsonNull) return null
    return try {
        element?.jsonObject
    } catch (e: Exception) {
        null
    }
}

internal fun JsonObject.str(key: String): String? {
    val element = this[key]
    if (element is JsonPrimitive) {
        return if (element.isString) element.content else element.contentOrNull
    }
    return null
}

internal fun JsonObject.arr(key: String): JsonArray? {
    val element = this[key]
    if (element is JsonArray) return element
    if (element is JsonNull) return null
    return try {
        element?.jsonArray
    } catch (e: Exception) {
        null
    }
}

internal fun JsonObject.int(key: String): Int? {
    val element = this[key]
    if (element is JsonPrimitive) return element.intOrNull
    return null
}

internal fun JsonObject.long(key: String): Long? {
    val element = this[key]
    if (element is JsonPrimitive) return element.longOrNull
    return null
}

internal fun JsonObject.bool(key: String): Boolean? {
    val element = this[key]
    if (element is JsonPrimitive) return element.booleanOrNull
    return null
}

internal fun log(level: String, msg: String) { println("[$level] $msg") }
