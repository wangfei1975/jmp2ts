package com.wterry.jmpegts.com.wterry.jmpegts.parser;

public final class HeaderParser {
    static final String TAG = HeaderParser.class.getSimpleName();
    static final int SYNC_BYTE = 0x47;
    
    int mPid;
    int mTei; // transport_error_indicator
    int mPusi; // PayloadUnitStartIndicator;
    int mScramblingControl; // 00, 01, 10, 11
    int mAdaptionExist; // 01 10 11
    int mCountinuityCounter;
    int mAdaptionLength;

    boolean hasError() {
        return (mTei != 0);
    }

    boolean isPesStart() {
        return mPusi != 0;
    }

    boolean hasPayload() {
        return (mAdaptionExist & 0x01) != 0;
    }

    int getPid() {
        return mPid;
    }

    int parse(BufferReader inBuf) {
        int data;
        if ((data = inBuf.readU8()) != SYNC_BYTE) {
            throw new ParseErrorException("Error first byte is not sync byte : " + data);
        }
        mPid  = inBuf.readU16();
        mTei  = (mPid>>15)&0x01;
        mPusi = (mPid>>14)&0x01;
        mPid &= 0x1FFF;

        data = inBuf.readU8();

        mScramblingControl = (data >> 6) & 0x03;
        mAdaptionExist = (data >> 4) & 0x03;
        mCountinuityCounter = (data & 0x0F);

        mAdaptionLength = 0;
        //has adaption field
        if ((mAdaptionExist & 0x02) != 0) {
            mAdaptionLength = inBuf.readU8();
            inBuf.skip(mAdaptionLength);
        }

        return inBuf.tell();
    }
}
