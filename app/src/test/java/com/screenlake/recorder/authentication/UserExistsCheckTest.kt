package com.screenlake.recorder.authentication

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * The user-exists check is off unless a study sets an endpoint, and when it is on, only a clear
 * "taken" answer stops sign-up.
 */
class UserExistsCheckTest {

    private val requests = mutableListOf<String>()

    @Test
    fun `with no endpoint nothing is sent`() = runBlocking {
        val check = UserExistsCheck(endpoint = "", client = client { respond(200, TAKEN) })

        assertFalse(check.isEnabled)
        assertFalse(check.exists("p@example.org"))
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `a taken email is reported and sent as a query parameter`() = runBlocking {
        val check = UserExistsCheck(endpoint = ENDPOINT, client = client { respond(200, TAKEN) })

        assertTrue(check.exists("p+1@example.org"))
        assertEquals("$ENDPOINT?email=p%2B1%40example.org", requests.single())
    }

    @Test
    fun `a free email, an error status or a bad answer lets sign-up continue`() = runBlocking {
        assertFalse(UserExistsCheck(ENDPOINT, client { respond(200, FREE) }).exists("p@example.org"))
        assertFalse(UserExistsCheck(ENDPOINT, client { respond(500, TAKEN) }).exists("p@example.org"))
        assertFalse(UserExistsCheck(ENDPOINT, client { respond(200, "not json") }).exists("p@example.org"))
        assertFalse(UserExistsCheck(ENDPOINT, client { respond(200, """{"items":[]}""") }).exists("p@example.org"))
    }

    @Test
    fun `no connection lets sign-up continue`() = runBlocking {
        val check = UserExistsCheck(ENDPOINT, client { throw IOException("offline") })

        assertFalse(check.exists("p@example.org"))
    }

    private fun client(answer: Interceptor.Chain.() -> Response) = OkHttpClient.Builder()
        .addInterceptor { chain ->
            requests += chain.request().url.toString()
            chain.answer()
        }
        .build()

    private fun Interceptor.Chain.respond(code: Int, body: String) = Response.Builder()
        .request(request())
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("")
        .body(body.toResponseBody("application/json".toMediaType()))
        .build()

    private companion object {
        const val ENDPOINT = "https://study.example.org/does-user-exist"
        const val TAKEN = """{"items":[{"message":true}]}"""
        const val FREE = """{"items":[{"message":false}]}"""
    }
}
