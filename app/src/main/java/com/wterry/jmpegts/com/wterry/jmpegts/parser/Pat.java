package com.wterry.jmpegts.com.wterry.jmpegts.parser;

import java.util.ArrayList;
import java.util.List;

public final class Pat extends PayloadParser {
    static final String TAG = Pat.class.getSimpleName();
    int mNumOfParsedPats;
    SectionParser mSection = new SectionParser();
    List<ProgramImpl> mProgs = new ArrayList<ProgramImpl>();

    Pat(ParserImpl tp) {
        super(tp, PAT_PID);
    }

    @Override
    int parse(HeaderParser tsHeader, BufferReader inBuf, boolean forceParse) {
        if (mNumOfParsedPats > 0) {
            // LOGI("Already has Parsed PAT, do not support PAT changes now, ignored");
            return 0;
        }
        if (mSection.parse(tsHeader, inBuf) < 0) {
            return -1;
        }

        if (!mSection.complete()) {
            Log.i(TAG, String.format("section not complete sectionLength = %d, pos = %d, continue",
                    mSection.getLength(), mSection.getPosition()));
            return 0;
        }
        Log.i(TAG, "******************* PAT *************************");
        if (mSection.getTableId() != 0x0) {
            Log.e(TAG, "error, invalid PAT table id, expect 0x0 saw: " + mSection.getTableId());
            return -1;
        }

        Log.i(TAG, "mSectionLength =" + mSection.getLength());

        Log.i(TAG, String.format("PAT: table_id(0x%x) trans stream id(0x%x) section_length(%d)", mSection.getTableId(),
                mSection.getTableIdExt(), mSection.getLength()));

        // N = (section_lenght - 5(transport_stream_id tolast_section_number) - 4(CRC))/4
        int numProgs = (mSection.getLength() - 4) / 4;
        Log.i(TAG, "numProgs = " + numProgs);

        BufferReader br = new BufferReader(mSection.getBuffer(), 0, mSection.getLength());
        for (int i = 0; i < numProgs; i++) {
            int prgNum = br.readU16();
            int prgPid = br.readU16() & 0x1FFF;
            Log.i(TAG, String.format("Program num(%d) pid(0x%x)", prgNum, prgPid));
            if (prgNum == 0) {
                Log.i(TAG, "Network PID %d, TBD..." + prgPid);
            }
            ProgramImpl prg = new ProgramImpl(mTsParser, prgPid, prgNum);
            mProgs.add(prg);
            mTsParser.setPayloadParser(prgPid, prg);
        }
        mNumOfParsedPats++;
        return br.tell();
    }

    boolean detected() {
        return mNumOfParsedPats > 0;
    }

    ProgramImpl[] getPrograms() {
        return mProgs.toArray(new ProgramImpl[mProgs.size()]);
    }

    int getNumOfPrograms() {
        return mProgs.size();
    }

    void flush() {
        for (ProgramImpl pg : mProgs) {
            pg.flush();
        }
    }
}
