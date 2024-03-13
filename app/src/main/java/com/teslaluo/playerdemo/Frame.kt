package com.teslaluo.playerdemo

import android.media.MediaCodec
import java.nio.ByteBuffer

class Frame {
    var buffer: ByteBuffer? = null

    var bufferInfo = MediaCodec.BufferInfo()

    fun setFrameBufferInfo(info: MediaCodec.BufferInfo) {
        bufferInfo.set(info.offset, info.size, info.presentationTimeUs, info.flags)
    }

}