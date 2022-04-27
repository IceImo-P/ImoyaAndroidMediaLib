package net.imoya.android.media.audio.raw

import net.imoya.android.media.audio.AudioSequenceItem

/**
 * [RawAudioSequencer] が連続再生する音声データ 1個を定義するクラス
 */
class RawAudioSequenceItem(delayMilliSeconds: Int, @JvmField val rawAudio: RawAudio) : AudioSequenceItem(delayMilliSeconds)
