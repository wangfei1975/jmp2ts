package com.wterry.jmpegts.com.wterry.jmpegts.parser;


public abstract class AudioStream extends StreamImpl {

    AudioStream(ParserImpl tp, int pid, int streamType) {
        super(tp, pid, streamType);
        mStaticPesBufferSize = 1024 * 32;
    }

    static int findFrameStart(final byte [] data, int start, final int end) {
        while (end - start > 4) {
            if (((data[start]&0XFF) == 0xFF) && ((data[start+1] & 0xF0) == 0xF0)) {
                return start;
            }
            start++;
        }
        return -1;
    }
    abstract int parseFrame(final byte [] data, int start, int end, MediaFormat fmt);


    @Override
    int locateFrame(final byte[] data, int start, int size, Frame frm) {
        frm.mData = -1;
        frm.mFlag = 0;
        frm.mSize = 0;
        int end = start + size;

        int fsize = 0;
        while (end - start > 4) {
            if ((start = findFrameStart(data, start, end)) < 0) {
                return -1;
            }
            if ((fsize = parseFrame(data, start, end, null)) > 0) {
                break;
            }
            start++;
        }

        frm.mData = start;
        frm.mFlag = Frame.FLAG_CODEC_CONF | Frame.FLAG_IFRAME;
        if (fsize > 0 && end - start >= fsize) {
            frm.mSize = fsize;
            return 1;
        }
        return 0;
        }
 
    @Override
    void parseMediaFormat(Frame frm) {
        final byte [] data = frm.mBuffer;
        int startp = frm.mData;
        final int endp = startp + frm.mSize;
        
        //info->mName = getCodecName();
        //info->mFourcc = getFourcc();
 
        MediaFormat fmt = new MediaFormat();
        while (endp - startp > 6) {
            if ((startp = findFrameStart(data, startp, endp)) < 0) {
                return;
            }
            if (parseFrame(data, startp, endp, fmt) > 0) {
                break;
            }
            startp++;
        }
        mMediaFormat = fmt;
    }

}
