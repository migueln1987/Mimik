package mimikMockHelpers

import io.ktor.http.HttpStatusCode

class QueryResponse<T>(build: QueryResponse<T>.() -> Unit = {}) {
    var item: T? = null
    var status: HttpStatusCode = HttpStatusCode.NoContent
    var responseMsg: String? = null

    init {
        build.invoke(this)
    }
}
