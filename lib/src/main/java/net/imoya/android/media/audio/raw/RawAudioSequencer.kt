package net.imoya.android.media.audio.raw;

import android.media.AudioFormat;
import android.media.AudioTrack;

import net.imoya.android.media.audio.AudioSequencer;
import net.imoya.android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.Arrays;

/**
 * 複数の {@link RawAudio} 音声データを結合し、連続再生する機能を提供します。
 */
public class RawAudioSequencer extends AudioSequencer<RawAudioSequenceItem> {
    /**
     * Tag for log
     */
    private static final String TAG = "ImoMediaLib.RawAudioSeq";

    /**
     * オーディオ形式
     */
    private RawAudioFormat trackFormat;

    /**
     * 結合済み音声データ
     */
    private ByteBuffer trackData;

    /**
     * 結合済み音声データを再生する {@link RawAudioPlayer}
     */
    private RawAudioPlayer rawAudioPlayer = null;

    /**
     * 結合済み音声データを再生する {@link RawAudioMediaPlayer}
     */
    private RawAudioMediaPlayer mediaPlayer;

    @Override
    protected void createPlayableSequence() {
        final RawAudioSequenceItem[] items = this.sequence.toArray(new RawAudioSequenceItem[0]);
        final int[] startPosition = new int[items.length];
        final RawAudioFormat format = items[0].rawAudio.getFormat();
        int lastEndPosition = 0;

        // 総サンプル数
        int wholeLength = 0;

        // 各アイテムの再生位置を決定する
        for (int i = 0; i < items.length; i++) {
            final RawAudioSequenceItem item = items[i];

            // 再生開始位置(samples)を算出する
            int startPos = lastEndPosition +
                    (item.delayMilliSeconds * format.getSamplesPerSecond() / 1000);
            if (startPos < 0)
                startPos = 0;
            startPosition[i] = startPos;

            // このアイテムの再生終了位置(samples)を算出する(次のアイテムの再生開始位置算出に使う)
            lastEndPosition = startPos + items[i].rawAudio.getLengthInBytes() / format.getBytesPerSample();

            // AudioTrack全体の長さ(samples)を算出する
            wholeLength = Math.max(wholeLength, lastEndPosition);
        }

        // 再生する音声のデータを構築する。
        final byte[] bytes = new byte[wholeLength * format.getBytesPerSample()];
        Arrays.fill(bytes, 0, bytes.length, (byte) 0);
        final ByteBuffer data = ByteBuffer.wrap(bytes);
        for (int i = 0; i < items.length; i++) {
            writeAudio(data, format, startPosition[i], items[i].rawAudio);
        }

        this.trackFormat = format;
        this.trackData = data;
    }

    /**
     * 結合済みの音声を 1回再生します。
     *
     * @throws IllegalStateException {@link #prepare()} がコールされていないか、音声の再生中です。
     * @throws RuntimeException      予期せぬエラーが発生しました。
     */
    @Override
    protected void playOnce() {
        this.playWithAudioTrack();
        // this.playWithMediaPlayer();
    }

    /**
     * {@link AudioTrack} やメモリを解放します。
     */
    @Override
    protected void cleanupResources() {
        try {
            this.cleanupRawAudioPlayer();
            this.cleanupMediaPlayer();
            this.trackData = null;
        } catch (Throwable tr) {
            Log.w(TAG, "release: ERROR", tr);
        }
    }

    private static void writeAudio(ByteBuffer dest, RawAudioFormat format, int startPosition, RawAudio rawAudio) {
        final int blockAlign = rawAudio.getFormat().getBytesPerSample();
        final int encoding = rawAudio.getFormat().getAudioFormat().getEncoding();
        final int samples = rawAudio.getLengthInBytes() / blockAlign;
        final ByteBuffer source = ByteBuffer.wrap(rawAudio.getData());
        switch (encoding) {
            case AudioFormat.ENCODING_PCM_8BIT:
                mergeBuf8(dest, startPosition, samples, format.getChannels(), source);
                break;
            case AudioFormat.ENCODING_PCM_16BIT:
                mergeBuf16(dest, startPosition, samples, format.getChannels(), source);
                break;
            case AudioFormat.ENCODING_PCM_FLOAT:
                mergeBufFloat(dest, startPosition, samples, format.getChannels(), source);
                break;
            default:
                Log.e(TAG, "writeAudio: Unknown PCM encoding value: " + encoding);
        }
    }

