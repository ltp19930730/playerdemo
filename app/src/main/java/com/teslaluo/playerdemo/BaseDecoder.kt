package com.teslaluo.playerdemo

import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import java.io.File
import java.nio.ByteBuffer

/*
Initialize the codec: Prepare the MediaCodec instance for encoding or decoding, specifying the desired format and mode (encoder or decoder).

Start the codec: Call start() to transition the codec to the executing state, which prepares its input and output buffers.

Dequeue input buffers: Use dequeueInputBuffer(long timeoutUs) to get the index of an available input buffer. If a buffer is available, fill it with the raw data to be encoded or the encoded data to be decoded.

Queue the filled buffer: Once the input buffer is filled with data, use queueInputBuffer to send it back to the codec for processing.

Process output buffers: Concurrently, use methods like dequeueOutputBuffer to retrieve processed data from the codec's output buffers.

Release resources: After processing is complete, release the codec resources by calling stop() and release().

 */
abstract class BaseDecoder(private val mFilePath: String): IDecoder {

    companion object {
        private const val TAG = "BaseDecoder"

    }

    private var mIsRunning = true

    private val mLock = Object()

    private var mReadyForDecode = false

    protected var mCodec: MediaCodec? = null

    protected var mExtractor: IExtractor? = null


    private var mBufferInfo = MediaCodec.BufferInfo()

    private var mState = DecodeState.STOP

    protected var mStateListener: IDecoderStateListener? = null

    private var mIsEOS = false

    protected var mVideoWidth = 0

    protected var mVideoHeight = 0

    private var mDuration: Long = 0

    private var mStartPos: Long = 0

    private var mEndPos: Long = 0

    private var mStartTimeForSync = -1L

    private var mSyncRender = true

