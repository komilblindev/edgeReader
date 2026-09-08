package uz.komil.mediapro.net

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.TimeUnit

/** Shared OkHttp client with mobile-friendly timeouts. */
object Http {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    /** Follows the browser-ish user agent many media hosts expect. */
    private const val UA =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0 Mobile Safari/537.36"

    fun get(url: String, headers: Map<String, String> = emptyMap()): Response {
        val b = Request.Builder().url(url).header("User-Agent", UA).header("Accept", "*/*")
        headers.forEach { (k, v) -> b.header(k, v) }
        return client.newCall(b.build()).execute()
    }

    fun getString(url: String, headers: Map<String, String> = emptyMap()): String {
        get(url, headers).use { r ->
            if (!r.isSuccessful) throw java.io.IOException("HTTP ${r.code} for $url")
            return r.body!!.string()
        }
    }

    fun getBytes(url: String, headers: Map<String, String> = emptyMap()): ByteArray {
        get(url, headers).use { r ->
            if (!r.isSuccessful) throw java.io.IOException("HTTP ${r.code} for $url")
            return r.body!!.bytes()
        }
    }

    /** Resolve a possibly-relative media URL against the manifest base. */
    fun resolve(baseUrl: String, ref: String): String {
        if (ref.startsWith("http://") || ref.startsWith("https://")) return ref
        val base = java.net.URI(baseUrl)
        return base.resolve(ref).toString()
    }
}
