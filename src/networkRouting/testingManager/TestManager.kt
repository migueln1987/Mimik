package networkRouting.testingManager

import com.google.gson.Gson
import com.google.gson.internal.LinkedTreeMap
import helpers.*
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode
import io.ktor.response.respondText
import io.ktor.routing.Route
import io.ktor.routing.post
import io.ktor.routing.route
import io.ktor.util.pipeline.PipelineContext
import kolor.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import networkRouting.RoutingContract
import networkRouting.testingManager.TestBounds.Companion.DataTypes
import java.time.Duration
import java.util.Date
import kotlin.Exception

@Suppress("RemoveRedundantQualifierName")
class TestManager : RoutingContract(RoutePaths.rootPath) {
    private enum class RoutePaths(val path: String) {
        START("start"),
        APPEND("append"),
        Modify("modify"),
        DISABLE("disable"),
        STOP("stop");

        companion object {
            const val rootPath = "tests"
        }
    }

    private val finalizer = "##Finalize"
    private val initTag = "##Init"
    private val allTag = "##All"

    override fun init(route: Route) {
        route.route(path) {
            start
            append
            modify
            disable
            stop
        }
    }

    companion object {
        val boundManager = mutableListOf<TestBounds>()
        private val lock = Semaphore(1)

        /**
         * Attempts to retrieve a [TestBounds] which has the following [boundID].
         * - If none is found and a bound is [BoundStates.Ready], the [boundID] is applied to that bound, then returned
         * - [null] is returned if none of the above is true
         */
        suspend fun getManagerByID(boundID: String?): TestBounds? {
            if (boundID.isNullOrBlank()) return null
            return lock.withPermit {
                // look for existing started bounds
                var bound = boundManager.asSequence()
                    .filter { it.state == BoundStates.Started && it.boundSource == boundID }
                    .sortedBy { it.startTime }
                    .firstOrNull()
                if (bound != null)
                    return@withPermit bound

                // look for a new slot to start a bounds
                bound = boundManager.asSequence()
                    .filter { it.state == BoundStates.Ready }
                    .sortedBy { it.createTime }
                    .firstOrNull()
                if (bound != null) {
                    bound.boundSource = boundID
                    return@withPermit bound
                }

                // return the last bounds to show error meta-data
                return@withPermit boundManager.asSequence()
                    .filter { !it.finalized && it.boundSource == boundID }
                    .sortedBy { it.startTime }
                    .firstOrNull()
            }
        }
    }

    private fun Headers.getValidTapes(): List<String> {
        val heads = getAll("tape")
        if (heads.isNullOrEmpty()) return listOf()
        val tapeCatNames = tapeCatalog.tapes.map { it.name }
        val tapeFlags = listOf(initTag, allTag)
        val containsFlags = tapeFlags.filter { heads.contains(it) }

        return heads.flatMap { it.split(',') }.asSequence()
            .filterNot { it.isBlank() }
            .map { it.trim() }
            .filter { tapeCatNames.contains(it) }
            .toList()
            .let {
                if (containsFlags.isNotEmpty())
                    containsFlags.toMutableList()
                else it
            }
    }