    final override fun run() {
        if (mState == DecodeState.STOP) {
            mState = DecodeState.START
        }
        mStateListener?.decoderPrepare(this)
        if (!init()) return
        try {
            while (mIsRunning) {

                if (mState != DecodeState.START &&
                    mState != DecodeState.DECODING &&
                    mState != DecodeState.SEEKING) {

                    waitDecode()
                    mStartTimeForSync = System.currentTimeMillis() - getCurTimeStamp()
                }

                if (!mIsRunning || mState == DecodeState.STOP) {
                    mIsRunning = false
                    break
                }

                if (mStartTimeForSync == -1L) {
                    mStartTimeForSync = System.currentTimeMillis()
                }

                if (!mIsEOS) {
                    mIsEOS = pushBufferToDecoder()
                }

                val index = pullBufferFromDecoder()

                if (index >= 0) {

                    val outputBuffer = mCodec!!.getOutputBuffer(index)

                    if (mSyncRender && mState == DecodeState.DECODING) {
                        sleepRender()
                    }

                    //【解码步骤：4. 渲染】
                    if (mSyncRender) {// 如果只是用于编码合成新视频，无需渲染
                        render(outputBuffer!!, mBufferInfo)
                    }

                    val frame = Frame()
                    frame.buffer = outputBuffer!!
                    frame.setFrameBufferInfo(mBufferInfo)
                    mStateListener?.decodeOneFrame(this, frame)

                    if (mBufferInfo.flags == MediaCodec.BUFFER_FLAG_END_OF_STREAM) {
                        mState = DecodeState.FINISH
                        mStateListener?.decoderFinish(this)
                    }
                    mCodec!!.releaseOutputBuffer(index, true);
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            doneDecode()
            release()
        }
    }

    private fun init(): Boolean {
        if (mFilePath.isEmpty() || !File(mFilePath).exists()) {
            mStateListener?.decoderError(this, "filepath empty")
            Log.d(TAG, "filepath empty")
            return false
        }
        if (!check()) return false
        mExtractor = initExtractor(mFilePath)
        if (mExtractor?.getFormat() == null) {
            return false
        }


        if (!initParams()) return false
        if (!initRender()) return false
        if (!initCodec()) return false

        return true
    }

    private fun initParams(): Boolean {
        try {
            val format = mExtractor!!.getFormat()!!
            mDuration = format.getLong(MediaFormat.KEY_DURATION) / 1000
            if (mEndPos == 0L) mEndPos = mDuration

            initSpecParams(mExtractor!!.getFormat()!!)
        } catch (e: Exception) {
            return false
        }
        return true
    }



    private fun initCodec(): Boolean {
        try {
            val type = mExtractor?.getFormat()?.getString(MediaFormat.KEY_MIME)
            mCodec = MediaCodec.createDecoderByType(type!!)
            if (!configCodec(mCodec!!, mExtractor!!.getFormat()!!)) {
                waitDecode()
            }
            mCodec!!.start()
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }

    private fun waitDecode() {
        try {
            if (mState == DecodeState.PAUSE) {
                mStateListener?.decoderPause(this)
            }
            synchronized(mLock) {
                mLock.wait()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    protected fun notifyDecode() {
        synchronized(mLock) {
            mLock.notifyAll()
        }
        if (mState == DecodeState.DECODING) {
            mStateListener?.decoderRunning(this)
        }
    }

    override fun pause() {
        mState = DecodeState.DECODING
    }

    override fun goOn() {
        mState = DecodeState.DECODING
        notifyDecode()
    }

    /*
     *  Dequeue input buffers: Use dequeueInputBuffer(long timeoutUs)
     *  to get the index of an available input buffer. If a buffer is available,
     *  fill it with the raw data to be encoded or the encoded data to be decoded.
     */
    private fun pushBufferToDecoder(): Boolean {
        var inputBufferIndex = mCodec!!.dequeueInputBuffer(1000);
        var isEndOfStream = false
        if (inputBufferIndex >= 0) {
            val inputBuffer = mCodec!!.getInputBuffer(inputBufferIndex)
            var sampleSize = mExtractor!!.readBuffer(inputBuffer!!)

            if (sampleSize < 0) {
                mCodec!!.queueInputBuffer(inputBufferIndex, 0, 0,
                    0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                isEndOfStream = true
            } else {
                mCodec!!.queueInputBuffer(inputBufferIndex, 0, sampleSize, mExtractor!!.getCurrentTimestamp(), 0)
            }

        }
        return isEndOfStream
    }

    private fun pullBufferFromDecoder(): Int {
        try {
            val index = mCodec!!.dequeueOutputBuffer(mBufferInfo, -1)
            when (index) {
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {}
                MediaCodec.INFO_TRY_AGAIN_LATER -> {}

                else -> {
                    return index
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return -1
    }

    private fun sleepRender() {
        val passTime = System.currentTimeMillis() - mStartTimeForSync
        val curTime = getCurTimeStamp()
        if (curTime > passTime) {
            Thread.sleep(curTime - passTime)
        }
    }

    private fun release() {
        try {
            mState = DecodeState.STOP
            mIsEOS = false
            mExtractor?.stop()
            mCodec?.stop()
            mCodec?.release()
            mStateListener?.decoderDestroy(this)
        } catch (e: Exception) {

        }
    }

    override fun stop() {
        mState = DecodeState.STOP
        mIsRunning = false
        notifyDecode()
    }

    override fun isDecoding(): Boolean {
        return mState == DecodeState.DECODING
    }

    override fun isSeeking(): Boolean {
        return mState == DecodeState.SEEKING
    }

    override fun isStop(): Boolean {
        return mState == DecodeState.STOP
    }

    override fun setStateListener(l: IDecoderStateListener?) {
        mStateListener = l
    }

    override fun getWidth(): Int {
        return mVideoWidth
    }

    override fun getHeight(): Int {
        return mVideoHeight
    }

    override fun getDuration(): Long {
        return mDuration
    }

    override fun getRotationAngle(): Int {
        return 0
    }

    override fun getCurTimeStamp(): Long {
        return mBufferInfo.presentationTimeUs / 1000
    }

    override fun getMediaFormat(): MediaFormat? {
        return mExtractor?.getFormat()
    }

    override fun getTrack(): Int {
        return 0
    }

    override fun getFilePath(): String {
        return mFilePath
    }


    abstract fun check(): Boolean

    abstract fun initExtractor(path: String): IExtractor

    abstract fun initSpecParams(format: MediaFormat)

    abstract fun initRender(): Boolean

    abstract fun configCodec(codec: MediaCodec, format: MediaFormat): Boolean

    abstract fun render(outputBuffer: ByteBuffer,
                        bufferInfo: MediaCodec.BufferInfo)

    abstract fun doneDecode()
}