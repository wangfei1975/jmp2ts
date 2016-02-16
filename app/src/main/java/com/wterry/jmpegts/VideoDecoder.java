package com.wterry.jmpegts;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import android.view.Surface;

import com.wterry.jmpegts.com.wterry.jmpegts.parser.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by feiwang on 15-11-18.
 */
public final class VideoDecoder {

    static final String TAG = VideoDecoder.class.getSimpleName();
    static public final int QOMX_COLOR_FormatYUV420PackedSemiPlanar64x32Tile2m8ka = 0x7FA30C03;//
    String mVideoFile;
    MediaCodec mDecoder;
    MediaFormat mInputFormat;
    MediaFormat mOutputFormat;

    MediaExtractor mExtractor;
    int mVideoRotation;
    long mDuration;
    int mVideoWidth;
    int mVideoHeight;

    ByteBuffer[] mInputBuffers;
    ByteBuffer[] mOutputBuffers;

    Surface mSurface;

    public VideoDecoder(String videoFile, Surface surface) throws IOException {
        mVideoFile = videoFile;
        mSurface = surface;
        init();
        prepare();
    }



     private void prepare() throws IOException {
         Log.d(TAG, "prepare ...");
         int idx = -1, counter = 0;
         MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
         while(mOutputFormat == null && counter++ < 15000) {
             idx = dequeueOutputBuffer(100, info);
             if (idx >= 0) {
                 mDecoder.releaseOutputBuffer(idx, false);
             }
             if (idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                 break;
             }
         }
         if (mOutputFormat == null) {
             throw new IOException("Could not detect video format");
         }
        // Log.d(TAG, "prepare seek and flush");
        //mExtractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
        //mDecoder.flush();
        Log.d(TAG, "prepare done");
    }
    private ByteBuffer getInputBuffer(int idx) {
        if (Build.VERSION.SDK_INT <= 20) {
            if (mInputBuffers != null && idx >= 0 && idx < mInputBuffers.length) {
                return mInputBuffers[idx];
            }
            return null;
        } else {
            return mDecoder.getInputBuffer(idx);
        }
    }


    public void seek(long pos) {
        mExtractor.seekTo(pos, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
        mDecoder.flush();
    }
    public ByteBuffer getOutputBuffer(int idx) {
        if (Build.VERSION.SDK_INT <= 20) {
            if (mOutputBuffers != null && idx >= 0 && idx < mOutputBuffers.length) {
                return mOutputBuffers[idx];
            }
            return null;
        } else {
            if (idx >= 0) {
                try {
                    return mDecoder.getOutputBuffer(idx);
                } catch (IllegalStateException e) {
                    return null;
                }
            }
            return null;
        }
    }
    private void fillInputBuffers() {
        int inIndex = mDecoder.dequeueInputBuffer(0);
        while(inIndex >= 0) {
            ByteBuffer buffer = getInputBuffer(inIndex);
            int sampleSize = mExtractor.readSampleData(buffer, 0);
            long sampleTime = mExtractor.getSampleTime();
            if (sampleSize < 0) {
                Log.d(TAG, "InputBuffer BUFFER_FLAG_END_OF_STREAM");
                mDecoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                break;
            } else {
                Log.v(TAG, "Fill input buffer: " + inIndex);
                mDecoder.queueInputBuffer(inIndex, 0, sampleSize, sampleTime, 0);
                mExtractor.advance();
            }
            inIndex = mDecoder.dequeueInputBuffer(0);
        }
    }


     boolean mEos;
    // static final String KEY_BUFFER_SIZE = "buffer-size";

    int mOutputColorFormat;

    public int dequeueOutputBuffer(int timeout, MediaCodec.BufferInfo info) {
        if (mEos) {
            info.flags =  MediaCodec.BUFFER_FLAG_END_OF_STREAM;
            return MediaCodec.INFO_TRY_AGAIN_LATER;
        }
        int outIndex = mDecoder.dequeueOutputBuffer(info, 0);
        if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER && !mEos) {
            fillInputBuffers();
            outIndex = mDecoder.dequeueOutputBuffer(info, timeout);
        }

        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            Log.i(TAG, "See end of stream in decoding");
            mEos = true;
        }
        switch (outIndex) {
            case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                //{height=816, what=1869968451, color-format=2130706688, slice-height=816, crop-left=32, width=1408, crop-bottom=743, crop-top=24, mime=video/raw, stride=1408, crop-right=1311}
                //QCOM output QOMX_COLOR_FormatYUV420PackedSemiPlanar64x32Tile2m8ka
               // mFormatDected = true;
                mOutputFormat = mDecoder.getOutputFormat();
                Log.i(TAG, "Output format:" + mOutputFormat); //ITU get this
                mOutputColorFormat = mOutputFormat.getInteger(MediaFormat.KEY_COLOR_FORMAT);
                if (mOutputColorFormat != MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar) {
                    Log.w(TAG, "output fomrat not YUV420SemiPlanar, need converting");
                }
                break;
            case MediaCodec.INFO_TRY_AGAIN_LATER:
                //  Log.d("DecodeActivity", "dequeueOutputBuffer timed out!");
                break;
            default:
                if (Build.VERSION.SDK_INT <= 20 && outIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    mOutputBuffers = mDecoder.getOutputBuffers();
                }
                Log.v(TAG, "got output buffer index " + outIndex);
                break;
        }

