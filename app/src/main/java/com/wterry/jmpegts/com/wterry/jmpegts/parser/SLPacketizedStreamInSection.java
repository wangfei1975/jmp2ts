package com.wterry.jmpegts.com.wterry.jmpegts.parser;

import com.wterry.jmpegts.com.wterry.jmpegts.parser.Mp4Descriptors.ESDescriptor;
import com.wterry.jmpegts.com.wterry.jmpegts.parser.Mp4Descriptors.Mp4Descriptor;

public final class SLPacketizedStreamInSection extends PayloadParser {
    static final String TAG = SLPacketizedStreamInSection.class.getSimpleName();
    int   mNumOfParsedSections;
    final SectionParser mSection = new SectionParser();
    final int  mEsID;
    final ProgramImpl mPmt;
    
    SLPacketizedStreamInSection(ParserImpl tsParser, int pid, int esid, ProgramImpl pmt) {
        super(tsParser, pid);
        mEsID = esid;
        mPmt = pmt;
    }
    @Override
    int parse(HeaderParser tsHeader, BufferReader inBuf, boolean forceParse) {
        if (mNumOfParsedSections > 0) {
            return 0;
        }

        if (mSection.parse(tsHeader, inBuf) < 0) {
            Log.e(TAG, "parse section failed.");
            return -1;
        }

        if (!mSection.complete()) {
            Log.i(TAG, String.format("section not complete sectionLength = %d, pos = %d, continue", mSection.getLength(),
                    mSection.getPosition()));
            return 0;
        }

        if (mSection.getTableId() != 0x05) {
            return 0;
        }
        //not include crc
        BufferReader br = new BufferReader(mSection.getBuffer(), 0, mSection.getLength()-4);
        while (br.remains() > 2) {
            Mp4Descriptor  des = Mp4Descriptors.createDescriptor(0, br);
            if (des != null) {
                ESDescriptor  esd = (ESDescriptor) des.findChild(ESDescriptor.TAG);
                if (esd != null) {
                    des.log(Log.DEBUG, 2);
                    esd.detachFromParent();
                    mPmt.populatePendingStream(esd);
                }
                Log.d(TAG, String.format("des->tag = 0x%x size = %d, remains = %d", des.tag(), des.size(), br.remains()));
            }
        }
        mNumOfParsedSections++;
        return 0;
    }
     
}
