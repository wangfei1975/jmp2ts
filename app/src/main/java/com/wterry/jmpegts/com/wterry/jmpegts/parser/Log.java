package com.wterry.jmpegts.com.wterry.jmpegts.parser;



public final class Log {
    public static final int VERBOSE = 2;
    public static final int DEBUG = 3;
    public static final int INFO = 4;
    public static final int WARNING = 5;
    public static final int ERROR = 6;
    public static final int LEVEL = 2;

    public static void log(int level, String tag, String msg) {
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
            default:
                android.util.Log.v(tag , msg);
                break;
        }
    }

    public static void d(String tag, String msg) {
        log(DEBUG, tag, msg);
    }
    public static void i(String tag, String msg) {
        log(INFO, tag, msg);
    }
    public static void e(String tag, String msg) {
        log(ERROR, tag, msg);
    }
    public static void w(String tag, String msg) {
        log(WARNING, tag, msg);
    }
    public static void v(String tag, String msg) {
        log(VERBOSE, tag, msg);
    }
}
