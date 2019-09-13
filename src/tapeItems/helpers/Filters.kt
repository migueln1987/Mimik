package tapeItems.helpers

import com.beust.klaxon.Parser
import Project.ignoreParams
import okreplay.MatchRule
import okreplay.Request

fun Request.filterBody(): String {
    val bodyString = StringBuilder()
    if (hasBody()) bodyString.append(String(body()))

    val jsonData = Parser.default().parse(bodyString) as com.beust.klaxon.JsonObject
    val filteredJsonData = jsonData.map.filter {
        !ignoreParams.contains(it.key)
    }

    return filteredJsonData.keys.sorted().joinToString { "," }
}

object filteredBody : MatchRule {
    override fun isMatch(a: Request, b: Request) =
        a.filterBody() == b.filterBody()
}
