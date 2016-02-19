package com.wterry.jmpegts.com.wterry.jmpegts.parser;

import com.wterry.jmpegts.com.wterry.jmpegts.parser.ParserImpl;
import com.wterry.jmpegts.com.wterry.jmpegts.parser.StreamImpl;

abstract public class VideoStream extends StreamImpl {

    static final int  VIDEO_DEFAULT_FPS  =   24;
    static final int  VIDEO_DEFAULT_FPSACC  = 49;

    VideoStream(ParserImpl tp, int pid, int streamType) {
        super(tp, pid, streamType);
    }

    static int findStartCode(final byte[] data, int start, int end)  {
        end -= 3;
        while(start < end) {
            if (data[start+2] > 2) {
                start += 3;
            } else if (data[start+1] != 0) {
                start += 2;
            } else if (data[start] != 0 || data[start+2] == 0) {
                start ++;
            } else {
                return start;
            }
        }
        return -1;
    }

}
