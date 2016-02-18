package com.wterry.jmpegts.com.wterry.jmpegts.parser;

import com.wterry.jmpegts.com.wterry.jmpegts.parser.Mp4Descriptors.DecConfigDescriptor;
import com.wterry.jmpegts.com.wterry.jmpegts.parser.Mp4Descriptors.ESDescriptor;
import com.wterry.jmpegts.com.wterry.jmpegts.parser.Mp4Descriptors.SLConfigDescriptor;

abstract class StreamImpl extends PayloadParser implements Stream {
    
    static final String TAG = StreamImpl.class.getSimpleName();
    
    

    int mStreamType;
    ESDescriptor mEsDescr;
    DecConfigDescriptor mDecConfigDescr;
    SLConfigDescriptor  mSLConfigDescr;
    Listener mListener;
    boolean mSawPesStart;
    boolean mSizeInHeader;

    PesParser    mPesParser = new PesParser();
    BufferWriter mPesBuffer = new BufferWriter(null, 0, 0);

    byte [] mStaticPesBuffer;
    int mStaticPesBufferSize = PesParser.MAX_PES_SIZE;
    boolean mLastFrmIsPartial;
    long mParsedFrames;
    long mParsedBytes;
    
    int mMaxFrameSize;
    TimestampQueue mPtsQue = new TimestampQueue();
    MediaFormat mMediaFormat;
    
    static StreamImpl createStream(ParserImpl tsParser, int pid, int streamType) {
        Log.i(TAG, String.format("creating stream 0x%x for pid 0x%x", streamType, pid));
        switch(streamType) {
        case 0x02:
          //  return new Mp2vStream(tsParser, pid, streamType, info, infoSize);
            break;
        case 0x10:
          //  return new Mp4vStream(tsParser, pid, streamType, info, infoSize);
            break;
        case 0x1B:
            return new H264Stream(tsParser, pid, streamType);
        case 0x03: case 0x04:
         //   return new Mxa3Stream(tsParser, pid, streamType, info, infoSize);
            break;
        case 0x0F:
             return new AACStream(tsParser, pid, streamType);
        case 0x83:
         //   return new LpcmStream(tsParser, pid, streamType, info, infoSize);
        default:
           break;
        }
        Log.w(TAG, String.format("unknown stream type 0x(%02X)", streamType));
        return null;
    }
    StreamImpl(ParserImpl tp, int pid, int streamType) {
        super(tp, pid);
        mStreamType = streamType;
    }
    void setESDescriptor(ESDescriptor des) {
        mEsDescr = des;
        if (des != null) {
            mDecConfigDescr = (DecConfigDescriptor)des.findChild(DecConfigDescriptor.TAG);
            mSLConfigDescr = (SLConfigDescriptor)des.findChild(SLConfigDescriptor.TAG);
        }
    }
 
    // return -1 not found anything
    // return 0 found frame start, need more data
    // return 1, found frame start and end
    abstract int locateFrame(final byte [] data, int start, int size, Frame frm);
    abstract void parseMediaFormat(final Frame frm);
    
    
    @Override
    public void setListener(Listener lis) {
        mListener = lis;
    }

    @Override
    public MediaFormat getFormat() {
        return mMediaFormat;
    }

    private void updateOutputBuffer(byte [] lastData, int pos, int size) {
        if (mStaticPesBuffer == null) {
            mStaticPesBuffer = new byte[mStaticPesBufferSize];
        }
        mPesBuffer.reset(mStaticPesBuffer, mStaticPesBufferSize);
        if (lastData != null && size > 0) {
            mPesBuffer.write(lastData, pos, size);
        }
    }  
    
