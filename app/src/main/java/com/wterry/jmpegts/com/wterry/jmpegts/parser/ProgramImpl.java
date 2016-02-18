package com.wterry.jmpegts.com.wterry.jmpegts.parser;

import java.util.ArrayList;
import java.util.List;

import com.wterry.jmpegts.com.wterry.jmpegts.parser.Mp4Descriptors.DecConfigDescriptor;
import com.wterry.jmpegts.com.wterry.jmpegts.parser.Mp4Descriptors.ESDescriptor;
import com.wterry.jmpegts.com.wterry.jmpegts.parser.Mp4Descriptors.IODDescriptor;
import com.wterry.jmpegts.com.wterry.jmpegts.parser.Mp4Descriptors.SLDescriptor;


public final class ProgramImpl extends PayloadParser implements Program {
    static final String TAG = ProgramImpl.class.getSimpleName();
    int     mProgramNumber;
    int     mNumOfParsedPmts;
    int     mPcrPid;
    int     mPrgInfoLen;
    byte  [] mPrgInfo;

    IODDescriptor mMp4Iod;
    SectionParser mSection = new SectionParser();
    List<PendingStream> mPendingStreams = new ArrayList<PendingStream>();
    List<StreamImpl> mStreams = new ArrayList<StreamImpl>();
    
    static final class PendingStream {
        int  mPid;
        int  mEsID;
        int  mPesType;
        PendingStream(int pid, int esid, int pesType) {
            mPid = pid;
            mEsID = esid;
            mPesType = pesType;
        }
    };
 
    ProgramImpl(ParserImpl tp, int pid, int progNum) {
        super(tp, pid);
        mProgramNumber = progNum;
    }

    @Override
    public Stream [] getStreams() {
        return mStreams.toArray(new Stream[mStreams.size()]);
    }
    
    @Override
    int parse(HeaderParser tsHeader, BufferReader inBuf, boolean forceParse) {

        if (mNumOfParsedPmts > 0) {
            //Log.d(TAG, String.format("Already has Parsed PMT for PID(%d), do not support PMT changes now, ignored", getPid()));
            return 0;
        }

        Log.i(TAG, String.format("******************* PMT of PID(%d) *************************", mPid));
        if (mSection.parse(tsHeader, inBuf) < 0) {
            return -1;
        }
        if (!mSection.complete()) {
            Log.i(TAG, String.format("section not complete sectionLength = %d, pos = %d, continue", mSection.getLength(), mSection.getPosition()));
            return 0;
        }
        if (mSection.getTableId() != 0x02) {
            Log.e(TAG, String.format("error, invalid PMT table id, expect 0x02 saw 0x%x", mSection.getTableId()));
            return -1;
        }

        if (mSection.getTableIdExt() != mProgramNumber) {
            Log.e(TAG, String.format("program number not match. mProgramNumber = %d, tableIdExt = %d",
                    mProgramNumber, mSection.getTableIdExt()));
            return -1;
        }
        
        BufferReader br = new BufferReader(mSection.getBuffer(), 0, mSection.getLength());
        mPcrPid = br.readU16() & 0x1FFF;
        mPrgInfoLen = br.readU16() & 0x0FFF;
        mPrgInfo = new byte[mPrgInfoLen];
        if (br.read(mPrgInfo, 0, mPrgInfoLen) != mPrgInfoLen) {
            Log.e(TAG, String.format("parse Program Info error mPrgInfoLen = %d", mPrgInfoLen));
            return -1;
        }
        Log.d(TAG, String.format("mPrgInfoLen = %d", mPrgInfoLen));
        if (mPrgInfoLen > 2) {
            if (parseProgInfo(mPrgInfo) < 0) {
                Log.e(TAG, "parseProgInfo failed");
                return -1;
            }
        }


        Log.i(TAG, String.format("PMT: table_id(0x%x) section len(%d) program number(0x%x) program info lenght(%d)",
                mSection.getTableId(), mSection.getLength(), mProgramNumber, mPrgInfoLen));

        if (mPrgInfoLen > mSection.getLength() - 8) {
            Log.e(TAG, String.format("invalid program info length(%d) mSectionLength (%d)", mPrgInfoLen, mSection.getLength()));
            return -1;
        }

        int streamInfoLen = mSection.getLength()  - mPrgInfoLen - 8;
        Log.i(TAG, String.format("stream info len = %d", streamInfoLen));

        int pos = br.tell();
        while (br.tell() - pos < streamInfoLen) {
            int pesType  = br.readU8();
            int pesPid   = br.readU16() & 0x1FFF;
            int pesInfoLen = br.readU16() & 0xFFF;
            if (pesInfoLen > streamInfoLen) {
                Log.e(TAG, String.format("pesInfoLen(%d) streamInfoLen(%d)", pesInfoLen, streamInfoLen));
                return -1;
            }
            BufferReader nbr = new BufferReader(br.data(), br.tell(), pesInfoLen);
            br.skip(pesInfoLen);
            int tag = nbr.readU8();
            int slesid = -1;
          
            if (tag == SLDescriptor.TAG && pesInfoLen > 2) {
                SLDescriptor sld = new SLDescriptor();
                if (sld.parse(tag, nbr) < 0) {
                    Log.e(TAG, "parse SLDescriptor  failed");
                    return -1;
                }
                slesid = sld.mEsID;
            }
            if (pesType == 0x13 || pesType == 0x12) {
                if (slesid < 0 || mMp4Iod == null) {
                    Log.e(TAG, String.format("No sldescriptor of MP4IOD for pesType 13 pid 0x%x", pesPid));
                    return -1;
                }
                if (pesType == 0x13) {
                    ESDescriptor esd = mMp4Iod.findESDescriptor(slesid);
                    if (esd != null) {
                        Log.d(TAG, String.format("found ESDescriptor with esid 0x%x", slesid));
                        SLPacketizedStreamInSection sl = new SLPacketizedStreamInSection(mTsParser, pesPid, slesid, this);
                        Log.d(TAG, String.format("Create SLPacket Parser for pid %x", pesPid));
                        mTsParser.setPayloadParser(pesPid, sl);
                    } else {
                        Log.w(TAG, String.format("slesid = 0x%x does not exist in MP4IOD, stream ignored", slesid));
                    }
                } else {
                    Log.i(TAG, String.format("add ESID 0x%x to pending pes stream pid(0x%x) type(0x%x)", slesid, pesPid, pesType));
                    addPendingStream(pesPid, slesid, pesType);
                }
            } else {
                StreamImpl st = StreamImpl.createStream(mTsParser, pesPid, pesType);
                if (st != null) {
                    mStreams.add(st);
                    mTsParser.setPayloadParser(pesPid, st);
                } else {
                    Log.w(TAG, String.format("Unknown stream type 0x%x, ignored", pesType));
                }
            }
        }
 
        Log.i(TAG, String.format("mNumOfStreams = %d, mNumPendingStream = %d", mStreams.size(), mPendingStreams.size()));
        mNumOfParsedPmts++;
        return br.tell();
    }
 
