package com.wterry.jmpegts.com.wterry.jmpegts.parser;


/**
 * Created by feiwang on 15-03-23.
 */
public interface Stream {
    public interface Listener {
        public void onFrame(Frame frame);
    }
    MediaFormat getFormat();
    void setListener(Listener lis);
    
}
