package com.pausiar.openfy.playback

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException

class NewPipeDownloader(
    private val client: OkHttpClient,
) : Downloader() {
    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: Request): Response {
        val httpRequest = okhttp3.Request.Builder()
            .url(request.url())
            .method(
                request.httpMethod(),
                request.dataToSend()?.takeIf { it.isNotEmpty() }?.toRequestBody(),
            )
            .apply {
                request.headers().forEach { (key, values) ->
                    values.forEach { value -> addHeader(key, value) }
                }
            }
            .build()

        val httpResponse = client.newCall(httpRequest).execute()
        val body = httpResponse.body?.string().orEmpty()
        val latestUrl = httpResponse.request.url.toString()

        if (httpResponse.code == 429) {
            throw ReCaptchaException("reCaptcha Challenge requested", latestUrl)
        }

        return Response(
            httpResponse.code,
            httpResponse.message,
            httpResponse.headers.toMultimap(),
            body,
            latestUrl,
        )
    }
}
