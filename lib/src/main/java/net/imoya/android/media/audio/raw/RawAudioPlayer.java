package net.imoya.android.media.audio.raw;

import android.media.AudioAttributes;
import android.media.AudioTrack;

import androidx.annotation.NonNull;

import net.imoya.android.media.audio.AudioTrackUtility;
import net.imoya.android.util.Log;

import java.nio.ByteBuffer;

/**
 * {@link AudioTrack} を使用して、1個の {@link RawAudio} を再生します。
 */
public class RawAudioPlayer {
    /**
     * Tag for log
     */
    private static final String TAG = "ImoMediaLib.RawAudioPlyr";

    /**
     * 音声の用途
     */
    private int audioUsage = AudioAttributes.USAGE_MEDIA;

    /**
     * 音声の種別
     */
    private int contentType = AudioAttributes.CONTENT_TYPE_UNKNOWN;

    /**
     * 音声を再生する {@link AudioTrack}
     */
    private AudioTrack track;

    /**
     * 音声の用途を返します。
     *
     * @return 音声の用途を表す {@link AudioAttributes}.USAGE_* 値
     */
    @SuppressWarnings("unused")
    public int getAudioUsage() {
        return this.audioUsage;
    }

    /**
     * 音声の用途を設定します。
     *
     * @param audioUsage 音声の用途を表す {@link AudioAttributes}.USAGE_* 値
     */
    public void setAudioUsage(int audioUsage) {
        this.audioUsage = audioUsage;
    }

    /**
     * 音声の種別を返します。
     *
     * @return 音声の種別を表す {@link AudioAttributes}.CONTENT_TYPE_* 値
     */
    @SuppressWarnings("unused")
    public int getContentType() {
        return this.contentType;
    }

    /**
     * 音声の種別を設定します。
     *
     * @param contentType 音声の種別を表す {@link AudioAttributes}.CONTENT_TYPE_* 値
     */
    public void setContentType(int contentType) {
        this.contentType = contentType;
    }

    /**
     * 音声の再生に使用しているリソースを解放します。
     */
    public synchronized void release() {
        this.cleanupAudioTrack();
    }

    /**
     * 1個の {@link RawAudio} を再生します。
     * <p>
     * 再生を完了するまで、メソッドを呼び出したスレッドはブロックされます。
     *
     * @param audio 再生する音声
     */
    public void play(@NonNull RawAudio audio) {
        final RawAudioFormat format = audio.getFormat();
        final ByteBuffer data = ByteBuffer.wrap(audio.getData());
        final int bytesPerSample = format.getBytesPerSample();

        Log.d(TAG, "play: format = " + format);

        // AudioTrack を構築する
        int bufferSize = AudioTrack.getMinBufferSize(
                format.getSamplesPerSecond(),
                AudioTrackUtility.getChannelConfig(format.getChannels()),
                AudioTrackUtility.getAudioEncodingForBytesPerSample(format.getBytesPerSampleAtChannel()));

        // Log.d(TAG, "play: bufferSize = " + bufferSize);

        if ((bufferSize % bytesPerSample) != 0) {
            bufferSize += bytesPerSample - (bufferSize % bytesPerSample);
        }

        // Log.d(TAG, "play: bufferSize = " + bufferSize);

        this.track = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(this.audioUsage)
                        .setContentType(this.contentType)
                        .build()
                )
                .setAudioFormat(format.getAudioFormat())
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build();

        // 最初のデータをバッファに書き込む。
        Log.d(TAG, "play: Writing data to AudioTrack");
        int writtenFirst = this.track.write(data, data.array().length, AudioTrack.WRITE_NON_BLOCKING);
        Log.d(TAG, "play: writtenFirst = " + writtenFirst);

        Log.d(TAG, "play: Starting AudioTrack");

        try {
            // 再生開始
            this.track.play();

            if (writtenFirst < data.array().length) {
                int written = writtenFirst;
                boolean endSuccessfully = true;
                do {
                    if (this.track.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
                        Log.d(TAG, "play: AudioTrack maybe stopped");
                        break;
                    }
                    Log.d(TAG, "play: Writing data to AudioTrack... (" + written + " - )");

                    final int w = this.track.write(data, data.array().length - written, AudioTrack.WRITE_BLOCKING);
                    Log.d(TAG, "play: Written " + w + " bytes");
                    if (w <= 0) {
                        endSuccessfully = false;
                        break;
                    } else {
                        written += w;
                    }
                }
                while (written < data.array().length);

                Log.d(TAG, "play: complete to write");

                // エラー終了していなければ、バッファの中身が再生されるまで待つ
                if (endSuccessfully) {
                    this.waitForBufferLength(format, bufferSize);
                }
            } else {
                // バッファの中身が再生されるまで待つ
                this.waitForBufferLength(format, bufferSize);
            }
        } catch (Exception e) {
            Log.v(TAG, "play: Exception at play", e);
        } finally {
            // AudioTrack の後片付け
            this.cleanupAudioTrack();
        }
    }

    private synchronized void waitForBufferLength(RawAudioFormat format, int bufferSize) {
        // Log.d(TAG, "waitForBufferLength: start");

        try {
            long millis = (bufferSize / format.getBytesPerSample()) * 1000L / format.getSamplesPerSecond() + 1;

            // Log.d(TAG, "waitForBufferLength: bufSize = " + bufferSize + ", time = " + millis);

            this.wait(millis);
        } catch (InterruptedException ex) {
            Log.v(TAG, ex);
        }

        // Log.d(TAG, "waitForBufferLength: end");
    }

    /**
     * {@link AudioTrack} を安全に停止し解放します。
     */
    private void cleanupAudioTrack() {
        // Log.d(TAG, "cleanupAudioTrack: start");

        try {
            if (this.track != null) {
                if (this.track.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                    try {
                        this.track.stop();
                    } catch (IllegalStateException ex) {
                        Log.i(TAG, "cleanupAudioTrack: ERROR on stop AudioTrack", ex);
                    }
                }
                this.track.release();
                this.track = null;
            }
        } catch (Throwable tr) {
            Log.w(TAG, "cleanupAudioTrack: ERROR", tr);
        }

        // Log.d(TAG, "cleanupAudioTrack: end");
    }


}