        return outIndex;
    }

    public static int getFormatInteger(final MediaFormat fmt, final String name, final int defaultValue) {
        try {
            return fmt.getInteger(name);
        } catch (NullPointerException e) { /* no such field */ } catch (ClassCastException e) { /* field of different type */ }
        return defaultValue;
    }


    YUV420PackedSemiPlanar64x32Tile2m8kaToNV12 mOutputConverter;
    byte [] mConverterBuffer;
    public void setupOutputConverter(int encoderInputFormat) {
        mOutputConverter = null;
        if (mOutputColorFormat == encoderInputFormat) {
            return;
        }

        final MediaFormat fmt = mOutputFormat;
        int w = fmt.getInteger(MediaFormat.KEY_WIDTH);
        int h = fmt.getInteger(MediaFormat.KEY_HEIGHT);
        Log.i(TAG, "need convert: video MediaFormat = " + fmt);
        int sliceHeight = getFormatInteger(fmt, "slice-height", h);
        if (sliceHeight <= 0) {
            sliceHeight = h;
        }
        h = sliceHeight;
        int stride = getFormatInteger(fmt, "stride", w);
        if (stride < w) {
            stride = w;
        }
        w = stride;
        if (mOutputColorFormat == QOMX_COLOR_FormatYUV420PackedSemiPlanar64x32Tile2m8ka &&
            encoderInputFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar) {
            mOutputConverter = new YUV420PackedSemiPlanar64x32Tile2m8kaToNV12(w, h);
            Log.i(TAG, "Setup convert YUV420PackedSemiPlanar64x32Tile2m8kaToNV12 with (" + w + "x" + h+")");
        }

    }

    public final boolean needConvertOutput() {
        return mOutputConverter != null;
    }

    public final void convertOutput(ByteBuffer buf, MediaCodec.BufferInfo info,  byte [] output) {
        if (mConverterBuffer == null || mConverterBuffer.length < info.size) {
            mConverterBuffer = new byte[info.size];
        }
        buf.get(mConverterBuffer, 0, info.size);
        mOutputConverter.convert(mConverterBuffer, output);
        buf.rewind();
    }
    public void init() throws IOException {
        Log.d(TAG, "init ...");
            mExtractor = new MediaExtractor();
            mExtractor.setDataSource(mVideoFile);
            mVideoRotation = new VideoRotationDetector().detect(mVideoFile);
        Log.d(TAG, "init detect rotation");
            for (int i = 0; i < mExtractor.getTrackCount(); i++) {
                MediaFormat format = mExtractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime.startsWith("video/")) {
                    mExtractor.selectTrack(i);
                    mDecoder = MediaCodec.createDecoderByType(mime);
                    //   mDecoder.setCallback(null);
                    Log.i(TAG, "Video format: " + format);
                    mInputFormat = format;
                    //TODO enumlate decoder output format and select prefered
                    if (mSurface == null) {
                        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar);
                    }
                    mDecoder.configure(format, mSurface, null, 0);
                    mVideoWidth = format.getInteger(MediaFormat.KEY_WIDTH);
                    mVideoHeight = format.getInteger(MediaFormat.KEY_HEIGHT);
                    mDuration = format.getLong(MediaFormat.KEY_DURATION);
                    Log.d(TAG, "init found video track");
                    break;
                }
            }
            if (mDecoder == null) {
                throw new IOException("No video track found in the input " + mVideoFile);
            }
            mDecoder.start();
            if (Build.VERSION.SDK_INT <= 20) {
                mInputBuffers = mDecoder.getInputBuffers();
                mOutputBuffers = mDecoder.getOutputBuffers();
            }
        Log.d(TAG, "init done");
        }

    public long getDuration() {
        return mDuration;
    }
    public MediaFormat getOutputFormat() {
        return mOutputFormat;
    }
    public int getRotation() {
        return mVideoRotation;
    }
    public void releaseOutputBuffer(int idx, boolean render) {
        if (idx >= 0) {
            mDecoder.releaseOutputBuffer(idx, render);
        }
    }
    public void close() {
        mDecoder.stop();
        mDecoder.release();
    }
}