    private val Route.start: Route
        get() = route(RoutePaths.START.path) {
            /**
             * Response codes:
             * - 200 (OK) {New bounds with given name}
             * - 201 (Created) {Created new name}
             * - 400 (BadRequest)
             * - 412 (PreconditionFailed) {Mimik has no tapes}
             */
            post {
                val heads = call.request.headers
                var handle = heads["handle"]
                val allowedTapes = heads.getValidTapes()
                var time = heads["time"]

                val noConfigs = allTrue(
                    handle.isNullOrBlank(),
                    allowedTapes.isEmpty(),
                    time.isNullOrBlank()
                )

                var canContinue = false
                when {
                    tapeCatalog.tapes.isEmpty() && !allowedTapes.contains(allTag) ->
                        call.respondText(status = HttpStatusCode.PreconditionFailed) { "No tapes to append this test to" }
                    noConfigs ->
                        call.respondText(status = HttpStatusCode.BadRequest) { "No config headers" }
                    allowedTapes.isEmpty() ->
                        call.respondText(status = HttpStatusCode.BadRequest) { "No [tape] config data" }
                    else -> canContinue = true
                }
                if (!canContinue) return@post

                var testBounds: TestBounds? = null
                var replaceHandleName = false

                if (handle.isNullOrBlank()) {
                    handle = createUniqueName()
                    printlnF(
                        "%s -> %s",
                        "No given handle name".magenta(),
                        "Creating new handle: $handle".green()
                    )
                }

                testBounds = boundManager.firstOrNull { it.handle == handle }

                if (testBounds != null) {
                    if (testBounds.state != BoundStates.Stopped) {
                        printlnF(
                            "Conflict bounds found, stopping the following test:\n- %s".magenta(),
                            handle
                        )
                        testBounds.stopTest()
                    }
                    replaceHandleName = true
                    handle = ensureUniqueName(handle)
                    println("Creating new test named: $handle".green())
                }

                val replacers = getReplacers()

                testBounds = TestBounds(handle, allowedTapes.toMutableList()).also {
                    replacers?.forEach { (t, u) ->
                        it.replacerData[t] = u
                    }
                }
                boundManager.add(testBounds)

                time = time ?: "5m"
                val timeVal = time.replace("\\D".toRegex(), "").toLongOrNull() ?: 5
                val timeType = time.replace("\\d".toRegex(), "")

                testBounds.timeLimit = when (timeVal) {
                    in (Long.MIN_VALUE..0) -> Duration.ofHours(1)
                    else -> when (timeType) {
                        "m" -> Duration.ofMinutes(timeVal)
                        "h" -> Duration.ofHours(timeVal)
                        "s" -> Duration.ofSeconds(timeVal)
                        else -> Duration.ofSeconds(timeVal)
                    }
                }

                val status = when (heads["handle"]) {
                    null, "" -> HttpStatusCode.Created
                    else -> {
                        if (replaceHandleName)
                            HttpStatusCode.Created
                        else
                            HttpStatusCode.OK
                    }
                }

                var findVars: Int? = null
                var remappers: Int? = null
                if (!replacers.isNullOrEmpty()) {
                    findVars = replacers.values.sumBy { it.findVars.size }
                    remappers = replacers.values.sumBy { it.replacers_body.size }
                }
                printlnF(
                    "Test Bounds (%s) ready with [%d] tapes:".green() +
                            "%s".cyan(),
                    testBounds.handle,
                    allowedTapes.size,
                    allowedTapes.joinToString(
                        prefix = "\n- ",
                        separator = "\n- "
                    ) { it }
                )

                println("With:".green())
                println(" - [${findVars ?: 0}]".cyan() + " Variable finders".green())
                println(" - [${remappers ?: 0}]".cyan() + " Response modifiers".green())

                call.response.headers.apply {
                    append("tape", allowedTapes)
                    append("handle", handle.toString())
                    if (remappers != null)
                        append("mappers", remappers.toString())
                }

                call.respondText(status = status) { "" }
            }
        }

    private val Route.append: Route
        get() = route(RoutePaths.APPEND.path) {
            /**
             * Response codes:
             * - 200 (OK)
             * - 304 (NotModified)
             * - 400 (BadRequest)
             */
            post {
                val heads = call.request.headers
                val handle = heads["handle"]
                val appendTapes = heads.getValidTapes()

                val noConfigs = allTrue(
                    handle.isNullOrBlank(),
                    appendTapes.isEmpty()
                )
                val boundHandle = boundManager.firstOrNull { it.handle == handle }

                var canContinue = false
                when {
                    noConfigs ->
                        call.respondText(status = HttpStatusCode.BadRequest) { "No config headers" }
                    appendTapes.isEmpty() ->
                        call.respondText(status = HttpStatusCode.NotModified) { "No [tape] data to append" }
                    boundHandle == null ->
                        call.respondText(status = HttpStatusCode.BadRequest) { "No test bounds with the name '$handle'" }
                    else -> canContinue = true
                }
                if (!canContinue) return@post

                printlnF(
                    "Appending %s tape%s to test (%s)".green(),
                    appendTapes.size,
                    if (appendTapes.size > 1) "s" else "",
                    handle
                )
                requireNotNull(boundHandle)
                boundHandle.tapes.addAll(appendTapes)
                call.response.headers.append("tape", appendTapes)
                call.respondText(status = HttpStatusCode.OK) { "" }
            }
        }

    private suspend fun PipelineContext<*, ApplicationCall>.getReplacers() =
        try {
            getReplacers_v2()
        } catch (_: Exception) {
            null
//            try {
//                getReplacers_v1()
//            } catch (_: Exception) {
//                null
//            }
        }

