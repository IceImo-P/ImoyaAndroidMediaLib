package net.imoya.android.media.audio.raw

import android.content.Context
import android.media.MediaFormat
import net.imoya.android.media.audio.AudioDecoder
import net.imoya.android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Convert application audio resources to [RawAudio]
 *
 * @param context [Context]
 * @param resourceIds Array of Resource ID
 */
@Suppress("unused")
class ResourcesToRawConverter(private val context: Context, private val resourceIds: IntArray) {
    private class ConverterContext(
        val context: Context,
        val lock: ReentrantLock,
        val condition: Condition,
        val map: HashMap<Int, RawAudio>,
        val errors: MutableList<Exception>
    )

    /**
     * [ConverterContext]
     */
    private lateinit var ctx: ConverterContext

    /**
     * [idSet] のキャッシュ
     */
    private lateinit var idSetCache: MutableSet<Int>

    /**
     * [result] のキャッシュ
     */
    private lateinit var resultCache: Array<RawAudio>

    /**
     * 重複しないリソースIDのセット
     */
    private val idSet: MutableSet<Int>
        get() {
            if (!this::idSetCache.isInitialized) {
                idSetCache = HashSet()
                resourceIds.forEach { idSetCache.add(it) }
            }
            return idSetCache
        }

    /**
     * 変換結果。コンストラクタの resourceIds 引数と同一の順番で [RawAudio] が格納されている配列である。
     * エラー発生時は空配列が返される。
     */
    @Suppress("weakness")
    val result: Array<RawAudio>
        get() {
            return if (!this::resultCache.isInitialized) {
                arrayOf()
            } else {
                this.resultCache
            }
        }

    /**
     * [AudioDecoder] callback implementation
     *
     * @param id resource ID
     * @param ctx [ConverterContext]
     * @param oneLock for notifying end of [AudioDecoder] to [ConvertOne]
     * @param oneCondition for notifying end of [AudioDecoder] to [ConvertOne]
     */
    private class DecoderCallbackImpl(
        private val id: Int,
        private val ctx: ConverterContext,
        private val oneLock: ReentrantLock,
        private val oneCondition: Condition
    ) :
        AudioDecoder.AudioDecoderCallback {
        override fun onEnd(decoder: AudioDecoder, data: ByteArray, format: MediaFormat) {
//            Log.v(TAG) { "callback.onEnd[$id]: start" }
            oneLock.withLock {
//                Log.v(TAG) { "callback.onEnd[$id]: entering lock" }
                ctx.map[id] = RawAudio(data, format)
//                Log.v(TAG) { "callback.onEnd[$id]: signalAll" }
                oneCondition.signalAll()
            }
//            Log.v(TAG) { "callback.onEnd[$id]: end" }
        }

        override fun onError(decoder: AudioDecoder, e: java.lang.Exception) {
//            Log.v(TAG) { "callback.onError[$id]: start: $e" }
            oneLock.withLock {
//                Log.v(TAG) { "callback.onError[$id]: entering lock" }
                ctx.errors.add(e)
//                Log.v(TAG) { "callback.onError[$id]: signalAll" }
                oneCondition.signalAll()
            }
//            Log.v(TAG) { "callback.onError[$id]: end" }
        }
    }

    /**
     * Convert an audio resource to [RawAudio]
     */
    private class ConvertOne(private val id: Int, private val ctx: ConverterContext) : Runnable {
        /**
         * for waiting [AudioDecoder]
         */
        private val localLock = ReentrantLock()

        /**
         * for waiting [AudioDecoder]
         */
        private lateinit var localCondition: Condition

        override fun run() {
            Log.v(TAG) {
                "convertOne: decoding $id, thread = ${Thread.currentThread().id}"
            }

            localCondition = localLock.newCondition()

            val decoder = AudioDecoder()
            decoder.setSource(ctx.context, id)
            decoder.convert(DecoderCallbackImpl(id, ctx, localLock, localCondition))

            // Wait for AudioDecoder
            localLock.withLock {
                localCondition.await()
            }

            // Notify to controller
            ctx.lock.withLock {
                ctx.condition.signalAll()
            }

            Log.v(TAG) { "convertOne: end. thread = ${Thread.currentThread().id}" }
        }
    }

    @Throws(java.lang.Exception::class)
    fun convert() {
        val lock = ReentrantLock()
        ctx = ConverterContext(context, lock, lock.newCondition(), HashMap(idSet.size), ArrayList())

        // 各音声リソースを RawAudio へ変換し、完了まで待つ
        executeAndWait()

        waitForComplete()

        if (ctx.errors.size > 0) {
            (0 until ctx.errors.size).forEach {
                Log.w(TAG) { "convert: error[$it]: ${ctx.errors[it]}" }
            }
            throw ctx.errors[0]
        } else {
            Log.v(TAG, "convert: success")
            resultCache = resourceIds.map { ctx.map[it]!! }.toTypedArray()
        }
    }

    private fun executeAndWait() {
        Log.v(TAG) {
            "executeAndWait: start parallel decoding: thread = ${Thread.currentThread().id}"
        }
        val maxThreads = (Runtime.getRuntime().availableProcessors() - 1).coerceAtLeast(1)
        val executorService = Executors.newFixedThreadPool(maxThreads)
        idSet.forEach {
            executorService.execute(ConvertOne(it, ctx))
        }

        Log.v(TAG, "convertResourcesToRawAudio: waiting")
        waitForComplete()

        executorService.shutdownNow()
    }

    private fun waitForComplete() {
        ctx.lock.withLock {
            while (ctx.errors.size == 0 && ctx.map.size < idSet.size) {
                ctx.condition.await()
                Log.v(TAG) {
                    "convertResourcesToRawAudio: errors.size = ${
                        ctx.errors.size
                    }, out.size = ${
                        ctx.map.size
                    }, in.size = ${
                        idSet.size
                    }"
                }
            }
        }
    }

    companion object {
        /**
         * Tag for log
         */
        private const val TAG = "ImoMediaLib.ResToRawCvt"
    }
}