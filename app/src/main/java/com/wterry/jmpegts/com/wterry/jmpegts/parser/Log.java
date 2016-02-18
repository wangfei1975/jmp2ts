package com.wterry.jmpegts.com.wterry.jmpegts.parser;
public final class Log {
    public static final int VERBOSE = 2;
    public static final int DEBUG = 3;
    public static final int INFO = 4;
    public static final int WARNING = 5;
    public static final int ERROR = 6;
    public static final int LEVEL = 2;

    static void logBuf(int level, final String tag,  final byte [] buffer, int pos, int length) {
        if (level < LEVEL) {
            return;
        }
        StringBuilder sb = new StringBuilder(3*16);
        for (int i = 0; (i + pos) < buffer.length && i < length; i++) {
            sb.append(String.format("%02X ", 0xFF & ((int)buffer[i+pos])));
            if ((i+1)%16 == 0) {
                log(level, tag,  sb.toString());
                sb.setLength(0);
            }
        }
        if (sb.length() > 0) {
            log(level, tag, sb.toString());
        }
    }

    public static void log(int level, final String tag, final String msg) {
        if (level < LEVEL) {
            return;
        }
        switch(level) {
            case ERROR:
                android.util.Log.e(tag, msg);
                break;
            case WARNING:
                android.util.Log.w(tag, msg);
                break;
            case INFO:
                android.util.Log.i(tag, msg);
                break;
            case DEBUG:
                android.util.Log.d(tag, msg);
                break;
            default:
                android.util.Log.v(tag , msg);
                break;
        }
    }

    public static void d(final String tag, final String msg) {
        log(DEBUG, tag, msg);
    }
    public static void i(final String tag, final String msg) {
        log(INFO, tag, msg);
    }
    public static void e(final String tag, final String msg) {
        log(ERROR, tag, msg);
    }
    public static void w(final String tag, final String msg) {
        log(WARNING, tag, msg);
    }
    public static void v(final String tag, final String msg) {
        log(VERBOSE, tag, msg);
    }
}
