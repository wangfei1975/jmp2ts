package com.wterry.jmpegts.com.wterry.jmpegts.parser;

public final class Frame {
    public static final int FLAG_CODEC_CONF = 0x01;
    public static final int FLAG_IFRAME = 0x02;
    public static final int FLAG_PARTIAL = 0x04;
    public static final int FLAG_CORRUPT = 0x08;
    byte [] mBuffer;
    long mPts;
    long mDts;
    int  mData;
    int  mSize;
    int  mFlag;
    Frame(byte [] buffer, int start, int size, long dts, long pts, int flag) {
        mBuffer = buffer;
        mData = start;
        mSize = size;
        mDts = dts;
        mPts = pts;
        mFlag = flag;
    }
}
