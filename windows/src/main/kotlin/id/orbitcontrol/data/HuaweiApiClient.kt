package id.orbitcontrol.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

data class HuaweiRawResponse(val status: Int, val body: String, val headers: Headers)

class HuaweiApiException(
    override val message: String,
    val code: String? = null,
    val path: String? = null,
    cause: Throwable? = null,
) : IOException(message, cause)

class HuaweiApiClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build(),
) {
    fun normalizeHost(input: String): String {
        val candidate = input.trim().ifEmpty { "http://192.168.8.1" }
            .let { if (it.contains("://")) it else "http://$it" }
        val url = candidate.toHttpUrlOrNull() ?: throw HuaweiApiException("Alamat modem tidak valid.")
        if (url.scheme != "http" && url.scheme != "https") {
            throw HuaweiApiException("Alamat modem harus memakai HTTP atau HTTPS.")
        }
        return url.newBuilder().encodedPath("/").query(null).fragment(null).build().toString().trimEnd('/')
    }

    suspend fun get(
        host: String,
        path: String,
        cookie: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): HuaweiRawResponse = execute(host, path, "GET", null, cookie, headers)

    suspend fun postXml(
        host: String,
        path: String,
        xml: String,
        cookie: String?,
        token: String,
        headers: Map<String, String> = emptyMap(),
    ): HuaweiRawResponse = execute(
        host = host,
        path = path,
        method = "POST",
        body = xml,
        cookie = cookie,
        headers = mapOf(
            "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8",
            "__RequestVerificationToken" to token,
            "_ResponseSource" to "Broswer",
        ) + headers,
    )

    private suspend fun execute(
        host: String,
        path: String,
        method: String,
        body: String?,
        cookie: String?,
        headers: Map<String, String>,
    ): HuaweiRawResponse = withContext(Dispatchers.IO) {
        val base = normalizeHost(host)
        val safePath = if (path.startsWith('/')) path else "/$path"
        val builder = Request.Builder().url(base + safePath)
            .header("Accept", "application/xml, text/xml, */*")
            .header("Connection", "keep-alive")
        cookie?.takeIf(String::isNotBlank)?.let { builder.header("Cookie", it) }
        headers.forEach(builder::header)
        if (method == "POST") {
            val mediaType = "application/x-www-form-urlencoded; charset=UTF-8".toMediaType()
            builder.post(body.orEmpty().toRequestBody(mediaType))
        } else builder.get()

        try {
            http.newCall(builder.build()).execute().use { response ->
                HuaweiRawResponse(response.code, response.body?.string().orEmpty(), response.headers)
            }
        } catch (error: IOException) {
            throw HuaweiApiException("Tidak dapat menghubungi modem di $base.", path = path, cause = error)
        }
    }
}
