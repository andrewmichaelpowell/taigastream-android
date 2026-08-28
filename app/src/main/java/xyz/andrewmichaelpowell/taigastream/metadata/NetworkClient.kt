//  Taiga Stream
//  github.com/andrewmichaelpowell

package xyz.andrewmichaelpowell.taigastream.metadata

import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.CacheControl
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response

/**
 * Shared OkHttp plumbing used by every metadata provider, standing in for the repeated
 * `URLRequest.noCacheRequest` + `URLSession.shared.dataTask` boilerplate in the iOS providers
 * (Taiga Stream Widget/WidgetView.swift).
 */
object NetworkClient {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val noCache = CacheControl.Builder().noCache().noStore().build()

    fun get(url: String, headers: Map<String, String> = emptyMap()): Request {
        val builder = Request.Builder().url(url).cacheControl(noCache)
        headers.forEach { (key, value) -> builder.addHeader(key, value) }
        return builder.build()
    }

    fun post(url: String, body: RequestBody, headers: Map<String, String> = emptyMap()): Request {
        val builder = Request.Builder().url(url).cacheControl(noCache).post(body)
        headers.forEach { (key, value) -> builder.addHeader(key, value) }
        return builder.build()
    }

    /**
     * Fires [request], invoking [onBody] with the response body text on any successful response.
     * Failures and non-2xx responses are dropped silently, matching the iOS providers'
     * `guard let data, error == nil else { return }` pattern.
     */
    fun fetchBody(request: Request, onFailure: () -> Unit = {}, onBody: (String) -> Unit) {
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = onFailure()
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (it.isSuccessful) onBody(it.body.string()) else onFailure()
                }
            }
        })
    }

    /** Same contract as [fetchBody] but yields the raw response bytes (images, artwork, favicons). */
    fun fetchBytes(request: Request, onFailure: () -> Unit = {}, onBytes: (ByteArray) -> Unit) {
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = onFailure()
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (it.isSuccessful) onBytes(it.body.bytes()) else onFailure()
                }
            }
        })
    }
}
