package networkRouting

import TapeCatalog
import helpers.anyTrue
import helpers.attractors.RequestAttractorBit
import helpers.attractors.RequestAttractors
import helpers.isJSONValid
import helpers.isJSONValidMsg
import helpers.isTrue
import helpers.removePrefix
import helpers.toHeaders
import helpers.valueOrNull
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode
import io.ktor.request.receiveText
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.Routing
import io.ktor.routing.put
import io.ktor.routing.route
import java.lang.Exception
import mimikMockHelpers.MockUseStates
import mimikMockHelpers.QueryResponse
import mimikMockHelpers.ResponseTapedata
import okhttp3.internal.http.HttpMethod
import tapeItems.BlankTape

@Suppress("RemoveRedundantQualifierName")
class MimikMock(path: String) : RoutingContract(path) {

    private val tapeCatalog by lazy { TapeCatalog.Instance }

    private enum class RoutePaths(val path: String) {
        MOCK("");
    }

    override fun init(route: Routing) {
        route.route(path) {
            mock
        }
    }

    private val Route.mock: Route
        get() = route(RoutePaths.MOCK.path) {
            put {
                call.processPutMock().apply {
                    println(responseMsg)
                    call.respond(status, responseMsg ?: "")
                }
            }
        }

    private suspend fun ApplicationCall.processPutMock(): QueryResponse<BlankTape> {
        val headers = request.headers
        var mockParams = headers.entries().asSequence()
            .filter { it.key.startsWith("mock", true) }
            .associateBy(
                { it.key.removePrefix("mock", true).toLowerCase() },
                { it.value[0] }
            )

        // Step 0: Pre-checks
        if (mockParams.isEmpty()) return QueryResponse {
            status = HttpStatusCode.BadRequest
            responseMsg = "Missing mock params. Ex: mock{variable}: {value}"
        }

        // Step 0.5: prepare variables
        mockParams = mockParams
            .filterNot { it.key.startsWith("filter", true) }
        val attractors = createRequestAttractor(request.headers)
        val alwaysLive = if (mockParams["live"].isTrue()) true else null

        // Step 1: get existing tape or create a new tape
        val query = putmockGetTape(mockParams)

        val tape = query.item ?: return QueryResponse {
            status = HttpStatusCode.InternalServerError
            responseMsg = HttpStatusCode.InternalServerError.description
        }

        when (query.status) {
            HttpStatusCode.Created -> { // set ONLY if this is a new tape
                if (attractors.hasData)
                    tape.attractors = attractors
            }
            HttpStatusCode.Found -> { // apply the tape's attractors to this object
                attractors.append(tape.attractors)
            }
        }

        query.item?.alwaysLive = alwaysLive

        if (mockParams["tape_save"].isTrue())
            tape.saveFile()

        if (mockParams.containsKey("tape_only")) return query.also {
            it.responseMsg = "Tape (%s): %s"
                .format(
                    if (query.status == HttpStatusCode.Created) "New" else "Old",
                    query.item?.name
                )
        }

        // Step 2: Get existing chapter (to override) or create a new one
        val interactionName = mockParams["name"]

        var creatingNewChapter = false
        val chapter =
            tape.chapters.firstOrNull { it.name == interactionName }
                ?: let {
                    creatingNewChapter = true
                    tape.createNewInteraction()
                }

        // Step 3: Set the MimikMock data
//        if (!chapter.hasRequestData)
//            chapter.requestData = RequestTapedata()

        val hasAwait = mockParams.containsKey("await")
        val awaitResponse = mockParams["await"].isTrue()

//        val requestMock = RequestTapedata() { builder ->
//            builder.method = mockParams["method"]
//
//            builder.url = tape.httpRoutingUrl?.newBuilder()
//                ?.apply {
//                    attractors.routingPath?.value?.also {
//                        addPathSegments(it.removePrefix("/"))
//                    }
//                    query(mockParams["route_params"])
//                }?.build().toString()
//
//            builder.headers = mockParams
//                .filter { it.key.startsWith("headerin_") }
//                .mapKeys { it.key.removePrefix("headerin_") }
//                .toHeaders.valueOrNull
//        }

        val bodyText = try {
            receiveText()
        } catch (e: Exception) {
            null
        }

        // Method will have a body and filter isn't allowing bodies
        if (HttpMethod.requiresRequestBody(mockParams["method"] ?: "") &&
            (attractors.queryBodyMatchers.isNullOrEmpty().isTrue() ||
                    attractors.queryBodyMatchers?.all { it.value.isBlank() }.isTrue())
        ) // add the default "accept all bodies" to calls requiring a body
            attractors.queryBodyMatchers = listOf(RequestAttractorBit(".*"))

        chapter.also { updateChapter ->
            updateChapter.alwaysLive = alwaysLive
            updateChapter.attractors = attractors
//            updateChapter.requestData = requestMock

            // In case we want to update an existing chapter's name
            updateChapter.chapterName = interactionName ?: updateChapter.name

            updateChapter.responseData = if (alwaysLive.isTrue() || (hasAwait && awaitResponse))
                null
            else ResponseTapedata { rData ->
                rData.code = mockParams["response_code"]?.toIntOrNull()

                rData.headers = mockParams
                    .filter { it.key.startsWith("headerout_") }
                    .mapKeys { it.key.removePrefix("headerout_") }
                    .toHeaders.valueOrNull

                // todo 1; Beautify the input if it's a valid json?
                // todo 2; skip body if the method doesn't allow bodies
                rData.body = bodyText
            }

            val useRequest = mockParams["use"]
            updateChapter.mockUses = if (mockParams["readonly"].isTrue()) {
                when (useRequest?.toLowerCase()) {
                    "disable" -> MockUseStates.DISABLE
                    else -> MockUseStates.ALWAYS
                }.state
            } else {
                useRequest?.toIntOrNull()
                    ?: when (useRequest?.toLowerCase()) {
                        "disable" -> MockUseStates.DISABLE
                        "always" -> MockUseStates.ALWAYS
                        else -> MockUseStates.asState(updateChapter.mockUses)
                    }.state
            }
        }

        if (tape.file?.exists().isTrue())
            tape.saveFile()

        val isJson = bodyText.isJSONValid

        // Step 4: Profit!!!
        return QueryResponse {
            val created = anyTrue(
                query.status == HttpStatusCode.Created,
                creatingNewChapter
            )
            status = if (created) HttpStatusCode.Created else HttpStatusCode.Found
            responseMsg = "Tape (%s): %s, Mock (%s): %s".format(
                if (query.status == HttpStatusCode.Created) "New" else "Old",
                query.item?.name,
                if (creatingNewChapter) "New" else "Old",
                chapter.name
            )
            if (!isJson && bodyText.isNullOrBlank()) {
                responseMsg += "\nNote; input body is not recognized as a valid json.\n" +
                        bodyText.isJSONValidMsg
            }
        }
    }

