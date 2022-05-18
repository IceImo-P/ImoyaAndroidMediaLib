package net.imoya.android.media

import java.lang.RuntimeException

class MediaFormatException : RuntimeException {
    @Suppress("unused")
    constructor()
    constructor(message: String?) : super(message)
    @Suppress("unused")
    constructor(cause: Throwable?) : super(cause)
    @Suppress("unused")
    constructor(cause: Throwable?, message: String?) : super(message, cause)
}