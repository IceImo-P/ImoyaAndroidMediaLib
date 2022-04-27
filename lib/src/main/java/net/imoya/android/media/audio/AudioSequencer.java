package net.imoya.android.media.audio;

import android.media.AudioAttributes;

import net.imoya.android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * 複数の音声データを結合し、連続再生する機能の abstract です。
 */
public abstract class AudioSequencer<T extends AudioSequenceItem> {
    /**
     * Tag for log
     */
    private static final String TAG = "ImoMediaLib.AudioSeq";

    /**
     * Internal state
     */
    private enum State {
        INIT,
        PREPARED,
        PLAYING,
        RELEASED,
    }

    /**
     * 音声の用途
     */
    protected int audioUsage = AudioAttributes.USAGE_MEDIA;

    /**
     * 音声の種別
     */
    protected int contentType = AudioAttributes.CONTENT_TYPE_UNKNOWN;

    /**
     * 音声データのリスト
     */
    protected final List<T> sequence = new ArrayList<>();

    /**
     * Current state
     */
    private State state = State.INIT;

    /**
     * 音声の用途を返します。
     *
     * @return 音声の用途を表す {@link AudioAttributes}.USAGE_* 値
     */
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
     * 連続再生する音声データを、リストの末尾へ追加します。
     *
     * @param item 追加する音声データを内包した {@link AudioSequenceItem}
     * @throws IllegalStateException 既に #prepare() をコール済みです。
     */
    public void addSequence(T item) {
        if (this.state != State.INIT) {
            throw new IllegalStateException();
        }
        this.sequence.add(item);
    }

    /**
     * 音声の再生に使用しているリソースやメモリを解放します。
     *
     * {@link #play()} メソッドをコールし音声を連続再生中に、別スレッドより本メソッドをコールすることができます。
     */
    public void release() {
        if (this.state != State.RELEASED) {
            this.cleanupResources();
            this.state = State.RELEASED;
        }
    }

    /**
     * 音声を連続再生可能な状態とする準備を行います。
     *
     * {@link #play()} メソッドをコールする前に、本メソッドをコールする必要があります。
     *
     * @throws IllegalStateException 既に #prepare() をコール済みであるか、再生する音声データが追加されていません。
     * @throws RuntimeException 予期せぬエラーが発生しました。
     */
    public void prepare() {
        if (this.state != State.INIT) {
            throw new IllegalStateException();
        }
        if (this.sequence.size() == 0) {
            throw new IllegalStateException();
        }

        this.createPlayableSequence();
        this.state = State.PREPARED;
    }

    /**
     * 音声を連続再生します。
     *
     * 連続再生が完了するまで、呼び出し元はブロックされます。
     */
    public void play() {
        if (this.state != State.PREPARED) {
            throw new IllegalStateException();
        } else {
            this.state = State.PLAYING;
        }

        try {
            this.playOnce();
        } finally {
            this.state = State.PREPARED;
        }
        Log.d(TAG, "play: complete");
    }

    /**
     * 音声を再生可能な状態とする処理
     */
    protected abstract void createPlayableSequence();

    /**
     * 音声を 1回連続再生する処理
     *
     * 実装の作成者は、再生を完了するまで、呼び出し元のスレッドをブロックするように実装してください。
     */
    protected abstract void playOnce();

    /**
     * 音声の再生に使用しているリソースやメモリを解放する処理
     */
    protected abstract void cleanupResources();
}
