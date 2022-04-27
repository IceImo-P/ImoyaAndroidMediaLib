package net.imoya.android.media.audio

/**
 * [AudioSequencer] が連続再生する音声データ 1個を定義するクラス
 *
 * @param delayMilliSeconds 直前の音声を再生終了してから、この音声を再生するまでの間隔(ミリ秒単位, マイナス可)
 */
abstract class AudioSequenceItem(@JvmField var delayMilliSeconds: Int = 0)
