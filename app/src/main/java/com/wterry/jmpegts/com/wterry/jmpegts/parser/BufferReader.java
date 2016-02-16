package com.wterry.jmpegts.com.wterry.jmpegts.parser;

public final class BufferReader extends BufferWrapper {
    public BufferReader(byte[] data, int offset, int size) {
        super(data, offset, size);
    }
    int readU8() {
        return ((int)mData[mPos++])&0xFF;
    }
    int readU16() {
        int t = ((mData[mPos]&0xFF) << 8) | (mData[mPos + 1]&0xFF);
        mPos += 2;
        return t;
    }
    int readU24() {
        int t = ((mData[mPos]&0xFF) << 16) | ((mData[mPos + 1]&0xFF) << 8) | (mData[mPos + 2]&0xFF);
        mPos += 3;
        return t;
    }
    long readU32() {
        long t = ((mData[mPos]&0xFFL) << 24) | ((mData[mPos + 1]&0xFF) << 16) | ((mData[mPos + 2]&0xFF)<<8) | (mData[mPos + 3]&0xFF);
        mPos += 4;
        return t;
    }
    int read(byte [] outbuf, int pos, int size) {
        int rdsize = size < remains() ? size : remains();
        System.arraycopy(mData, mPos, outbuf, pos, rdsize);
        mPos += rdsize;
        return rdsize;
    }
    int skip(int size) {
        int realskip = size < remains() ? size : remains();
        mPos += realskip;
        return realskip;
    }
}