    /**
     * Creates a Request Attractor from the input [headers] that contain "mockFilter"
     */
    private fun createRequestAttractor(headers: Headers): RequestAttractors {
        val filterKey = "mockfilter_"
        val filters = headers.entries()
            .filter { it.key.contains(filterKey, true) }
            .associateBy(
                { it.key.toLowerCase().removePrefix(filterKey) },
                { it.value })

        val urlPath = filters["path"]?.firstOrNull()

        val paramAttractors = filters.filterAttractorKeys("param") {
            it.split("&")
        }

        val bodyAttractors = filters.filterAttractorKeys("body")

        return RequestAttractors { attr ->
            if (urlPath != null)
                attr.routingPath = RequestAttractorBit(urlPath)
            if (paramAttractors.isNotEmpty())
                attr.queryParamMatchers = paramAttractors
            if (bodyAttractors.isNotEmpty())
                attr.queryBodyMatchers = bodyAttractors
        }
    }

    /**
     * Filters this [key, values] map into a list of Attractor bits.
     * Function also sets [optional] and [required] flags
     */
    private fun Map<String, List<String>>.filterAttractorKeys(
        key: String,
        valueSplitter: (String) -> List<String>? = { null }
    ): List<RequestAttractorBit> {
        return asSequence()
            .filter { it.key.contains(key) }
            .filterNot { it.value.isEmpty() }
            .flatMap { kvvm ->
                kvvm.value.asSequence()
                    .filterNot { it.isEmpty() }
                    .flatMap {
                        (valueSplitter.invoke(it) ?: listOf(it)).asSequence()
                    }
                    .map {
                        RequestAttractorBit { bit ->
                            bit.optional = kvvm.key.contains("~")
                            bit.except = kvvm.key.contains("!")
                            bit.value = it
                        }
                    }
            }
            .toList()
    }

    /**
     * Attempts to find an existing tape suitable tape (by name) or creates a new one.
     */
    private fun putmockGetTape(mockParams: Map<String, String>): QueryResponse<BlankTape> {
        val paramTapeName = mockParams["tape_name"]?.split("/")?.last()
        // todo; add an option for sub-directories
        // val paramTapeDir = mockParams["tape_name"]?.replace(paramTapeName ?: "", "")

        val result = QueryResponse<BlankTape> {
            status = HttpStatusCode.NotFound
            item = tapeCatalog.tapes.firstOrNull { it.name.equals(paramTapeName, true) }
                ?.also { status = HttpStatusCode.Found }
        }

        if (result.status != HttpStatusCode.Found) {
            result.status = HttpStatusCode.Created
            result.item = BlankTape.Builder {
                it.tapeName = paramTapeName
                it.routingURL = mockParams["tape_url"]
                it.allowLiveRecordings = mockParams["tape_allowliverecordings"].isTrue(true)
            }.build()
                .also { tapeCatalog.tapes.add(it) }
        }

        return result
    }
}
