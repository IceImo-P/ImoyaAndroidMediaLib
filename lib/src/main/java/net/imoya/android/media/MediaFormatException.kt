package net.imoya.android.media;

public class MediaFormatException extends RuntimeException {
    public MediaFormatException() {
    }

    public MediaFormatException(String message) {
        super(message);
    }

    public MediaFormatException(Throwable cause) {
        super(cause);
    }

    public MediaFormatException(Throwable cause, String message) {
        super(message, cause);
    }
}
