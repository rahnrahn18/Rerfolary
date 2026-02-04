package com.kashif.folar.utils

object NativeBridge {
    init {
        try {
            System.loadLibrary("folar-native")
        } catch (e: UnsatisfiedLinkError) {
            e.printStackTrace()
        }
    }

    /**
     * Stabilizes the video at inputPath and saves it to outputPath.
     * This is a blocking call and should be run on a background thread.
     */
    external fun stabilizeVideo(inputPath: String, outputPath: String)
}
