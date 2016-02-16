package com.wterry.jmpegts.com.wterry.jmpegts.parser;

public class BufferWrapper {
    protected byte [] mData;
    protected int mBaseOffset;
    protected int mSize;
    protected int mPos;
 
    public BufferWrapper(byte [] data, int offset, int size) {
        mBaseOffset = offset;
        mData = data;
        mPos = offset;
        mSize = size;
    }
    public byte [] data() {
        return mData;
    }
    public int size()   {
        return mSize;
    }
    public int tell() {
        return mPos;
    }
    void seek(int pos) {
        mPos = pos;
    }
    int remains() {
        return mBaseOffset + mSize - mPos;
    }
    
    int pointer() {
        return mBaseOffset;
    }
}
