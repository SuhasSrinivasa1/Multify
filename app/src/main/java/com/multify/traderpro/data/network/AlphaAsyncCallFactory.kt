package com.multify.traderpro.data.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.Timeout
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Retrofit suspend requests enqueue here. API quota is reserved in a cancellable coroutine,
 * not in an OkHttp interceptor worker. The original HTTP request still uses the same
 * recovery gate, request accounting, response cache, endpoints and client timeouts.
 */
internal class AlphaAsyncCallFactory(
    private val client: OkHttpClient,
    private val governor: GrowwRateLimitInterceptor,
    private val hotCache: GrowwHotDataCacheInterceptor
) : Call.Factory {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun newCall(request: Request): Call = DeferredAlphaCall(request)

    private inner class DeferredAlphaCall(private val original: Request) : Call {
        private val executed = AtomicBoolean(false)
        private val completed = AtomicBoolean(false)
        private val cancelled = AtomicBoolean(false)
        private val transport = client.newCall(
            original.newBuilder().tag(
                GrowwRateLimitInterceptor.PreAdmitted::class.java,
                GrowwRateLimitInterceptor.PreAdmitted()
            ).build()
        )
        @Volatile private var admissionJob: Job? = null

        override fun request(): Request = original
        override fun timeout(): Timeout = transport.timeout()
        override fun isExecuted(): Boolean = executed.get()
        override fun isCanceled(): Boolean = cancelled.get() || transport.isCanceled()
        override fun cancel() {
            cancelled.set(true)
            admissionJob?.cancel()
            transport.cancel()
        }
        override fun clone(): Call = DeferredAlphaCall(original)

        override fun enqueue(responseCallback: Callback) {
            check(executed.compareAndSet(false, true)) { "Already Executed" }
            val cached = hotCache.freshResponse(original)
            if (cached != null) {
                completed.set(true)
                responseCallback.onResponse(this, cached)
                return
            }
            admissionJob = scope.launch {
                try {
                    governor.acquirePermit(original)
                    if (isCanceled()) throw IOException("Canceled")
                    transport.enqueue(object : Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            if (completed.compareAndSet(false, true)) responseCallback.onFailure(this@DeferredAlphaCall, e)
                        }
                        override fun onResponse(call: Call, response: Response) {
                            if (completed.compareAndSet(false, true)) responseCallback.onResponse(this@DeferredAlphaCall, response)
                            else response.close()
                        }
                    })
                } catch (t: Throwable) {
                    val e = if (t is IOException) t else if (t is CancellationException)
                        IOException("Canceled", t) else IOException("Alpha API admission failed", t)
                    if (completed.compareAndSet(false, true)) responseCallback.onFailure(this@DeferredAlphaCall, e)
                }
            }
            if (cancelled.get()) admissionJob?.cancel()
        }

        override fun execute(): Response {
            check(executed.compareAndSet(false, true)) { "Already Executed" }
            hotCache.freshResponse(original)?.let { return it }
            // Retrofit uses enqueue for suspend endpoints. Synchronous callers (off-main)
            // retain compatible execution semantics without occupying OkHttp workers.
            runBlocking { governor.acquirePermit(original) }
            if (isCanceled()) throw IOException("Canceled")
            return transport.execute()
        }
    }
}
