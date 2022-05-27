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

import android.media.MediaFormat

/**
 * [android.media.AudioTrack] へ設定可能な生 PCM データを表します。
 */
class RawAudio {
    /** 生 PCM データ */
    val data: ByteArray

    /** 生 PCM データの形式 */
    val format: RawAudioFormat

    /** 生 PCM データのバイト数 */
    val lengthInBytes: Int
        get() = data.size

    /**
     * コンストラクタ
     *
     * @param data 生 PCM データ
     * @param format PCM データの形式を表す [MediaFormat]
     */
    constructor(data: ByteArray, format: MediaFormat) {
        this.data = data
        this.format = RawAudioFormat(format)
    }

    /**
     * コンストラクタ
     *
     * @param data 生 PCM データ
     * @param format PCM データの形式を表す [RawAudioFormat]
     */
    constructor(data: ByteArray, format: RawAudioFormat) {
        this.data = data
        this.format = format
    }
}