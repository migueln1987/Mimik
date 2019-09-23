package helpers

import com.beust.klaxon.JsonObject
import com.beust.klaxon.Parser
import io.ktor.application.ApplicationCall
import io.ktor.request.contentType
import io.ktor.request.httpMethod
import io.ktor.request.receiveText
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okio.Buffer
import okreplay.Request
import org.w3c.dom.NodeList
import java.nio.charset.Charset

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

fun NodeList.asList() = (0..length).mapNotNull(this::item)

suspend fun ApplicationCall.toOkRequest(outboundHost: String = "local.host"): okhttp3.Request {
    val requestBody = try {
        receiveText()
    } catch (e: Exception) {
        System.out.println(e)
        ""
    }

    return okhttp3.Request.Builder().also { build ->
        build.url(
            "%s://%s%s".format(request.local.scheme, outboundHost, request.local.uri)
        )

        val headerCache = Headers.Builder()
        request.headers.forEach { s, list ->
            list.forEach { headerCache.set(s, it) }
        }

        // resolve what host would be taking to
        if ((headerCache.get("Host") ?: "").startsWith("0.0.0.0"))
            headerCache.set("Host", outboundHost)

        build.headers(headerCache.build())

        build.method(
            request.httpMethod.value,
            RequestBody.create(
                MediaType.parse(request.contentType().toString()),
                requestBody
            )
        )
    }.build()
}

fun okhttp3.Request.reHost(outboundHost: HttpUrl): okhttp3.Request {
    return newBuilder().also { build ->
        build.url(
            "%s://%s%s".format(
                outboundHost.scheme(),
                outboundHost.host(),
                url().encodedPath()
            )
        )
    }.build()
}

/**
 * Returns the string contents of a RequestBody
 */
val RequestBody?.content: String
    get() = this?.let { _ ->
        Buffer().let { buffer ->
            writeTo(buffer)
            val charset: Charset = contentType()?.charset() ?: Charset.defaultCharset()
            buffer.readString(charset)
        }
    } ?: ""

val ResponseBody?.content: String
    get() {
        return try {
            this?.let { string() } ?: ""
        } catch (e: Exception) {
            ""
        }
    }
