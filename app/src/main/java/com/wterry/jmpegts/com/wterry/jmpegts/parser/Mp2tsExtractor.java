package com.wterry.jmpegts.com.wterry.jmpegts.parser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * Created by Fei Wang on 2/18/2016.
 */
public class Mp2tsExtractor {
    final static String TAG = Thread.currentThread().getStackTrace()[1].getClass().getSimpleName();

    final static int FMT_DECT_STEP_SIZE = 188 * 7 * 10;
    final static int FMT_DETECT_SIZE =  FMT_DECT_STEP_SIZE * 10;
    final static int MAX_FMT_DETECT_SIZE = FMT_DETECT_SIZE * 10;
    Parser mParser;

    final void setDataSource(final String path) throws IOException {
        FileInputStream is = new FileInputStream(new File(path));
        mParser = ParserImpl.createParser();
        byte [] fmtBuffer = new byte[FMT_DETECT_SIZE];
        int fmt = 0;
        int checkSize = 0;
        while (fmt < 4 && checkSize < MAX_FMT_DETECT_SIZE) {
            if (fmtBuffer.length < checkSize + FMT_DECT_STEP_SIZE) {
                byte [] nb = new byte [fmtBuffer.length + FMT_DETECT_SIZE];
                System.arraycopy(fmtBuffer, 0, nb, 0, checkSize);
                fmtBuffer = nb;
            }
            int rdSize = is.read(fmtBuffer, checkSize, FMT_DECT_STEP_SIZE);
            if (rdSize <= 0) {
                Log.d(TAG, "detecting reach end of file.");
                break;
            }
            fmt = mParser.detectFormat(fmtBuffer, 0, checkSize + rdSize);
            checkSize += rdSize;
            Log.d(TAG, "format detecting size " + checkSize + " detect result = " + fmt);
        }

        if (fmt < 4) {
            throw  new IOException("Un recogrnized format, not a MPEG2 TS file");
        }

        is.close();
    }
}
