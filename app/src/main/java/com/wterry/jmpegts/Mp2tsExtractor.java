package com.wterry.jmpegts;

import android.media.MediaFormat;

import com.wterry.jmpegts.com.wterry.jmpegts.parser.Frame;
import com.wterry.jmpegts.com.wterry.jmpegts.parser.Log;
import com.wterry.jmpegts.com.wterry.jmpegts.parser.Parser;
import com.wterry.jmpegts.com.wterry.jmpegts.parser.ParserImpl;
import com.wterry.jmpegts.com.wterry.jmpegts.parser.Program;
import com.wterry.jmpegts.com.wterry.jmpegts.parser.Stream;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Created by Fei Wang on 2/18/2016.
 */
public final class Mp2tsExtractor {
    final static String TAG = Mp2tsExtractor.class.getSimpleName();

    final static int FMT_DECT_STEP_SIZE = 188 * 7 * 10;
    final static int FMT_DETECT_SIZE =  FMT_DECT_STEP_SIZE * 10;
    final static int MAX_FMT_DETECT_SIZE = FMT_DETECT_SIZE * 10;
    Parser mParser;
    String mDataSource;

    Program [] mProgroms;
    ArrayList<Stream> mStreams;
    final int	getTrackCount() {
        return mStreams.size();
    }
    public MediaFormat getTrackFormat (int index) {
        if (index >= 0 && index < mStreams.size()) {
            return mStreams.get(index).getFormat().toAndroidMediaFormat();
        }
        return null;
    }
    FileInputStream mInputStream;
    public final void release () {
        if (mInputStream != null) {
            try {
                mInputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    Stream mCurrentTrack;
    boolean mSawCodecConf;
    Frame mCurrentFrame;

    byte [] mReadBuffer;

    final int	readSampleData(ByteBuffer byteBuf, int offset) {
        if (mCurrentFrame == null || mCurrentFrame.getSize() <= 0) {
            return -1;
        }
        int size =  mCurrentFrame.getSize();
        if (size + offset > byteBuf.capacity()) {
            throw new InvalidParameterException("byteBuffer not big enough");
        }
        byteBuf.position(offset);
        byteBuf.put(mCurrentFrame.getBuffer(), mCurrentFrame.getOffset(), size);
        return size;
    }
    final public boolean advance () {
        if (mInputStream == null) {
            throw new IllegalStateException("No data source");
        }
        if (mCurrentTrack == null) {
            throw new IllegalStateException("No current Track");
        }
        if (mReadBuffer == null) {
            mReadBuffer = new byte[mParser.getPacketSize()];
        }

        if (mCurrentFrame != null) {
            mCurrentFrame.reset();
        }

        try {
            while (mCurrentFrame == null || mCurrentFrame.getSize() <= 0) {
                int rsize = mInputStream.read(mReadBuffer);
                if (rsize <= 0 || rsize != mReadBuffer.length) {
                    return false;
                }
                mParser.parse(mReadBuffer, 0, rsize);
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }
    long mPtsBase;
    public long getSampleTime() {
        if ( mCurrentFrame == null || mCurrentFrame.getSize() <= 0) {
            return -1;
        }
        return mCurrentFrame.getPts() - mPtsBase;
    }
    public void selectTrack (int index) {
        if (mCurrentTrack == null && index >= 0 && index < mStreams.size()) {
            mCurrentTrack = mStreams.get(index);
            mSawCodecConf = false;
            mPtsBase = 0;
            mCurrentFrame = null;
            mCurrentTrack.setListener(new Stream.Listener() {
                @Override
                public void onFrame(Frame frame) {
                    if (!mSawCodecConf) {
                        mSawCodecConf = (frame.getFlag() == (Frame.FLAG_CODEC_CONF|Frame.FLAG_IFRAME));
                        if (mSawCodecConf) {
                            mPtsBase = frame.getPts();
                        }
                    }
                    if (!mSawCodecConf) {
                        return;
                    }
                    Log.i(TAG, "got frame size " + frame.getSize());
                    if (mCurrentFrame == null || mCurrentFrame.getBuffer().length < frame.getSize()) {
                        mCurrentFrame = new Frame(new byte[mCurrentTrack.getMaxFrameSize() * 3], -1, 0, 0, 0, 0);
                    }
                    System.arraycopy(frame.getBuffer(), frame.getOffset(), mCurrentFrame.getBuffer(), 0,  frame.getSize());
                    mCurrentFrame.set(0, frame.getSize(), frame.getPts(), frame.getFlag());
                }
            });
            advance();
        }
    }
    public void seekTo (long timeUs, int mode) {
        //NOT supported yet
    }
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
        is.close();
        if (fmt < 4) {
            throw  new IOException("Un recogrnized format, not a MPEG2 TS file");
        }
        mProgroms = mParser.getPrograms();
        mStreams = new ArrayList<Stream>();
        for (int i = 0; i < mProgroms.length; i++) {
            mStreams.addAll(Arrays.asList(mProgroms[i].getStreams()));
        }
        mParser.flush();
        mDataSource = path;

        mInputStream = new FileInputStream(new File(path));
    }
}
