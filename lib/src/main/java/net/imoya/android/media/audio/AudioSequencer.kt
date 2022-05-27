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

package net.imoya.android.media.audio

import android.media.AudioAttributes
import net.imoya.android.media.MediaLog

/**
 * 複数の音声データを結合し、連続再生する機能の abstract です。
 */
abstract class AudioSequencer<T : AudioSequenceItem> {
    /**
     * Internal state
     */
    private enum class State {
        INIT, PREPARED, PLAYING, RELEASED
    }

    /**
     * 音声の用途を表す [AudioAttributes].USAGE_* 値
     */
    @JvmField
    protected var audioUsageField = AudioAttributes.USAGE_MEDIA

    /**
     * 音声の用途を表す [AudioAttributes].USAGE_* 値
     */
    var audioUsage: Int
        get() {
            return audioUsageField
        }
        set(value) {
            audioUsageField = value
        }

    /**
     * 音声の種別を表す [AudioAttributes].CONTENT_TYPE_* 値
     */
    @JvmField
    protected var contentTypeField = AudioAttributes.CONTENT_TYPE_UNKNOWN

    /**
     * 音声の種別を表す [AudioAttributes].CONTENT_TYPE_* 値
     */
    var contentType: Int
        get() {
            return contentTypeField
        }
        set(value) {
            contentTypeField = value
        }

    /**
     * 音声データのリスト
     */
    @JvmField
    protected val sequence: MutableList<T> = ArrayList()

    /**
     * Current state
     */
    private var state = State.INIT

    /**
     * 連続再生する音声データを、リストの末尾へ追加します。
     *
     * @param item 追加する音声データを内包した [AudioSequenceItem]
     * @throws IllegalStateException 既に #prepare() をコール済みです。
     */
    @Suppress("unused")
    fun addSequence(item: T) {
        check(state == State.INIT)
        sequence.add(item)
    }

    /**
     * 音声の再生に使用しているリソースやメモリを解放します。
     *
     * [.play] メソッドをコールし音声を連続再生中に、別スレッドより本メソッドをコールすることができます。
     */
    fun release() {
        if (state != State.RELEASED) {
            cleanupResources()
            state = State.RELEASED
        }
    }

    /**
     * 音声を連続再生可能な状態とする準備を行います。
     *
     * [.play] メソッドをコールする前に、本メソッドをコールする必要があります。
     *
     * @throws IllegalStateException 既に #prepare() をコール済みであるか、再生する音声データが追加されていません。
     * @throws RuntimeException 予期せぬエラーが発生しました。
     */
    fun prepare() {
        check(state == State.INIT)
        check(sequence.size != 0)
        createPlayableSequence()
        state = State.PREPARED
    }

    /**
     * 音声を連続再生します。
     *
     * 連続再生が完了するまで、呼び出し元はブロックされます。
     */
    fun play() {
        check(state == State.PREPARED)
        state = State.PLAYING
        try {
            playOnce()
        } finally {
            state = State.PREPARED
        }
        MediaLog.v(TAG, "play: complete")
    }

    /**
     * 音声を再生可能な状態とする処理
     */
    protected abstract fun createPlayableSequence()

    /**
     * 音声を 1回連続再生する処理
     *
     * 実装の作成者は、再生を完了するまで、呼び出し元のスレッドをブロックするように実装してください。
     */
    protected abstract fun playOnce()

    /**
     * 音声の再生に使用しているリソースやメモリを解放する処理
     */
    protected abstract fun cleanupResources()

    companion object {
        /**
         * Tag for log
         */
        private const val TAG = "ImoMediaLib.AudioSeq"
    }
}
