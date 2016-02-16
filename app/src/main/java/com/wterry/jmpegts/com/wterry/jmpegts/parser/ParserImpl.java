package com.wterry.jmpegts.com.wterry.jmpegts.parser;

/**
 * Created by feiwang on 15-03-23.
 */
public class ParserImpl implements Parser {

    static final String TAG = ParserImpl.class.getSimpleName();
    static final int MAX_PID = 4096*2;
    static final int TS_PACKET_SIZE = 188;
    static final int TS_MAX_PACKET_SIZE = 214;
    static final int MIN_FORMAT_DETECTION_SIZE = TS_PACKET_SIZE*7*64;

    private PayloadParser [] mPayloadParsers = new PayloadParser[MAX_PID];
    
    private int mTsPktSize;
    private Pat mPat;
    
    private ParserImpl() {
        mTsPktSize = TS_PACKET_SIZE;
        for (int i = 0; i < mPayloadParsers.length; i++) {
            mPayloadParsers[i] = null;
        }
        mPat = new Pat(this);
        mPayloadParsers[PayloadParser.PAT_PID] = mPat;
    }
    public PayloadParser getPayloadParser(int pid) {
        if (pid >= 0 && pid < MAX_PID) {
            return mPayloadParsers[pid];
        }
        return null;
    }
    
    void setPayloadParser(int pid, PayloadParser pp) {
        if (pid >= 0 && pid < MAX_PID) {
            mPayloadParsers[pid] = pp;
        }
    }
    public static Parser createParser() {
        return new ParserImpl();
    }
 

    @Override
    public int detectFormat(byte[] data, int pos, int size) {

        if (size < MIN_FORMAT_DETECTION_SIZE) {
            return 0;
        }
        int[] offset = new int[] { 0 };
        int pktsize;
        for (pktsize = TS_PACKET_SIZE; pktsize < TS_MAX_PACKET_SIZE + 1; pktsize++) {
            if (tasteData(data, pos, size, pktsize, offset) >= (MIN_FORMAT_DETECTION_SIZE / pktsize)) {
                break;
            }
        }
        if (pktsize > TS_MAX_PACKET_SIZE) {
            Log.e(TAG, "invalid pktsize = " + pktsize);
            return -1;
        }

        mTsPktSize = pktsize;
        if (parse(data, offset[0], size - offset[0], true) <= 0) {
            Log.e(TAG, "parse error");
            return -1;
        }
        if (!mPat.detected()) {
            return 1;
        }
        int detectedPmt = 0, detectedStream = 0, knownStream = 0, pendingStream = 0;
        for (ProgramImpl prog : mPat.getPrograms()) {
            pendingStream += prog.getNumPendingStream();
            if (prog.detected()) {
                detectedPmt++;
                for (Stream stream : prog.getStreams()) {
                    knownStream++;
                    if (stream.getFormat() != null) {
                        detectedStream++;
                    }
                }
            }
        }
        if (detectedPmt == 0) {
            return 2;
        } else if (detectedStream == 0) {
            return 3;
        } else if (detectedStream != knownStream || pendingStream > 0) {
            return 4;
        }
        return 5;

    }

    @Override
    public Program [] getPrograms() {
        return mPat.getPrograms();
    }
    
    int parsePacket(final byte [] pkt, int pos,  boolean forceParse) {
        BufferReader inBuf = new BufferReader(pkt, pos, mTsPktSize);
        HeaderParser header = new HeaderParser();
        int ret;

        if ((ret = header.parse(inBuf)) < 0) {
            Log.i(TAG, "parse TS header error");
            return ret;
        }
        if (header.hasError()) {
            Log.i(TAG, "see transport error indicator in packet header, discard packet.");
            return mTsPktSize;
        }
        if (header.hasPayload() && inBuf.remains() > 0) {
            int pid =  header.getPid();
            if (pid >= 0 && pid < MAX_PID) {
                PayloadParser  pp = mPayloadParsers[pid];
                if (pp != null && (ret = pp.parse(header, inBuf, forceParse)) < 0) {
                    return ret;
                }
            }
        }
        //Log.i(TAG, "inbuf .tell = " + inBuf.tell() + " pos = " + pos);
        return inBuf.tell() - pos;
    }
    int parse(final byte [] data, int pos, int size, boolean forceParse) {
        int parsed = 0;
        while(size >= mTsPktSize) {
            if (parsePacket(data, pos+parsed, forceParse) < 0) {
                return -1;
            }
            parsed += mTsPktSize;
            size   -= mTsPktSize;
        }
        return parsed;
    }
    @Override
    public int parse(byte[] data, int pos, int size) {
        return parse(data, pos, size, false);
    }
    
    public int getPacketSize() {
        return mTsPktSize;
    }
    
    
    
    private int tasteData(final byte [] data, int pos, int size, int pktsize, int [] offset) {
            int [] score = new int[TS_MAX_PACKET_SIZE+1];
            int best = 0, bestidx = -1;
            
            for (int i = pos; i < pos + size - 3; i++) {
                if (data[i] == HeaderParser.SYNC_BYTE) {
                    int idx = i % pktsize;
                    if (++score[idx] > best) {
                        best = score[idx];
                        bestidx = idx;
                    }
                }
            }
           // LOGI("bestidx = %d, bestscore = %d, size = %d, pktsize = %d", bestidx, score[bestidx], size, pktsize);
            if (offset != null && offset.length > 0 && bestidx >= 0) {
                offset[0] = bestidx;
            }
            return best;
    }
 
    
}
