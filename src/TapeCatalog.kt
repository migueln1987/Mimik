package com.fiserv.ktmimic

import com.fiserv.ktmimic.tapeTypes.CFCTape
import com.fiserv.ktmimic.tapeTypes.GeneralTape
import com.fiserv.ktmimic.tapeTypes.NewTapes
import com.fiserv.ktmimic.tapeTypes.baseTape
import com.fiserv.ktmimic.tapeTypes.helpers.toChain
import io.ktor.application.ApplicationCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okreplay.OkReplayConfig
import okreplay.OkReplayInterceptor
import okhttp3.Response
import okreplay.Tape
import java.util.logging.Logger

class TapeCatalog(private val config: OkReplayConfig) : OkReplayInterceptor() {
    private val log by lazy { Logger.getLogger("TapeCatalog") }

    private val defaultTape = NewTapes()

    private val tapes by lazy {
        arrayOf(
            defaultTape,
            GeneralTape(),
            CFCTape()
        )
    }

    private val tapeCalls: HashMap<String, Tape> = hashMapOf()

    private var lastLoadedTape: String? = null

    init {
        baseTape.tapeRoot = config.tapeRoot
        catalogTapeCalls()
    }

    /**
     * Parses all the tape into [opId, Tape] for easy tape recall
     */
    private fun catalogTapeCalls() {
        tapes.forEach { tape ->
            tape.chapterTitles.forEach { key ->
                if (tapeCalls.containsKey(key)) {
                    log.warning("Catalog already contains a tape chapter title of $key")
                } else {
                    tapeCalls[key] = tape
                }
            }
        }
    }

    private fun getTape(opId: String) = tapeCalls.getOrDefault(opId, defaultTape)

    suspend fun processCall(call: ApplicationCall, tapeKey: () -> String): Response {
        val key = tapeKey.invoke()
        if (lastLoadedTape != key) {
            lastLoadedTape = key
            start(config, getTape(key))
        }

        return withContext(Dispatchers.IO) {
            intercept(call.toChain())
        }
    }
}