    void    updateMaxFrameSize(int size) {
        if (size > mMaxFrameSize) {
            mMaxFrameSize = size;
           // LOGD("[%s] mMaxFrameSize = %d", mCodecInfo?mCodecInfo->mName:"noname", mMaxFrameSize);
        }
    }
    boolean parsePesBuffer(int nextSize, boolean nextIsPesStart) {

        Log.d(TAG, String.format("pid 0x%x pes buffer size %d nextSize %d", getPid(), mPesBuffer.tell(), nextSize));
        Frame frm = new Frame(mPesBuffer.data(), -1, 0, -1, -1, 0);
        int r = locateFrame(mPesBuffer.data(), 0, mPesBuffer.tell(), frm);
        Log.d(TAG, String.format("locateFrame result = %d, frm.size = %d flag = 0x%x", r, frm.mSize, frm.mFlag));
        if (frm.mData > mPesBuffer.pointer() && mLastFrmIsPartial) {
            //if last frame is partial frame,  output the remains
            long pts = -1;//  mPtsQue.dequeue(mParsedBytes + frm.mData - mPesBuffer.pointer());
           // LOGV("dequeue pts = %lld offset = %lld", pts, mParsedBytes + frm.mData - mPesBuffer.pointer());
            Frame tfrm = new Frame(mPesBuffer.data(), 0, frm.mData - mPesBuffer.pointer(), -1, pts, 0);
            Log.d(TAG, String.format("frame not start from beginning of pes buffer, output packet size %d", tfrm.mSize));
            updateMaxFrameSize(tfrm.mSize);
            if (mListener != null) {
                mListener.onFrame(tfrm);
            }
            mParsedFrames++;
            mParsedBytes += tfrm.mSize;
            updateOutputBuffer(frm.mBuffer, frm.mData, mPesBuffer.tell() - tfrm.mSize);
            r = locateFrame(mPesBuffer.data(), 0, mPesBuffer.tell(), frm);
        }
        while (r > 0) {
            if (mMediaFormat == null) {
                parseMediaFormat(frm);
            }
            updateMaxFrameSize(frm.mSize);
            mParsedFrames++;
            int offset = frm.mData + frm.mSize - mPesBuffer.pointer();
            long pts = mPtsQue.dequeue(mParsedBytes + offset);
            Log.d(TAG, String.format("dequeue pts = %d offset = %d", pts, mParsedBytes + offset));
            if (mListener != null) {
                frm.mPts = pts;
                mListener.onFrame(frm);
            }
            mLastFrmIsPartial = false;
            mParsedBytes += offset;
            Log.d(TAG, String.format("update output buffer with %d bytes offset = %d, frame size = %d", mPesBuffer.tell() - offset, offset, frm.mSize));
            updateOutputBuffer(frm.mBuffer, frm.mData + frm.mSize, mPesBuffer.tell() - offset); 
            r = locateFrame(mPesBuffer.data(), 0, mPesBuffer.tell(), frm);
         }
        if (mPesBuffer.remains() >= nextSize) {
            return true;
        }
        Log.d(TAG, String.format("pes buffer remains %d nextsize %d", mPesBuffer.remains(), nextSize));
        
        if (mLastFrmIsPartial || frm.mData >= 0) {
            Log.w(TAG, "incomplete frame");
            /*
            if (mListener != null) {
                frm.reset(mExternalBuffer, mPesBuffer.pointer(), mPesBuffer.tell(), Frame.FLAG_PARTIAL);
                Log.d(TAG, String.format("frame not complete, output a partial frame size %d", frm.mSize));
                mListener.onFrame(ByteBuffer.wrap(frm.mBuffer, frm.mData, frm.mSize), frm.mPts, frm.mFlag);
                updateMaxFrameSize(frm.mSize);
            }
            */
            mLastFrmIsPartial = true;
            
        }
        
        mParsedBytes += mPesBuffer.tell();
        updateOutputBuffer(null, 0, 0);
        return true;
    }

    int mNumDetectPts;
    long mDetectPtsDeltas;
    long mLastPts;

    void detectFrameRate(long pts) {
        if (mNumDetectPts >= 25) {
            return;
        }
        Log.d(TAG, "update detected pts");
        if (mNumDetectPts > 0) {
            mDetectPtsDeltas += Math.abs(pts - mLastPts);
        }
        mLastPts = pts;
        mNumDetectPts++;
        if (mMediaFormat != null) {
            int accuracy = mMediaFormat.getInteger(MediaFormat.KEY_FRAME_RATE_ACCURACY, 69);
            if (accuracy < 70 && mDetectPtsDeltas > 0) {
                int fps = (int) ((1000000L * mNumDetectPts + mDetectPtsDeltas / 2) / mDetectPtsDeltas);
                if (fps > 5 && fps < 100) {
                    mMediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, fps);
                    mMediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE_ACCURACY, accuracy + 1);
                    Log.i(TAG, "update detected frame rate to " + fps + "accuracy = " + accuracy);
                }
            }
        }
    }
    @Override
    int parse(HeaderParser tsHeader, BufferReader inBuf, boolean forceParse) {
        if (tsHeader.hasError()) {
            Log.w(TAG, "see transport error indicator in packet header, discard packet.");
            mSawPesStart = false;
            return inBuf.remains();
        }
        if (mListener == null && !forceParse) {
            return inBuf.remains();
        }

        if (tsHeader.isPesStart() && !mSawPesStart) {
            mPesBuffer.seek(0);
            mSawPesStart = true;
        }

        if (!mSawPesStart) {
            Log.d(TAG, "discard data until see at least one PES start");
            return inBuf.remains();
        }

        if (mPesBuffer.data() == null) {
            updateOutputBuffer(null, 0, 0);
        }

        //parse pes buffer if do not have enough space to hold next inBuf
        //or we see pes start and it is a sizeInHeader(AVCC like) format
        if ((mPesBuffer.tell() > 0)
                && ((mSizeInHeader && tsHeader.isPesStart())
                || (mPesBuffer.remains() < inBuf.remains()))) {
             parsePesBuffer(inBuf.remains(), tsHeader.isPesStart());
        }

        assert(mPesBuffer.remains() >= inBuf.remains());

        int off = mPesBuffer.tell();
        int ret = mPesParser.parse(tsHeader, inBuf, mPesBuffer, mSLConfigDescr);
        if (ret < 0) {
            return -1;
        }

        long pts = mPesParser.getPts();
        if (pts >= 0) {
            Log.v(TAG, "enqueue pts " + pts + "offset = " + (mParsedBytes + off));
            mPtsQue.enqueue(pts, mParsedBytes + off);
            detectFrameRate(pts);
        }
        //parse pesBuffer for AnnexB like format, i.e, detect start code(0 0 0 1) to
        //find frame end
        if (!mSizeInHeader && tsHeader.isPesStart() && !parsePesBuffer(0, false)) {
            return -1;
        }
        return ret;
    
    }
    void flush() {
        if (mListener != null && mPesBuffer.remains() > 0) {
            parsePesBuffer(mPesBuffer.remains()+1, true);
        }
        mPesBuffer.reset(null, 0);
        mPtsQue.reset();
        mParsedFrames = 0;
        mParsedBytes = 0;
        mStaticPesBuffer = null;
        mLastFrmIsPartial = false;
        mSawPesStart = false;
    }
}