    private static void mergeBuf8(ByteBuffer dest, int startPosition, int samples, int channels, ByteBuffer source) {
        int s;
        int destPosition = startPosition;
        for (int i = 0; i < samples * channels; i++) {
            s = dest.get(destPosition) + source.get(i);
            if (s > 127) {
                s = 127;
            } else if (s < -128) {
                s = -128;
            }
            dest.put(destPosition, (byte) (s + 128));
            destPosition++;
        }
    }

    private static void mergeBuf16(ByteBuffer dest, int startPosition, int samples, int channels, ByteBuffer source) {
        int s;
        int destPosition = startPosition;
        final ShortBuffer sourceBuffer = source.order(ByteOrder.nativeOrder()).asShortBuffer();
        final ShortBuffer destBuffer = dest.order(ByteOrder.nativeOrder()).asShortBuffer();
        for (int i = 0; i < samples * channels; i++) {
            s = destBuffer.get(destPosition) + sourceBuffer.get(i);
            if (s > Short.MAX_VALUE) {
                // Log.i(TAG, "mergeBuf16: clipped (>32767)");
                s = Short.MAX_VALUE;
            } else if (s < Short.MIN_VALUE) {
                // Log.i(TAG, "mergeBuf16: clipped (<-32768)");
                s = Short.MIN_VALUE;
            }
            destBuffer.put(destPosition, (short) s);
            destPosition++;
        }
    }

    private static void mergeBufFloat(ByteBuffer dest, int startPosition, int samples, int channels, ByteBuffer source) {
        float s;
        int destPosition = startPosition;
        final FloatBuffer sourceBuffer = source.order(ByteOrder.nativeOrder()).asFloatBuffer();
        final FloatBuffer destBuffer = dest.order(ByteOrder.nativeOrder()).asFloatBuffer();
        for (int i = 0; i < samples * channels; i++) {
            s = destBuffer.get(destPosition) + sourceBuffer.get(i);
            if (s > 1.0f) {
                // Log.i(TAG, "mergeBufFloat: clipped (> 1.0)");
                s = 1.0f;
            } else if (s < -1.0f) {
                // Log.i(TAG, "mergeBufFloat: clipped (< -1.0)");
                s = -1.0f;
            }
            destBuffer.put(destPosition, s);
            destPosition++;
        }
    }

    private void playWithAudioTrack() {
        this.rawAudioPlayer = new RawAudioPlayer();
        this.rawAudioPlayer.setAudioUsage(this.audioUsage);
        this.rawAudioPlayer.setContentType(this.contentType);
        try {
            this.rawAudioPlayer.play(new RawAudio(this.trackData.array(), this.trackFormat));
        } finally {
            this.rawAudioPlayer = null;
        }
    }

    private synchronized void cleanupRawAudioPlayer() {
        // Log.d(TAG, "cleanupRawAudioPlayer: start");

        try {
            if (this.rawAudioPlayer != null) {
                this.rawAudioPlayer.release();
                this.rawAudioPlayer = null;
            }
        } catch (Throwable tr) {
            Log.w(TAG, "cleanupRawAudioPlayer: ERROR", tr);
        }

        // Log.d(TAG, "cleanupRawAudioPlayer: end");
    }

    private void playWithMediaPlayer() {
        this.mediaPlayer = new RawAudioMediaPlayer();
        this.mediaPlayer.setAudioUsage(this.audioUsage);
        this.mediaPlayer.setContentType(this.contentType);
        try {
            this.mediaPlayer.play(new RawAudio(this.trackData.array(), this.trackFormat));
        } finally {
            this.mediaPlayer = null;
        }
    }

    private synchronized void cleanupMediaPlayer() {
        // Log.d(TAG, "cleanupMediaPlayer: start");

        try {
            if (this.mediaPlayer != null) {
                this.mediaPlayer.release();
                this.mediaPlayer = null;
            }
        } catch (Throwable tr) {
            Log.w(TAG, "cleanupMediaPlayer: ERROR", tr);
        }

        // Log.d(TAG, "cleanupMediaPlayer: end");
    }
}
