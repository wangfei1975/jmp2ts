package com.wterry.jmpegts.com.wterry.jmpegts.parser;

/**
 * Created by Fei Wang on 2/26/2016.
 */
/*
 * Stream parser for MPEG4 Visual  IEC-14496 part 2(compatible with H263)
 * http://akuvian.org/src/x264/ISO-IEC-14496-2_2001_MPEG4_Visual.pdf.gz
 *
 * */
public class Mp4vStream extends VideoStream {
    static final String TAG = Mp4vStream.class.getSimpleName();
    static final int MP4V_VOL_START_CODE_START = 0x20; //20 to 2F
    static final int MP4V_VOL_START_CODE_END = 0x2F; //20 to 2F
    static final int MP4V_VOP_START_CODE = 0xB6;

    Mp4vStream(ParserImpl tp, int pid, int streamType) {
        super(tp, pid, streamType);
    }


    @Override
    int locateFrame(byte[] data, int start, int size, Frame frm) {
        final int end = start + size;
        int fstart = start;
        frm.mData = -1;
        frm.mFlag = 0;
        frm.mSize = 0;

        //find VOP start
        while ((fstart = findStartCode(data, fstart, end)) >= 0) {
            final int code = data[fstart + 3]&0xFF;
            if (code == MP4V_VOP_START_CODE) {
                break;
            }
            fstart += 3;
        }
        if (fstart < 0 || fstart + 4 < end) {
            return -1;
        }
        frm.mData = fstart;
        frm.mFlag = 0;
        if (((((data[fstart+4]&0xFF) >> 6) & 0x3) == 0)) {
            frm.mFlag = Frame.FLAG_IFRAME;
        }
        //find next VOP
        while((fstart = findStartCode(data, fstart+3, end)) >= 0) {
            final int code = data[fstart + 3]&0xFF;
            if (code >= MP4V_VOL_START_CODE_START && code <= MP4V_VOL_START_CODE_END) {
                frm.mFlag |= Frame.FLAG_CODEC_CONF;
            } else if (code == MP4V_VOP_START_CODE) {
                frm.mSize = fstart - frm.mData;
                return 1;
            }
        }
        return 0;
    }

    static int getBitsRequired(int num) {
        int r = 0;
        while (num > 0) {
            r++;
            num >>= 1;
        }
        return r;
    }
    @Override
    void parseMediaFormat(Frame frm) {
        final  byte [] data = frm.mBuffer;
        int sp = frm.mData;
        int ep = sp + frm.mSize;
        while ((sp = findStartCode(data, sp, ep)) >= 0) {
            final int code = data[sp+3]&0xFF;
            if ( code >= MP4V_VOL_START_CODE_START && code <= MP4V_VOL_START_CODE_END) {
                break;
            }
            sp += 3;
        }
        if (sp + 6 >= ep) {
            Log.w(TAG, "Could not find VOL object");
            return;
        }
        BitBufferReader bitBuf = new BitBufferReader(data, (sp+4) * 8, (ep-sp-4) * 8);
        Log.i(TAG, "MP4V parse codec info");
        MediaFormat fmt = new MediaFormat();
        fmt.setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_VIDEO_MPEG4);
        fmt.setInteger(MediaFormat.KEY_FOURCC, Mp4Descriptors.fourcc("MP4V"));
        fmt.setString(MediaFormat.KEY_DESCRIPTION, "MPEG4 Visual ISO/IEC14496-2");
        //random_accessible_vol and video_object_type_indication
        //bitBuf.read(9);
        bitBuf.skip(9);
        //is_object_layer_identifier
        if (bitBuf.read(1) != 0) {
            // video_object_layer_verid
            // bitBuf.read(4);
            // video_object_layer_priority
            // bitBuf.read(3);
            bitBuf.skip(7);
        }

        int aspectRatio = bitBuf.read(4);
        if (aspectRatio == 15) {
            int parX = bitBuf.read(8);
            int parY = bitBuf.read(8);
            fmt.setInteger(MediaFormat.KEY_ASPECT_RATIO_WIDTH, parX);
            fmt.setInteger(MediaFormat.KEY_ASPECT_RATIO_HEIGHT, parY);
        } else {
            fmt.setInteger(MediaFormat.KEY_ASPECT_RATIO_WIDTH, aspectRatio);
            fmt.setInteger(MediaFormat.KEY_ASPECT_RATIO_HEIGHT, 1);
        }
        //vol_contorl_parameters
        if (bitBuf.read(1) != 0) {
            //chroma_format, low_delay
            //bitBuf.read(3);
            bitBuf.skip(3);
            //vbv_parameters
            if (bitBuf.read(1) != 0) {
                /*
                bitBuf.read(16);
                bitBuf.read(16);
                bitBuf.read(16);
                bitBuf.read(3);
                bitBuf.read(12);
                bitBuf.read(16);
                */
                bitBuf.skip(16+16+16+3+12+16);
            }
        }
        //  video_object_layer_shape
        int video_object_layer_shape = bitBuf.read(2);
        if (video_object_layer_shape == 0x03) {
            //bitBuf.read(4);
            bitBuf.skip(4);
        }
        //  marker_bit
        bitBuf.skip(1);
        //  vop_time_increment_resolution
        int vop_time_increment_resolution = bitBuf.read(16);
        //  marker_bit
        bitBuf.skip(1);
        //  fixed_vop_rate
        if (bitBuf.read(1) != 0) {
            //  fixed_vop_time_increment
            bitBuf.skip(getBitsRequired(vop_time_increment_resolution));
        }

        // Only worry about rectangular
        if (video_object_layer_shape == 0) {
            //  marker_bit
            bitBuf.skip(1);
            int width = bitBuf.read(13);
            //  marker_bit
            bitBuf.skip(1);
            int heigh = bitBuf.read(13);
            //  marker_bit
            bitBuf.skip(1);
            if (width > 0 && heigh > 0) {
                fmt.setInteger(MediaFormat.KEY_WIDTH, width);
                fmt.setInteger(MediaFormat.KEY_HEIGHT, heigh);
            }
        }
        //TODO FPS
        fmt.setInteger(MediaFormat.KEY_FRAME_RATE, 24);
        fmt.setInteger(MediaFormat.KEY_FRAME_RATE_ACCURACY, 100);
        
        mMediaFormat = fmt;
        Log.i(TAG, "MP4V format = " + fmt);

    }
}
