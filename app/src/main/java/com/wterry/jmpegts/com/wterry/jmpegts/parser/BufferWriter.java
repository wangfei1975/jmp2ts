package com.wterry.jmpegts.com.wterry.jmpegts.parser;

public final class BufferWriter extends BufferWrapper {

    public BufferWriter(byte[] data, int offset, int size) {
        super(data, offset, size);
    }
    void reset(byte [] buf, int size) {
        mData = buf;
        mSize = size;
        mBaseOffset = 0;
        mPos = 0;
    }

    int write(final byte [] data, int offset, int size) {
        //TODO check
        int towrite = mBaseOffset + mSize - mPos < size ? mBaseOffset + mSize - mPos : size;
        System.arraycopy(data,  offset,  mData,  mPos,  towrite);
        mPos += towrite;
        return towrite;
    }

    int append(BufferReader in) {
        int towrite =  mBaseOffset + mSize - mPos < in.remains() ? mBaseOffset + mSize - mPos: in.remains();
        int inoff = in.tell();
        return write(in.data(), inoff, towrite);
    }
}