    /**
     * Example:
     * ```json
     * {
     *   "aa": {
     *     "Body": [{
     *         "from": "bb",
     *         "to": "cc"
     *       },
     *       {
     *         "from": "dd",
     *         "to": "dd"
     *       }
     *     ]
     *   }
     * }
     * ```
     */
    private suspend fun PipelineContext<*, ApplicationCall>.getReplacers_v1() = Gson()
        .fromJson(call.tryGetBody().orEmpty(), LinkedTreeMap::class.java).orEmpty()
        .mapKeys { it.key as String }
        .mapValues { mv ->
            (mv.value as LinkedTreeMap<*, *>)
                .mapKeys { DataTypes.valueOf(it.key.toString().uppercaseFirstLetter()) }
                .mapValues { mvv ->
                    (mvv.value as List<*>)
                        .map { (it as LinkedTreeMap<*, *>).map { it.value.toString() } }
                        .mapNotNull { if (it.size >= 2) it[0] to it[1] else null }
                        .toMutableList()
                }.toMap(mutableMapOf())
        }.toMutableMap()

    /**
     * Example:
     * ```json
     * {
     *   "aa": [
     *     "body{[abc]->none}",
     *     "body{[abc]->none}",
     *     "var{[abc]->SaveVar}"
     *   ],
     *   "bb": [
     *     "body{[ab]->none}"
     *   ]
     * }
     * ```
     */
    private suspend fun PipelineContext<*, ApplicationCall>.getReplacers_v2() =
        parse_v2(call.tryGetBody().orEmpty())

