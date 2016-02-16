package com.wterry.jmpegts.com.wterry.jmpegts.parser;

public final class TimestampQueue {

    static final int DEFAULT_PTS_QUEUE_SIZE = 10;
    long[] mPts;
    long[] mOffset;
    int mHead;
    int mTail;
    int mSize;
    TimestampQueue() {
        this(DEFAULT_PTS_QUEUE_SIZE);
    }
    TimestampQueue(int size) {
        mSize = size;
        mPts = new long[size];
        mOffset = new long[size];
        mHead = mTail = 0;
    }

    boolean enqueue(long pts, long offset) {
        if ((mTail + 1) % mSize == mHead) {
            return false;
        }
        mPts[mTail] = pts;
        mOffset[mTail] = offset;
        mTail = (mTail + 1) % mSize;
        return true;
    }

    long dequeue(long offset) {
        long ret = -1;
        while (mHead != mTail && mOffset[mHead] < offset) {
            if (mPts[mHead] > ret) {
                ret = mPts[mHead];
            }
            mHead = (mHead + 1) % mSize;
        }
        return ret;
    }

    void reset() {
        mHead = mTail = 0;
    }
}
