package apiTests

import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.handleRequest
import org.junit.Assert
import org.junit.Test

class HttpTapeTests {
    @Test
    fun deleteTape() {
        val tapeName = "DeleteTapeTest"
        TestApp {
            handleRequest(HttpMethod.Put, "/mock") {
                addHeader("mockTape_Name", tapeName)
                addHeader("mockTape_Only", "true")
            }.apply {
                response {
                    Assert.assertEquals(HttpStatusCode.Created, it.status())
                }
            }

            handleRequest(HttpMethod.Get, "/tapes/delete?tape=$tapeName") {}
                .apply {
                    response {
                        val hasTape = TapeCatalog.Instance.tapes
                            .any { it.tapeName == tapeName }

                        Assert.assertFalse(hasTape)
                    }
                }
        }
    }
}
