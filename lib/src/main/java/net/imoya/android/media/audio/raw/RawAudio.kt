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