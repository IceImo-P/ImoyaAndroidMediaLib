/*
 * Copyright (C) 2022 IceImo-P
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.imoya.android.media.audio.raw

import android.content.Context
import android.media.MediaFormat
import net.imoya.android.media.MediaLog
import net.imoya.android.media.audio.AudioDecoder

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
    @Suppress("MemberVisibilityCanBePrivate")
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
//            MediaLog.v(TAG) { "callback.onEnd[$id]: start" }
            oneLock.withLock {
//                MediaLog.v(TAG) { "callback.onEnd[$id]: entering lock" }
                ctx.map[id] = RawAudio(data, format)
//                MediaLog.v(TAG) { "callback.onEnd[$id]: signalAll" }
                oneCondition.signalAll()
            }
//            MediaLog.v(TAG) { "callback.onEnd[$id]: end" }
        }

        override fun onError(decoder: AudioDecoder, e: java.lang.Exception) {
//            MediaLog.v(TAG) { "callback.onError[$id]: start: $e" }
            oneLock.withLock {
//                MediaLog.v(TAG) { "callback.onError[$id]: entering lock" }
                ctx.errors.add(e)
//                MediaLog.v(TAG) { "callback.onError[$id]: signalAll" }
                oneCondition.signalAll()
            }
//            MediaLog.v(TAG) { "callback.onError[$id]: end" }
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
//            MediaLog.v(TAG) {
//                "convertOne: decoding $id, thread = ${Thread.currentThread().id}"
//            }

            localCondition = localLock.newCondition()

            val decoder = AudioDecoder()
            decoder.setSource(ctx.context, id)
            decoder.convert(DecoderCallbackImpl(id, ctx, localLock, localCondition))

            // Wait for AudioDecoder
            localLock.withLock {
                try {
                    localCondition.await()
                } catch (e: InterruptedException) {
                    ctx.errors.add(e)
                }
            }

            // Notify to controller
            ctx.lock.withLock {
                ctx.condition.signalAll()
            }

//            MediaLog.v(TAG) { "convertOne: end. thread = ${Thread.currentThread().id}" }
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
                MediaLog.w(TAG) { "convert: error[$it]: ${ctx.errors[it]}" }
            }
            throw ctx.errors[0]
        } else {
            MediaLog.v(TAG, "convert: success")
            resultCache = resourceIds.map { ctx.map[it]!! }.toTypedArray()
        }
    }

    private fun executeAndWait() {
//        MediaLog.v(TAG) {
//            "executeAndWait: start parallel decoding: thread = ${Thread.currentThread().id}"
//        }
        val maxThreads = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
        val executorService = Executors.newFixedThreadPool(maxThreads)
        idSet.forEach {
            executorService.execute(ConvertOne(it, ctx))
        }

//        MediaLog.v(TAG, "executeAndWait: waiting")
        waitForComplete()

        executorService.shutdownNow()
    }

    private fun waitForComplete() {
        ctx.lock.withLock {
            while (ctx.errors.size == 0 && ctx.map.size < idSet.size) {
                ctx.condition.await()
//                MediaLog.v(TAG) {
//                    "waitForComplete: errors.size = ${
//                        ctx.errors.size
//                    }, out.size = ${
//                        ctx.map.size
//                    }, in.size = ${
//                        idSet.size
//                    }"
//                }
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