    private fun String.toJsonMap(): Map<String, Any>? {
        if (isEmpty()) return mapOf()
        return try {
            Gson()
                .fromJson(this, LinkedTreeMap::class.java).orEmpty()
                .mapKeys { it.key.toString() }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun parse_v2(body: String): MutableMap<String, boundChapterItems> {
        return (body.toJsonMap() ?: return mutableMapOf())
            .mapValues { mv ->
                (mv.value as List<*>)
                    .map {
                        val contents = it.toString().split("{")
                        Pair(
                            contents.getOrNull(0),
                            contents.drop(1).joinToString(separator = "{").dropLast(1)
                                .split("->")
                                .run { if (size >= 2) Pair(get(0), get(1)) else null }
                        )
                    }
                    .filterNot { it.first == null || it.second == null }
                    .map { it.first!! to it.second!! }
                    .groupBy { it.first }
                    .map { (key, data) ->
                        key to data.map { it.second }.toMutableList()
                    }
            }
            .mapValues { (_, data) ->
                boundChapterItems().also { bItems ->
                    data.forEach {
                        when (it.first.toLowerCase()) {
                            "body" -> {
                                bItems.replacers_body.clear()
                                bItems.replacers_body.addAll(it.second)
                            }
                            "var" -> {
                                bItems.findVars.clear()
                                bItems.findVars.addAll(it.second)
                            }
                        }
                    }
                }
            }.toMutableMap()
    }

    private val Route.modify: Route
        get() = route(RoutePaths.Modify.path) {
            /**
             * Response codes:
             * - 400 (BadRequest)
             * - 204 (NoContent)
             * - 200 (OK)
             */
            post {
                val heads = call.request.headers
                val handle = heads["handle"]
                val testBounds = boundManager.firstOrNull { it.handle == handle }

                var canContinue = false
                when {
                    handle == null ->
                        call.respondText(status = HttpStatusCode.BadRequest) { "Invalid handle name" }
                    testBounds == null ->
                        call.respondText(status = HttpStatusCode.BadRequest) { "No test bounds with the name($handle)" }
                    else -> canContinue = true
                }
                if (!canContinue) return@post

                val replacers = getReplacers()
                if (replacers.isNullOrEmpty()) {
                    call.respondText(status = HttpStatusCode.NoContent) { "No modifications entered" }
                } else {
                    requireNotNull(testBounds)
                    replacers.forEach { (t, u) ->
                        testBounds.replacerData[t] = u
                    }

                    val findVars = replacers.values.sumBy { it.findVars.size }
                    val remappers = replacers.values.sumBy { it.replacers_body.size }

                    println("Applying the following bound items to test bounds ${testBounds.handle}:".green())
                    println(" - [$findVars]".cyan() + " Variable finders".green())
                    println(" - [$remappers]".cyan() + " Response modifiers".green())

                    val items = findVars + remappers
                    call.respondText(status = HttpStatusCode.OK) { "Appended $items to test bounds ${testBounds.handle}" }
                }
            }
            // todo; patch
        }

    private val Route.disable: Route
        get() = route(RoutePaths.DISABLE.path) {
            /**
             * Response codes:
             * - 200 (OK)
             * - 304 (NotModified)
             * - 400 (BadRequest)
             */
            post {
                val heads = call.request.headers
                val handle = heads["handle"]
                val disableTapes = heads.getValidTapes()

                val noConfigs = allTrue(
                    handle.isNullOrBlank(),
                    disableTapes.isEmpty()
                )
                val boundHandle = boundManager.firstOrNull { it.handle == handle }

                var canContinue = false
                when {
                    noConfigs ->
                        call.respondText(status = HttpStatusCode.BadRequest) { "No config headers" }
                    disableTapes.isEmpty() ->
                        call.respondText(status = HttpStatusCode.NotModified) { "No requested [tape] to disable" }
                    boundHandle == null ->
                        call.respondText(status = HttpStatusCode.BadRequest) { "No test bounds with the name '$handle'" }
                    else -> canContinue = true
                }
                if (!canContinue) return@post

                requireNotNull(boundHandle)
                var dCount = 0
                when {
                    disableTapes.contains(allTag) -> {
                        dCount += boundHandle.tapes.size
                        boundHandle.tapes.clear()
                    }
                    else -> {
                        disableTapes.forEach { t ->
                            boundHandle.tapes.removeIf {
                                (it == t).also { dCount++ }
                            }
                        }
                    }
                }

                printlnF(
                    "Disabling %s tape%s in test (%s)".green(),
                    dCount,
                    if (dCount > 1) "s" else "",
                    handle
                )
                call.respondText(status = HttpStatusCode.OK) { "" }
            }
        }

    private val Route.stop: Route
        get() = route(RoutePaths.STOP.path) {
            /**
             * Response codes:
             * - 200 (OK)
             * - 304 (NotModified)
             * - 400 (BadRequest)
             */
            post {
                val heads = call.request.headers
                val handles = heads["handle"]?.split(',')
                    ?.map { it.trim() }

                if (handles == null || handles.isEmpty()) {
                    call.respondText(status = HttpStatusCode.BadRequest) { "No [handle] parameter" }
                    return@post
                }

                if (handles.size > 1 && handles.any { it == finalizer }) {
                    var finalized = 0
                    handles.asSequence()
                        .filterNot { it == finalizer }
                        .mapNotNull { h -> boundManager.firstOrNull { it.handle == h } }
                        .filterNot { it.finalized }
                        .forEach {
                            finalized++
                            it.finalized = true
                        }
                    println("Finalized $finalized tests.".green())
                    call.response.headers.append("finalized", finalized.toString())
                    call.respondText(status = HttpStatusCode.OK) { "" }
                    return@post
                }

                var tryStopped = 0
                boundManager.asSequence()
                    .filter { handles.contains(it.handle) }
                    .forEach {
                        val sb = StringBuilder()
                        sb.appendln("Stopping test bounds (${it.handle})...")
                        when (it.state) {
                            BoundStates.Ready -> sb.append("Test was idle. no change".green())
                            BoundStates.Stopped -> sb.append("Test was already stopped")
                            BoundStates.Started -> sb.append("Test was stopped".green())
                            else -> sb.append("Test was in an unknown state".magenta())
                        }
                        println(sb.toString().yellow())

                        it.stopTest()
                        tryStopped++

                        val duration = it.expireTime?.let { expTime ->
                            (it.timeLimit - (Date() - expTime)).toString().removePrefix("PT")
                        } ?: "[Idle]"

                        call.response.headers.append("${it.handle}_time", duration)
                        printlnF(
                            "Test bounds (%s) ran for %s".green(),
                            it.handle,
                            duration
                        )
                    }

                call.response.headers.append("stopped", tryStopped.toString())

                val status = when (tryStopped) {
                    handles.size -> HttpStatusCode.OK
                    else -> {
                        call.response.headers.append("missing", (handles.size - tryStopped).toString())
                        printlnF(
                            "%d Test Bounds were not stopped/ unchanged".magenta(),
                            handles.size - tryStopped
                        )
                        HttpStatusCode.NotModified
                    }
                }

                call.respondText(status = status) { "" }
            }
        }

    fun createUniqueName(): String {
        val randHost = RandomHost()

        var result = randHost.valueAsChars()
        while (boundManager.any { it.handle == result }) {
            println("New Handle: $result".magenta())
            randHost.nextRandom()
            result = randHost.valueAsChars()
        }

        return result
    }

    fun ensureUniqueName(handle: String): String {
        var inc = 0
        var result = handle + "_" + inc

        while (boundManager.any { it.handle == result }) {
            boundManager.firstOrNull { it.handle == result }?.also {
                if (it.state != BoundStates.Stopped) {
                    printlnF(
                        "Conflict bounds found, stopping the following test:\n- %s".red(),
                        result
                    )
                    it.stopTest()
                }
            }
            inc++
            result = handle + "_" + inc
        }
        return result
    }
}
