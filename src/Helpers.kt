package com.fiserv.ktmimic

import com.beust.klaxon.JsonObject
import com.beust.klaxon.Parser
import okreplay.Request
import java.lang.StringBuilder

fun Request.toJson(): String {
    val bodyString = StringBuilder()
    if (hasBody()) bodyString.append(String(body()))

    val jsonData = Parser.default().parse(bodyString) as JsonObject
    return jsonData.toJsonString(true, true)
}

fun okreplay.Response.toJson(): String {
    val bodyString = StringBuilder()
    if (hasBody()) bodyString.append(String(body()))

    val jsonData = Parser.default().parse(bodyString) as JsonObject
    return jsonData.toJsonString(true, true)
}

fun okhttp3.Response.toJson(): String {
    return (try {
        body()?.byteStream()?.let { stream ->
            (Parser.default().parse(stream) as JsonObject)
                .toJsonString(true, true)
        }
    } catch (_: Exception) {
        null
    }) ?: ""
}
