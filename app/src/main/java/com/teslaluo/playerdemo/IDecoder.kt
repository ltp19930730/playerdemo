package com.teslaluo.playerdemo

import android.media.MediaFormat

interface IDecoder: Runnable {
    fun pause()

    fun goOn()

    fun stop()

    fun isDecoding(): Boolean

    fun isSeeking(): Boolean

    fun isStop(): Boolean

    fun setStateListener(l : IDecoderStateListener?)

    fun getWidth(): Int

    fun getHeight(): Int

    fun getDuration(): Long

    fun getCurTimeStamp(): Long

    fun getRotationAngle(): Int

    fun getMediaFormat(): MediaFormat?

    fun getTrack(): Int

    fun getFilePath(): String
}