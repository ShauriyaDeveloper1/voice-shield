package com.sagar.voice_shield.ml

/**
 * JNI Interface for whisper.cpp.
 * This class provides the architecture to hook up native C++ STT transcription.
 */
object WhisperLib {
    
    // We attempt to load the native library if compiled.
    var isInitialized = false
        private set

    init {
        try {
            System.loadLibrary("whisper")
            isInitialized = true
        } catch (e: UnsatisfiedLinkError) {
            // Native library not found. Falling back to stub implementation.
            isInitialized = false
        }
    }

    /**
     * Initialize the whisper context with the given model path.
     * Returns a pointer to the context, or 0 if failed.
     */
    external fun initContext(modelPath: String): Long

    /**
     * Free the whisper context.
     */
    external fun freeContext(contextPtr: Long)

    /**
     * Run full transcription on the provided audio samples.
     * Audio samples should be 16kHz f32 mono.
     * Returns the transcribed text string.
     */
    external fun fullTranscribe(contextPtr: Long, audioData: FloatArray): String

    // --- Fallback Stubs ---

    fun initContextStub(modelPath: String): Long {
        return if (isInitialized) initContext(modelPath) else 1L // Dummy pointer
    }

    fun freeContextStub(contextPtr: Long) {
        if (isInitialized) freeContext(contextPtr)
    }

    fun fullTranscribeStub(contextPtr: Long, audioData: FloatArray): String {
        return if (isInitialized) {
            fullTranscribe(contextPtr, audioData)
        } else {
            // Return a mock transcript for testing the backend's XLM-Roberta context engine
            "Hello, please transfer the funds to my account."
        }
    }
}
