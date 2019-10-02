package mimikMockHelpers

import helpers.tryGetBody
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.internal.http.HttpMethod
import java.nio.charset.Charset

class RequestTapedata : Tapedata {

    constructor(request: okreplay.Request) {
        method = request.method()
        url = request.url().toString()
        headers = request.headers()
        body = request.tryGetBody()
    }

    constructor(builder: (RequestTapedata) -> Unit = {}) {
        builder.invoke(this)

        if (method != null && HttpMethod.requiresRequestBody(method) && body == null)
            body = ""

        // todo; fact check to make sure the header is a multiple of 2
        if (!hasHeaders)
            headers = Headers.of("Content-Type", "text/plain")
    }

    fun clone(postClone: (RequestTapedata) -> Unit) = RequestTapedata {
        it.method = method
        it.url = url.toString()
        it.headers = headers.newBuilder().build()
        it.body = body
    }.also { postClone.invoke(it) }

    override fun toString(): String {
        return "%s: %s".format(method, url)
    }

    var method: String? = null
        get() = field?.toUpperCase() ?: "GET"

    var url: String? = ""
    val httpUrl: HttpUrl?
        get() = HttpUrl.parse(url ?: "")

    val replayRequest: okreplay.Request
        get() {
            return object : okreplay.Request {
                override fun url() = httpUrl ?: HttpUrl.get("http://invalid.com")
                override fun method() = method ?: "GET"

                override fun getEncoding() = ""
                override fun getCharset() = Charset.forName("UTF-8")

                override fun headers() = headers
                override fun header(name: String) = headers[name]
                override fun getContentType() = headers["Content-Type"]

                override fun hasBody() = !body.isNullOrBlank()
                override fun body() = bodyAsText().toByteArray()
                override fun bodyAsText() = body ?: ""

                override fun newBuilder() = TODO()
                override fun toYaml() = TODO()
            }
        }
}
