package com.wterry.jmpegts.com.wterry.jmpegts.parser;

public final class AACStream extends AudioStream {
    static final String TAG = AACStream.class.getSimpleName();

    AACStream(ParserImpl tp, int pid, int streamType) {
        super(tp, pid, streamType);
    }
    static final int [] mSamplingRate = new int[] { 96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000, 12000, 11025,
        8000, 7350};
 
    @Override
    int parseFrame(byte[] data, int start, int end, MediaFormat fmt) {
        if (end - start < 6) {
            return -1;
        }
        BitBufferReader bitBuf = new BitBufferReader(data, start*8+12, (end - start) * 8);
        int version =  bitBuf.read();
        int layer = bitBuf.read(2);
        if (layer != 0) {
            Log.w(TAG, String.format("layer %d not zero", layer));
            return -1;
        }
        //int err_protection = bitBuf.read();
        bitBuf.read();
        int profile = bitBuf.read(2) + 1;
        Log.i(TAG, "AAC profile = " + profile);
        if (profile > 2) {
            Log.w(TAG, "unsupported profile " + profile);
            return -1;
        }
        int srateidx = bitBuf.read(4);
        if (srateidx == 0x0F) {
            return -1;
        }

        int sampleRate = mSamplingRate[srateidx];

        bitBuf.read();
        int channels = bitBuf.read(3);

        bitBuf.skip(4);

        int fsize = bitBuf.read(13);

        if (fmt != null) {
            bitBuf.skip(11);
            int rdb = bitBuf.read(2);
            int bitRate = (int)(((8 * fsize * sampleRate) / ((rdb + 1) * 1024) + 0.5));
            fmt.setInteger(MediaFormat.KEY_AAC_PROFILE, profile);
            fmt.setInteger(MediaFormat.KEY_SAMPLE_RATE, sampleRate);
            fmt.setInteger(MediaFormat.KEY_CHANNEL_COUNT, channels);
            fmt.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
            fmt.setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_AUDIO_AAC);
             if (version != 0) {
                fmt.setInteger(MediaFormat.KEY_FOURCC, Mp4Descriptors.fourcc("M2AC"));
                fmt.setString(MediaFormat.KEY_DESCRIPTION, "MPEG-2 AAC ADTS Audio");
            } else {
                fmt.setInteger(MediaFormat.KEY_FOURCC, Mp4Descriptors.fourcc("M4AC"));
                fmt.setString(MediaFormat.KEY_DESCRIPTION, "MPEG-4 AAC ADTS Audio");
            }
        }
        return fsize;
    }
 
}
