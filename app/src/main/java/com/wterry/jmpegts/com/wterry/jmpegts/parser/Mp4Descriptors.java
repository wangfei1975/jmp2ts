package com.wterry.jmpegts.com.wterry.jmpegts.parser;

import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.Map;

public final class Mp4Descriptors {
    static final String LOGTAG = Mp4Descriptors.class.getSimpleName();
    public static int fourcc(CharSequence fcc) {
        return (((int)fcc.charAt(0)<<24)|((int)fcc.charAt(1)<<16)|((int)fcc.charAt(2)<<8)|((int)fcc.charAt(3)));
    }
    static Mp4Descriptor newMp4Descriptor(int tag) {
        for (Class<?> c : Mp4Descriptors.class.getDeclaredClasses()) {
            if (Mp4Descriptor.class.isAssignableFrom(c)) {
                try {
                    Field f = c.getField("TAG");
                    if (f.getInt(null) == tag) {
                        return (Mp4Descriptor)c.newInstance();
                    }
                } catch (NoSuchFieldException | SecurityException | InstantiationException | IllegalAccessException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }
        return new Mp4Descriptor(tag);
    }
    static Mp4Descriptor createDescriptor(int tag, BufferReader br) {
        int pos = br.tell();
        if (tag == 0) {
         tag = br.readU8();
        }
        Mp4Descriptor descr = newMp4Descriptor(tag);
        if (descr.parse(tag, br) != 0) {
            return null;
        }
        br.seek(pos + descr.size());
        return descr;
    }

    public static class Mp4Descriptor {
        Mp4Descriptor mParent;
        Mp4Descriptor mChildren;
        Mp4Descriptor mSibling;
        int mTag;
        int mSize;
        int mBytesOfSizeField;

        void removeChild(Mp4Descriptor ch) {
            Mp4Descriptor d = mChildren;
            while (d != null && d != ch && d.mSibling != ch) {
                d = d.mSibling;
            }
            if (d != null && d == ch) {
                mChildren = d.mSibling;
            } else if (d != null && d.mSibling == ch) {
                d.mSibling = ch.mSibling;
            }
            ch.mParent = null;
        }

        Mp4Descriptor(int tag) {
            mTag = tag;
        }

        int size() {
            return mSize + 1 + mBytesOfSizeField;
        }

        int tag() {
            return mTag;
        }

        int parseDetails(BufferReader br) {
            br.skip(mSize);
            return 0;
        }

        int parseChildren(BufferReader br, int sizeLeft) {
            Mp4Descriptor d;
            while (sizeLeft > 2) {
                if ((d = createDescriptor(0, br)) == null) {
                    return -1;
                }
                addChild(d);
                sizeLeft -= d.size();
            }
            return 0;
        }

        int parse(int tag, BufferReader br) {
            mTag = tag;
            mSize = 0;
            mBytesOfSizeField = 0;
            int tmp;
            do {
                tmp = br.readU8();
                mSize = (mSize << 7) | (tmp & 0x7F);
                mBytesOfSizeField++;
            } while ((tmp & 0x80) != 0 && (mBytesOfSizeField < 4));

            // Log.d(LOGTAG, String.format("tag = 0x%x, size = %d", mTag, mSize));
            if (br.remains() < mSize) {
                Log.e(LOGTAG, String.format("no enough data, remains = %d, descriptor size = %d", br.remains(), mSize));
                return -1;
            }
            return parseDetails(br);
        }

        void addChild(Mp4Descriptor ch) {
            ch.mSibling = mChildren;
            ch.mParent = this;
            mChildren = ch;
        }

        void detachFromParent() {
            if (mParent != null) {
                mParent.removeChild(this);
            }
        }

        Mp4Descriptor next() {
            if (mChildren != null) {
                return mChildren;
            } else if (mSibling != null) {
                return mSibling;
            }
            for (Mp4Descriptor p = mParent; p != null; p = p.mParent) {
                if (p.mSibling != null) {
                    return p.mSibling;
                }
            }
            return null;
        }

        Mp4Descriptor findChild(int tag) {
            for (Mp4Descriptor d = this; d != null; d = d.next()) {
                if (d.tag() == tag) {
                    return d;
                }
            }
            return null;
        }
        
        void logFields(int level, String indent) {
            for (Field f : this.getClass().getDeclaredFields()) {
                try {
                    int v = 0;
                    if (f.getType().equals(Integer.class)) {
                        v = f.getInt(this);
                    } else if (f.getType().equals(Long.class)) {
                        v = (int) f.getLong(this);
                    }
                    String txt = String.format("%s%s = %d(0x%x)", indent, f.getName(), v, v);
                    Log.log(level, LOGTAG, txt);
                } catch (IllegalArgumentException | IllegalAccessException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }
        void log(int level, int indent) {
            StringBuilder strIndent = new StringBuilder();
            for (int i = 0; i < indent; i++) {
                strIndent.append("    ");
            }
            String txt = strIndent.toString() + (mChildren == null ? '-' : '+') + this.getClass().getSimpleName();
            Log.log(level, LOGTAG, txt);
            for (Mp4Descriptor  c = mChildren; c != null; c = c.mSibling) {
                c.log(level, indent+1);
            }
        }
    }

    public static final class InitialObjectDescriptor extends Mp4Descriptor {
        static final int TAG = 0x02;
        int mObjectDescriptorID;
        boolean mUrlFlag;
        byte[] mUrl;
        int mIncludeInlineProfilesFlag;
        int mODProfile;
        int mSceneProfile;
        int mAudioProfile;
        int mVisualProfile;
        int mGraphicsProfile;

        InitialObjectDescriptor() {
            super(TAG);
        }

        @Override
        int parseDetails(BufferReader br) {
            int data = br.readU16();
            mObjectDescriptorID = data >> 6;
            mUrlFlag = ((data >> 5) & 0x1) != 0;
            mIncludeInlineProfilesFlag = (data >> 4) & 0x1;
            int sizeLeft = mSize - 2;
            if (mUrlFlag) {
                int len = br.readU8();
                mUrl = new byte[len];
                if (br.read(mUrl, 0, len) != len) {
                    Log.e(LOGTAG, String.format("could not read url for len %d", len));
                    return -1;
                }
                sizeLeft -= (len + 1);
            } else {
                mODProfile = br.readU8();
                mSceneProfile = br.readU8();
                mAudioProfile = br.readU8();
                mVisualProfile = br.readU8();
                mGraphicsProfile = br.readU8();
                sizeLeft -= 5;
            }
            return parseChildren(br, sizeLeft);
        }
    }

    public static final class IODDescriptor extends Mp4Descriptor {
        static final int TAG = 0x1D;
        int mScope;
        int mLabel;

        IODDescriptor() {
            super(TAG);
        }
        ESDescriptor findESDescriptor(int esid) {
            for (Mp4Descriptor  d = this; d != null; d = d.next()) {
                if (d.tag() == ESDescriptor.TAG && ((ESDescriptor)d).mEsID == esid) {
                    return (ESDescriptor)d;
                }
            }
            return null;
        }
        @Override
        int parseDetails(BufferReader br) {
            mScope = br.readU8();
            mLabel = br.readU8();
            Log.d(LOGTAG, String.format("mScope = 0x%x, mLabel = 0x%x", mScope, mLabel));
            return parseChildren(br, mSize - 2);
        }
    }

    public static final class DecSpecificDescriptor extends Mp4Descriptor {
        static final int TAG = 0x05;
        byte [] mInfo;

        DecSpecificDescriptor() {
            super(TAG);
        }

        @Override
        int parseDetails(BufferReader br) {
            mInfo = new byte[mSize];
            if (br.read(mInfo, 0, mSize) != mSize) {
                Log.e(LOGTAG, "Decoder specific info failed");
                return -1;
            }
            return 0;
        }

    }

    public static final class DecConfigDescriptor extends Mp4Descriptor {

        static final int TAG = 0x04;
        int mObjectType;
        int mStreamType;
        int mUpStream;
        int mBufferSizeDB;
        long mMaxBitrate;
        long mAvgBitrate;

        static final class CodecInfo {
            int    fourcc;
            String mime;
            String name;
            CodecInfo(int fcc, String m, String n) {
                fourcc = fcc;
                mime = m;
                name = n;
            }
        }
        static final Map<Integer, CodecInfo> mCodecInfo = new IdentityHashMap<Integer, CodecInfo>();
        static {
            //ISO/IEC-14496-1 Table 5
            mCodecInfo.put(0x08, new CodecInfo(fourcc("TEXT"), "text/stream", "Streaming Text Stream"));
            mCodecInfo.put(0x20, new CodecInfo(fourcc("MP4V"), MediaFormat.MIMETYPE_VIDEO_MPEG4, "MP4V Visual ISO/IEC 14496-2"));
            mCodecInfo.put(0x21, new CodecInfo(fourcc("AVC1"), MediaFormat.MIMETYPE_VIDEO_AVC, "AVC  Visual H.264|ISO/IEC 14496-10"));
            mCodecInfo.put(0x40, new CodecInfo(fourcc("M4AC"), MediaFormat.MIMETYPE_AUDIO_AAC, "AAC  Audio ISO/IEC 14496-3"));
            mCodecInfo.put(0x01, new CodecInfo(fourcc("MP4S"), "system/a", "Systems ISO/IEC 14496-1 a"));
            mCodecInfo.put(0x02, new CodecInfo(fourcc("MP4S"), "system/b", "Systems ISO/IEC 14496-1 b"));
        }
  
        DecSpecificDescriptor getSpecific() {
            return (DecSpecificDescriptor) findChild(DecSpecificDescriptor.TAG);
        }

        DecConfigDescriptor() {
            super(TAG);
        }

        int getFourcc() {
            CodecInfo info = mCodecInfo.get(mObjectType);
            if (info != null) {
                return info.fourcc;
            }
            return 0;
        }
        String getMimeType() {
            CodecInfo info = mCodecInfo.get(mObjectType);
            if (info != null) {
                return info.mime;
            }
            return "unknown";
        }
        @Override
        int parseDetails(BufferReader br) {
            mObjectType = br.readU8();
            CodecInfo info = mCodecInfo.get(mObjectType);
            if (info != null) {
                Log.i(LOGTAG, "Codec name " + info.name + " mime:" + info.mime);
            }
            
            int data = br.readU8();
            mStreamType = data>>2;
            mUpStream = (data>>1)&1;
            mBufferSizeDB = br.readU24();
            mMaxBitrate = br.readU32();
            mAvgBitrate = br.readU32();
            return parseChildren(br, mSize - 2 - 3 - 4 - 4);
        }

    };

    public static final class SLConfigDescriptor extends Mp4Descriptor {
        static final int TAG = 0x06;
        boolean mUseAccessUnitStartFlag;
        boolean mUseAccessUnitEndFlag;
        boolean mUseRandomAccessPointFlag;
        boolean mHasRandomAccessUnitsOnlyFlag;
        boolean mUsePaddingFlag;
        boolean mUseTimeStampsFlag;
        boolean mUseIdleFlag;
        boolean mDurationFlag;
        long mOCRResolution;
        int mTimeStampLength;
        int mOCRLength;
        int mAU_Length;
        int mInstantBitrateLength;
        int mDegradationPriorityLength;
        int mAU_seqNumLength;
        int mPacketSeqNumLength;
        int mTimeScale;
        int mAccessUnitDuration;
        int mCompositionUnitDuration;
        long mTimeStampResNum;
        long mTimeStampResDen;

        SLConfigDescriptor() {
            super(TAG);
        }
        static long gcd(long x, long y) {
            return y != 0 ? gcd(y, x%y) : x;
        }
        @Override
        int parseDetails(BufferReader br) {
            int pos = br.tell();
            int data = br.readU8();
            if (data != 0) {
                Log.e(LOGTAG, "predefined not zero");
                br.skip(mSize-1);
                return 0;
            }
            data = br.readU8();
            mUseAccessUnitStartFlag = ((data&0x80)>>7) != 0;
            mUseAccessUnitEndFlag = ((data&0x40)>>6) != 0;
            mUseRandomAccessPointFlag =  ((data&0x20)>>5) != 0;
            mHasRandomAccessUnitsOnlyFlag =  ((data&0x10)>>4) != 0;
            mUsePaddingFlag =  ((data&0x08)>>3) != 0;
            mUseTimeStampsFlag =  ((data&0x04)>>2) != 0;
            mUseIdleFlag =  ((data&0x02)>>1) != 0;
            mDurationFlag = (data&0x01) != 0;
            mTimeStampResDen = br.readU32();
            mTimeStampResNum = 1000000;
            if (mTimeStampResDen != 0) {
                long g = gcd(mTimeStampResDen, mTimeStampResNum);
                if (g > 0) {
                    mTimeStampResNum /= g;
                    mTimeStampResDen /= g;
                }
            }
            Log.d(LOGTAG, String.format("mTimeStampResNum = %d, mTimeStampResDen = %d", mTimeStampResNum, mTimeStampResDen));
            mOCRResolution = br.readU32();
            if ((mTimeStampLength = br.readU8()) > 64) {
                Log.e(LOGTAG, String.format("Invalid timeStampLength = %d", mTimeStampLength));
                return -1;
            }
            Log.d(LOGTAG, String.format("timeStampLength = %d", mTimeStampLength));
            if ((mOCRLength = br.readU8()) > 64) {
                Log.e(LOGTAG, String.format("Invalid OCRLength = %d", mOCRLength));
                return -1;
            }
            if ((mAU_Length = br.readU8()) > 32) {
                Log.e(LOGTAG, String.format("Invalid AU_Length = %d", mAU_Length));
                return -1;
            }
            mInstantBitrateLength = br.readU8();
            int len = br.readU16();
            mDegradationPriorityLength = len>>12;
            if (((mAU_seqNumLength = (len>>7)&0x1F)) > 16) {
                Log.e(LOGTAG, String.format("Invalid AU_seqNumLength = %d", mAU_seqNumLength));
                return -1;
            }
            if ((mPacketSeqNumLength = (len>>2)&0x1F) > 16) {
                Log.e(LOGTAG, String.format("Invalid packetSeqNumLength = %d", mPacketSeqNumLength));
                return -1;
            }
            br.seek(pos + mSize);
            return 0;
        }

    };

    public static final class ESDescriptor extends Mp4Descriptor {

        static final int TAG = 0x03;
        int mEsID;
        int mStreamDependenceFlag;
        int mDependsOnEsID;
        int mUrlFlag;
        byte[] mUrl;
        int mOCRStreamFlag;
        int mOCREsID;
        int mStreamPriority;

        ESDescriptor() {
            super(TAG);
        }

        @Override
        int parseDetails(BufferReader br) {
            mEsID = br.readU16();
            int data = br.readU8();
            mStreamDependenceFlag = (data>>7)&1;
            mUrlFlag = (data>>6)&1;
            mOCRStreamFlag = (data>>5)&1;
            mStreamPriority = data&0x1F;

            int sizeLeft = mSize - 3;
            if (mStreamDependenceFlag != 0) {
                mDependsOnEsID = br.readU16();
                sizeLeft -= 2;
            }
            if (mUrlFlag != 0) {
                int len = br.readU8();
                mUrl = new byte[len];
                if (br.read(mUrl, 0, len) != len) {
                    Log.e(LOGTAG, String.format("read url fail len = %d", len));
                    return -1;
                }
                sizeLeft -= (len + 1);
            }
            if (mOCRStreamFlag != 0) {
                mOCREsID = br.readU16();
                sizeLeft -= 2;
            }
            return parseChildren(br, sizeLeft);
        }

    };

    public static final class ODescriptor extends Mp4Descriptor {
        static final int TAG = 0x01;
        int mDstESId;
        ODescriptor() {
            super(TAG);
        }
        @Override
        int parseDetails(BufferReader br) {
            mDstESId = br.readU16();
            return parseChildren(br, mSize - 2);
        }
    }

    public static final class SLDescriptor extends Mp4Descriptor {
        static final int TAG = 0x1E;
        int mEsID;
        SLDescriptor() {
            super(TAG);
        }

        @Override
        int parseDetails(BufferReader br) {
            mEsID = br.readU16();
            return parseChildren(br, mSize - 2);
        }
    }
}