    boolean detected() {
        return mNumOfParsedPmts > 0;
    }
    
    int getNumPendingStream() {
        return mPendingStreams.size();
    }
    int parseProgInfo(final byte [] info) {
        BufferReader inBuf = new BufferReader(info, 0, info.length);
        int tag = inBuf.readU8();
        if (tag == IODDescriptor.TAG) {
            if ((mMp4Iod = (IODDescriptor)Mp4Descriptors.createDescriptor(tag, inBuf)) != null) {
               mMp4Iod.log(Log.DEBUG, 2);
            }
        }
        return 0;
    }
    void addPendingStream(int pid, int esid, int pestype) {
        mPendingStreams.add(new PendingStream(pid, esid, pestype));
    }
    void populatePendingStream(ESDescriptor  esd) {
        int esid = esd.mEsID;
        PendingStream ps = null;

        for (PendingStream pps : mPendingStreams) {
            if (pps.mEsID == esid) {
                ps = pps;
                break;
            }
        }
        if (ps == null) {
            Log.w(TAG, String.format("do not find pending stream for esid(0x%x)", esid));
            return;
        }
        if (mTsParser.getPayloadParser(ps.mPid) != null) {
            return;
        }
        DecConfigDescriptor dcfg = (DecConfigDescriptor )esd.findChild(DecConfigDescriptor.TAG);
        if (dcfg == null) {
            Log.w(TAG, String.format("No DecConfigDescriptor in ESDescriptor with esid(0x%x)", esid));
            return;
        }
    
        String mime = dcfg.getMimeType();
        Log.d(TAG, "dcfg codec mime = " + mime);
        if (mime.equals(MediaFormat.MIMETYPE_VIDEO_AVC)) {
            Log.i(TAG, String.format("add new stream AVC1 for pid(0x%x) esid(0x%x)", ps.mPid, ps.mEsID));
            StreamImpl   st = new H264Stream(mTsParser, ps.mPid, ps.mPesType);
            st.setESDescriptor(esd);
            mStreams.add(st);
            mTsParser.setPayloadParser(ps.mPid, st);
            mPendingStreams.remove(ps);
            Log.d(TAG, "mNumPendingStream = " + mPendingStreams.size());
        } else if (mime.equals(MediaFormat.MIMETYPE_AUDIO_AAC)) {
            Log.i(TAG, String.format("add new stream M4AC for pid(0x%x) esid(0x%x)", ps.mPid, ps.mEsID));
            StreamImpl st = new BsacStream(mTsParser, ps.mPid, ps.mPesType);
            st.setESDescriptor(esd);
            mStreams.add(st);
            mTsParser.setPayloadParser(ps.mPid, st);
            mPendingStreams.remove(ps);
            Log.d(TAG, "mNumPendingStream = " + mPendingStreams.size());
        }
    }
    void flush() {
        for (StreamImpl stream : mStreams) {
            stream.flush();
        }
    }
}
