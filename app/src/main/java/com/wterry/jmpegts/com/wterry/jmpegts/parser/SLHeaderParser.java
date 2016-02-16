package com.wterry.jmpegts.com.wterry.jmpegts.parser;

import com.wterry.jmpegts.com.wterry.jmpegts.parser.Mp4Descriptors.SLConfigDescriptor;

public final class SLHeaderParser {

    static final String TAG = SLHeaderParser.class.getSimpleName();
    boolean mAccessUnitStartFlag;
    boolean mAccessUnitEndFlag;
    boolean mOCRflag;
    boolean mIdleFlag;
    boolean mPaddingFlag;
    int mPaddingBits;
    int mPacketSequenceNumber;
    boolean mDegPrioflag;
    int mDegradationPriority;
    long mObjectClockReference = -1;
    boolean mRandomAccessPointFlag;
    long mAU_sequenceNumber;
    long mDecodingTimeStamp = -1;
    long mCompositionTimeStamp = -1;
    int mAccessUnitLength;
    long mInstantBitrate;

    long getDts() {
        return mDecodingTimeStamp;
    }

    long getPts() {
        return mCompositionTimeStamp;
    }

    int parse(final BufferReader inBuf, final SLConfigDescriptor sl) {
        BitBufferReader br = new BitBufferReader(inBuf.data(), inBuf.tell() * 8, inBuf.remains() * 8);
        if (sl.mUseAccessUnitStartFlag) {
            mAccessUnitStartFlag = br.read() != 0;
        }
        if (sl.mUseAccessUnitEndFlag) {
            mAccessUnitEndFlag = br.read() != 0;
        }
        if (!sl.mUseAccessUnitStartFlag && !sl.mUseAccessUnitEndFlag) {
            mAccessUnitStartFlag = mAccessUnitEndFlag = true;
        }

        if (sl.mOCRLength > 0) {
            mOCRflag = br.read() != 0;
        }
        if (sl.mUseIdleFlag) {
            mIdleFlag = br.read() != 0;
        }
        if (sl.mUsePaddingFlag) {
            if ((mPaddingFlag = (br.read()) != 0)) {
                mPaddingBits = br.read(3);
            }
        }

        if (!mIdleFlag && (!mPaddingFlag || mPaddingBits != 0)) {
            if (sl.mPacketSeqNumLength > 0) {
                mPacketSequenceNumber = br.read(sl.mPacketSeqNumLength);
            }
            if (sl.mDegradationPriorityLength > 0) {
                if ((mDegPrioflag = (br.read() != 0))) {
                    mDegradationPriority = br.read(sl.mDegradationPriorityLength);
                }
            }
            if (mOCRflag) {
                mObjectClockReference = br.readLong(sl.mOCRLength);
            }
            if (mAccessUnitStartFlag) {
                if (sl.mUseRandomAccessPointFlag) {
                    mRandomAccessPointFlag = (br.read() != 0);
                }
                if (sl.mAU_seqNumLength > 0) {
                    mAU_sequenceNumber = br.readLong(sl.mAU_seqNumLength);
                }
                boolean dtsFlag = false, ptsFlag = false;
                if (sl.mUseTimeStampsFlag) {
                    dtsFlag = br.read() != 0;
                    ptsFlag = br.read() != 0;
                }
                boolean insBitrateFlag = false;
                if (sl.mInstantBitrateLength > 0) {
                    insBitrateFlag = br.read() != 0;
                }
                if (dtsFlag) {
                    mDecodingTimeStamp = br.readLong(sl.mTimeStampLength);
                    if (sl.mTimeStampResDen != 0) {
                        mDecodingTimeStamp *= sl.mTimeStampResNum;
                        mDecodingTimeStamp /= sl.mTimeStampResDen;
                    }
                    Log.d(TAG, String.format("mDecodingTimeStamp = %d", mDecodingTimeStamp));
                }
                if (ptsFlag) {
                    mCompositionTimeStamp = br.readLong(sl.mTimeStampLength);
                    if (sl.mTimeStampResDen != 0) {
                        mCompositionTimeStamp *= sl.mTimeStampResNum;
                        mCompositionTimeStamp /= sl.mTimeStampResDen;
                    }
                    Log.d(TAG, String.format("mCompositionTimeStamp = %d", mCompositionTimeStamp));
                }
                if (sl.mAU_Length > 0) {
                    mAccessUnitLength = br.read(sl.mAU_Length);
                }
                if (insBitrateFlag) {
                    mInstantBitrate = br.readLong(sl.mInstantBitrateLength);
                }
            }
        }
        Log.d(TAG, String.format("SL packet header size in bits = %d", br.tell()));
        inBuf.skip(((br.tell() - br.pointer()) + 7) >> 3);
        return 0;
    }

}
