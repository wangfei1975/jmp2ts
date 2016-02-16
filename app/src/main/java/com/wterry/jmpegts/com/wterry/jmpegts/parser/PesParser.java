package com.wterry.jmpegts.com.wterry.jmpegts.parser;

import com.wterry.jmpegts.com.wterry.jmpegts.parser.Mp4Descriptors.SLConfigDescriptor;

public final class PesParser {

    static final String TAG = PesParser.class.getSimpleName();
    static final int MAX_PES_SIZE = (1920 * 1088 + ParserImpl.TS_PACKET_SIZE);
    
    byte[] mHeader = new byte[64];
    int mMarker;
    int mHeaderSize;
    int mPesSize;
    int mRealPesSize;
    int mPtsDtsIndicator;
    long mPts;
    long mDts;
    boolean mSawPusi;

    static long parseTimeStamp(final BufferReader inBuf) {
        long pts = (inBuf.readU8() & 0x0EL) << 29;
        pts |= ((((long) inBuf.readU16()) >> 1) << 15);
        pts |= ((long) inBuf.readU16()) >> 1;
        return (pts * 1000L + 45L) / 90L;
    }

    static boolean hasOptionalPesHeader(int code) {
        /*
         *  program_stream_map, private_stream_2
         *  ECM, EMM,
         *  program_stream_directory, DSMCC_stream
         */
        return code != 0xbc && code != 0xbf &&
               code != 0xf0 && code != 0xf1 &&         
               code != 0xff && code != 0xf2 &&
               code != 0xf8;
    }

    // int parseSLHeader(BufferReader & inBuf, const SLConfigDescriptor * sl);

    long getDts() {
        return mDts;
    }

    long getPts() {
        return mPts;
    }

    int parse(final HeaderParser tsHeader, BufferReader inBuf, BufferWriter outBuf, final SLConfigDescriptor sl) {
        int startPos = inBuf.tell();
        mPts = mDts = -1;
        if (tsHeader.isPesStart()) {
            //LOGI("Got PES packet******* size (%d)", mRealPesSize);
            //parse PES header and get PTS/DTS
            inBuf.read(mHeader, 0, 4);
            if (mHeader[0] != 0 || mHeader[1] != 0 || mHeader[2] != 1) {
                Log.e(TAG, String.format("Error PES start code expect 0x000001 see : 0x%02x%02x%02x", mHeader[0], mHeader[1], mHeader[2]));
                return -1;
            }
            mSawPusi = true;
            mPesSize = inBuf.readU16();
            if (hasOptionalPesHeader(mHeader[3])) {
                mMarker = inBuf.readU8();
                mPtsDtsIndicator = inBuf.readU8();
                mHeaderSize = inBuf.readU8() + 9;

                int hdRemains = mHeaderSize - 9;
                if ((mPtsDtsIndicator & 0x80) != 0) {
                    mPts = mDts = parseTimeStamp(inBuf);
                    hdRemains -= 5;
                }
                if ((mPtsDtsIndicator & 0x40) != 0) {
                    mDts = parseTimeStamp(inBuf);
                    hdRemains -= 5;
                }
                inBuf.skip(hdRemains);
            }
            if (sl != null){
                /*
                SLHeaderParser slHeader;
                slHeader.parse(inBuf, sl);
                if (slHeader.getDts() >= 0) {
                    mDts = slHeader.getDts();
                }
                if (slHeader.getPts() >= 0) {
                    mPts = slHeader.getPts();
                }
                */
            }
            mRealPesSize = 0;
        }
        mRealPesSize += inBuf.remains();
        if (outBuf != null) {
            outBuf.append(inBuf);
        }
        return inBuf.tell() - startPos;
    }

}
