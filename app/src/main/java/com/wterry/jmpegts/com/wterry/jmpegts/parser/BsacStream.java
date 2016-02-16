package com.wterry.jmpegts.com.wterry.jmpegts.parser;


public final class BsacStream extends StreamImpl {

    BsacStream(ParserImpl tp, int pid, int streamType) {
        super(tp, pid, streamType);
        // TODO Auto-generated constructor stub
    }

    @Override
    int locateFrame(byte[] data, int offset, int size, Frame frm) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    void parseMediaFormat(final Frame frm) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public String toString() {
        return "BsacStream";
    }
}
