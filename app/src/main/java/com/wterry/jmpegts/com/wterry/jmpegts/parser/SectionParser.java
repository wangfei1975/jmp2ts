package com.wterry.jmpegts.com.wterry.jmpegts.parser;

public final class SectionParser {
    static final String TAG = SectionParser.class.getSimpleName();
    static final int MAX_SECTION_SIZE = 1024;

    int mTableId;
    int mSectionLength;
    int mTableIdExt;
    int mVersionNumber;
    int mSectionNumber;
    int mLastSectionNumber;

    byte[] mSectionBuffer;
    int mBufferPos;

    boolean complete() {
        return mSectionBuffer != null && mSectionLength > 0 && mBufferPos >= mSectionLength;
    }

    byte[] getBuffer() {
        return mSectionBuffer;
    }

    int getPosition() {
        return mBufferPos;
    }

    int getTableId() {
        return mTableId;
    }

    int getTableIdExt() {
        return mTableIdExt;
    }

    int getLength() {
        return mSectionLength;
    }

    SectionParser() {
    }

    int parse(final HeaderParser tsHeader, final BufferReader inBuf) {
        if (tsHeader.isPesStart()) {
            int len = inBuf.readU8();
            if (len > 0) {
                Log.w(TAG, "non zero section payload: " + len);
                if (mSectionBuffer == null || mBufferPos + len > mSectionBuffer.length) {
                    Log.w(TAG, "Unexpect section payload, discard");
                } else if (inBuf.read(mSectionBuffer, mBufferPos, len) != len) {
                    Log.e(TAG, String.format("reading section payload %d bytes failed.", len));
                    return -1;
                }
                return 0;
            }
            mTableId = inBuf.readU8();
            mSectionLength = inBuf.readU16() & 0x0FFF;
            if (mSectionLength < 5) {
                Log.e(TAG, "Invalid mSectionLength =" + mSectionLength);
                return -1;
            }
            Log.d(TAG, String.format("pid = 0x%x mSectionLength = %d", tsHeader.getPid(), mSectionLength));
            mSectionLength -= 5;
            mTableIdExt = inBuf.readU16();
            mVersionNumber = inBuf.readU8();
            mSectionNumber = inBuf.readU8();
            mLastSectionNumber = inBuf.readU8();
            mSectionBuffer = new byte[mSectionLength];
            mBufferPos = Math.min(inBuf.remains(), mSectionLength);
            inBuf.read(mSectionBuffer, 0, mBufferPos);
        } else {
            int rd = Math.min(mSectionBuffer.length - mBufferPos, inBuf.remains());
            inBuf.read(mSectionBuffer, mBufferPos, rd);
            mBufferPos += rd;
        }
        return 0;
    }
}
