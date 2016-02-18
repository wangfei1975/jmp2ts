package com.wterry.jmpegts.com.wterry.jmpegts.parser;

/**
 * Created by feiwang on 15-03-23.
 */
public interface Parser {
    public int detectFormat(final byte[] buffer, int pos, int size);
    public Program [] getPrograms();
    public int parse(final byte[] buffer, int pos, int size);
    
    public void flush();

    public int getPacketSize();
    //public void reset();
}
