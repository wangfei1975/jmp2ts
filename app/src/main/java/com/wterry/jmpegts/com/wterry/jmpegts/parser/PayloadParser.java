package com.wterry.jmpegts.com.wterry.jmpegts.parser;

abstract class PayloadParser {

    static final int PAT_PID  = 0x0000;
    static final int SDT_PID  = 0x0011;
    ParserImpl mTsParser;
    int mPid;

    PayloadParser(ParserImpl tp, int pid) {
        mTsParser = tp;
        mPid = pid;
    }

    int getPid() {
        return mPid;
    }
    abstract int parse(final HeaderParser tsHeader, BufferReader inBuf, boolean forceParse);
}
