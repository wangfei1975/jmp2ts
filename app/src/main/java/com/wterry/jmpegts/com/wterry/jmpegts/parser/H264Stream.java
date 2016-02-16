package com.wterry.jmpegts.com.wterry.jmpegts.parser;

public final class H264Stream extends VideoStream {
    static final String TAG = H264Stream.class.getSimpleName();

    static final int H264NalSlice = 1;
    static final int H264NalSliceDPA = 2;
    static final int H264NalSliceDPB = 3;
    static final int H264NalSliceDPC = 4;
    static final int H264NalSliceIDR = 5;
    static final int H264NalSEI = 6;
    static final int H264NalSPS = 7;
    static final int H264NalPPS = 8;
    static final int H264NalAuDelimiter = 9;
    static final int H264NalSeqEnd = 10;
    static final int H264NalStreamEnd = 11;
    static final int H264NalFilterData = 12;

    H264Stream(ParserImpl tp, int pid, int streamType) {
        super(tp, pid, streamType);
    }

    private int locateFrameH264(final byte[] data, int start, int size, Frame outFrame) {
        int ep = start + size;
        int sp = start;
        int[] flg = new int[1];
        while (ep - sp > 4) {
            if ((sp = findStartCode(data, sp, ep)) < 0) {
                break;
            }
            if (outFrame.mData < 0) {
                outFrame.mData = sp;
            }
            flg[0] = 0;
            if (isAccessUnitStart(data, sp + 3, flg)) {
                if (sp > outFrame.mData) {
                    break;
                }
            }
            outFrame.mFlag |= flg[0];
            sp += 3;
        }
        if (outFrame.mData < 0) {
            return -1;
        }
        if (sp >= 0 && ep - sp > 4) {
            outFrame.mSize = sp - outFrame.mData;
            Log.d(TAG, String.format("got h264 frm data %d frame size %d, flag:0x%x", outFrame.mData, outFrame.mSize,
                    outFrame.mFlag));
            return 1;
        }
        return 0;

    }

    static final int[][] h264_aspec_ratio_from_idc = new int[][] { { 1, 1 }, { 1, 1 }, { 12, 11 }, { 10, 11 },
            { 16, 11 }, { 40, 33 }, { 24, 11 }, { 20, 11 }, { 32, 11 }, { 80, 33 }, { 18, 11 }, { 15, 11 }, { 64, 33 },
            { 160, 99 } };

    static void skipScalingList(BitBufferReader bitBuf, int size) {
        if (bitBuf.read() == 0) {
            return;
        }
        int val = 8;
        for (int i = 0; i < size; i++) {
            if (val != 0) {
                val = (val + (int) bitBuf.readExpGolombSigned()) & 0xff;
            }
            if (i == 0 && val == 0) {
                break;
            }
        }
    }

    static int decodeSps(MediaFormat fmt, final byte[] data, int start, int size) {
        BitBufferReader bitBuf = new BitBufferReader(data, start * 8, size * 8);

        int profile = bitBuf.read(8);
        int profileCompatibility = bitBuf.read(8);
        int levelIndication = bitBuf.read(8);
        Log.i(TAG, String.format("profile = %d, compatibility = %d, level indication = %d", profile,
                profileCompatibility, levelIndication));

        long setId = bitBuf.readExpGolomb();
        Log.d(TAG, String.format("parameter set id = %d", setId));
        // ISO/IEC14496-10 7.3.2.1.1 Sequence parameter set data syntax
        if (profile == 100 || profile == 110 || profile == 122 || profile == 144 || profile == 44 || profile == 83
                || profile == 86 || profile == 118 || profile == 128 || profile == 144) {
            long chroma_format_idc = bitBuf.readExpGolomb();
            if (chroma_format_idc == 3) {
                // residual_colour_transform_flag = bitBuf.read();
                bitBuf.skip(1);
            }

            // bit_depth_luma_minus8 = bitBuf.readExpGolomb();
            bitBuf.readExpGolomb();
            // bit_depth_chroma_minus8 = bitBuf.readExpGolomb();
            bitBuf.readExpGolomb();
            // qpprime_y_zero_transform_bypass_flag = bitBuf.read();
            bitBuf.skip(1);
            int seq_scaling_matrix_present_flag = bitBuf.read();
            if (seq_scaling_matrix_present_flag != 0) {
                for (int i = 0; i < 6; i++) {
                    skipScalingList(bitBuf, 16);
                }
                for (int i = 0; i < 2; i++) {
                    skipScalingList(bitBuf, 64);
                }
            }
        }
        // log2_max_frame_num_minus4 = bitBuf.readExpGolomb();
        bitBuf.readExpGolomb();
        int pic_order_cnt_type = (int) bitBuf.readExpGolomb();
        if (pic_order_cnt_type == 0) {
            // int log2_max_pic_order_cnt_lsb_minus4 = bitBuf.readExpGolomb();
            bitBuf.readExpGolomb();
        } else if (pic_order_cnt_type == 1) {
            // int delta_pic_order_always_zero_flag = bitBuf.read();
            bitBuf.read();
            // int offset_for_non_ref_pic = bitBuf.readExpGolombSigned();
            bitBuf.readExpGolombSigned();
            // int offset_for_top_to_bottom_field =
            // bitBuf.readExpGolombSigned();
            bitBuf.readExpGolombSigned();
            int num_ref_frames_in_pic_order_cnt_cycle = (int) bitBuf.readExpGolomb();
            for (int i = 0; i < num_ref_frames_in_pic_order_cnt_cycle; i++) {
                // offset_for_ref_frame[i] = read_exp_golomb_s(buf);
                bitBuf.readExpGolombSigned();
            }
        }
        // int num_ref_frames = bitBuf.readExpGolomb();
        bitBuf.readExpGolomb();
        // int gaps_in_frame_num_value_allowed_flag = bitBuf.read();
        bitBuf.read();

        int pic_width = 16 * ((int) bitBuf.readExpGolomb() + 1);
        int pic_height = 16 * ((int) bitBuf.readExpGolomb() + 1);

        int frame_mbs_only_flag = bitBuf.read();

        pic_height = (2 - frame_mbs_only_flag) * pic_height;
        Log.i(TAG, String.format("pic_width = %d pic_heigh = %d", pic_width, pic_height));

        fmt.setInteger(MediaFormat.KEY_WIDTH, pic_width);
        fmt.setInteger(MediaFormat.KEY_HEIGHT, pic_height);
        if (frame_mbs_only_flag == 0) {
            // int mb_adaptive_frame_field_flag = bitBuf.read();
            bitBuf.read();
        }

        // int direct_8x8_inference_flag = bitBuf.read();
        bitBuf.read();
        int frame_cropping_flag = bitBuf.read();
        if (frame_cropping_flag != 0) {
            // int frame_crop_left_offset = bitBuf.readExpGolomb();
            bitBuf.readExpGolomb();
            // int frame_crop_right_offset = bitBuf.readExpGolomb();
            bitBuf.readExpGolomb();
            // int frame_crop_top_offset = bitBuf.readExpGolomb();
            bitBuf.readExpGolomb();
            // int frame_crop_bottom_offset = bitBuf.readExpGolomb();
            bitBuf.readExpGolomb();
        }

        int vui_parameters_present_flag = bitBuf.read();

        if (vui_parameters_present_flag != 0) {
            Log.d(TAG, "VUI Parameters present");
            int sar_width = 1, sar_height = 1;
            int aspect_ratio_idc;
            // aspect_ratio_info_present_flag
            if (bitBuf.read() != 0) {
                if ((aspect_ratio_idc = bitBuf.read(8)) == 0xFF) {
                    sar_width = bitBuf.read(16);
                    sar_height = bitBuf.read(16);
                    Log.d(TAG, String.format("extended SAR sar_width = %d sar_height = %d", sar_width, sar_height));
                } else if (aspect_ratio_idc < 14) {
                    sar_width = h264_aspec_ratio_from_idc[aspect_ratio_idc][0];
                    sar_height = h264_aspec_ratio_from_idc[aspect_ratio_idc][1];
                }

                Log.d(TAG, "aspect_ratio_idc = " + aspect_ratio_idc);
            }
            fmt.setInteger(MediaFormat.KEY_ASPECT_RATIO_WIDTH, sar_width);
            fmt.setInteger(MediaFormat.KEY_ASPECT_RATIO_HEIGHT, sar_height);
            // overscan_info_present_flag
            if (bitBuf.read() != 0) {
                // overscan_appropriate_flag
                bitBuf.skip(1);
            }

            // video_signal_type_present_flag
            if (bitBuf.read() != 0) {
                // video_format
                bitBuf.skip(3);
                // video_full_range_flag
                bitBuf.skip(1);
                // colour_description_present_flag
                if (bitBuf.read() != 0) {
                    // colour_primaries
                    bitBuf.skip(8);
                    // transfer_characteristics
                    bitBuf.skip(8);
                    // matrix_coefficients
                    bitBuf.skip(8);
                }
            }
            // chroma_loc_info_present_flag
            if (bitBuf.read() != 0) {
                // chroma_sample_loc_type_top_field
                bitBuf.readExpGolomb();
                // chroma_sample_loc_type_bottom_field
                bitBuf.readExpGolomb();
            }
            // timing_info_present_flag
            if (bitBuf.read() != 0) {
                int num_units_in_tick, time_scale, fixed_frame_rate_flag;
                if ((num_units_in_tick = bitBuf.read(32)) == 0) {
                    Log.w(TAG, "num_units_in_tick = 0, invalid value ");
                }
                if ((time_scale = bitBuf.read(32)) == 0) {
                    Log.w(TAG, "time_scale = 0, invalid value");
                }
                fixed_frame_rate_flag = bitBuf.read();
                Log.i(TAG, String.format("num_units_in_tick = %d, time_scale = %d, fixed_frame_rate_flag = %d",
                        num_units_in_tick, time_scale, fixed_frame_rate_flag));

                if (num_units_in_tick != 0) {
                    int frameRate = (time_scale + num_units_in_tick) / (num_units_in_tick * 2);
                    fmt.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
                    fmt.setInteger(MediaFormat.KEY_FRAME_RATE_ACCURACY, 100);
                }
            }
        }
        return 0;
    }

    @Override
    int locateFrame(final byte[] data, int offset, int size, Frame frm) {
        Log.d(TAG, "locating frame : size " + size);
        return locateFrameH264(data, offset, size, frm);

    }

    String mName;
    @Override
    void parseMediaFormat(final Frame frm) {
        // LOGI("h264 parse codec info frm data %p frm size %d 0x%02x 0x%02x 0x%02x",
        // frm.mData, frm.getSize(), frm.mData[0], frm.mData[1], frm.mData[2]);
        final byte[] data = frm.mBuffer;
        int sp = frm.mData;
        int ep = sp + frm.mSize;
        while (ep - sp > 4) {
            if ((sp = findStartCode(frm.mBuffer, sp, ep)) < 0) {
                Log.w(TAG, "could not find start code");
                return;
            }
            int nalType = data[sp + 3] & 0x1F;
            Log.d(TAG, "nalType = " + nalType);
            if (nalType == H264NalSPS || nalType == H264NalPPS) {
                break;
            }
            sp += 3;
        }
        if (ep - sp <= 6) {
            Log.w(TAG, "could not find SPS object");
            return;
        }
        Log.i(TAG, "H264 parse codec info");
        MediaFormat fmt = new MediaFormat();
        fmt.setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_VIDEO_AVC);
        fmt.setInteger(MediaFormat.KEY_FOURCC, Mp4Descriptors.fourcc("H264"));
        mName = "H264/AVC Video ISO/IEC14496-10 Annex B";
        fmt.setString(MediaFormat.KEY_DESCRIPTION, mName);
        decodeSps(fmt, data, sp + 4, ep - sp - 4);
        int frameRate = fmt.getInteger(MediaFormat.KEY_FRAME_RATE, 0);
        if (frameRate == 0 && mNumDetectPts > 0 && mDetectPtsDeltas > 0) {
            frameRate = (int) ((1000000L * mNumDetectPts + mDetectPtsDeltas / 2) / mDetectPtsDeltas);
            if (frameRate > 5 && frameRate < 100) {
                fmt.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
                fmt.setInteger(MediaFormat.KEY_FRAME_RATE_ACCURACY, 70);
                Log.i(TAG, String.format("use detected frame rate (%d) from PTS", frameRate));
            }
        }

        if (frameRate == 0) {
            fmt.setInteger(MediaFormat.KEY_FRAME_RATE, 24);
            fmt.setInteger(MediaFormat.KEY_FRAME_RATE_ACCURACY, 20);
            Log.i(TAG, "Could not find Frame rate information from SPS, use default frame rate (24)");
        }
        mMediaFormat = fmt;
        Log.i(TAG, "H264 format = " + fmt);
    }

    boolean mLastWasVlc = false;

    boolean isAccessUnitStart(final byte[] data, int offset, int[] frmFlag) {
        int nalType = data[offset] & 0x1F;
        boolean lastWasVlc = mLastWasVlc;

        if (nalType == H264NalSliceIDR) {
            frmFlag[0] |= Frame.FLAG_IFRAME;
        } else if (nalType == H264NalSPS || nalType == H264NalPPS) {
            frmFlag[0] |= Frame.FLAG_CODEC_CONF;
        }

        if ((nalType >= H264NalSlice) && (nalType <= H264NalSliceIDR)) {
            mLastWasVlc = true;
            /*
             * if first bit is '1' means first_mb_in_slice is zero, 
             * which means it is the first slice in picture
             */
            return (lastWasVlc && ((data[offset + 1] & 0x80) != 0));
        } else {
            if (nalType == H264NalFilterData || nalType == H264NalSeqEnd) {
                return false;
            }
            mLastWasVlc = false;
            return (lastWasVlc)
                    && (((nalType >= H264NalSEI) && (nalType <= H264NalPPS)) || ((nalType >= 14) && (nalType <= 18)) || (nalType == H264NalAuDelimiter));
        }
    }

    @Override
    public String toString() {
        if (mName != null) {
            return mName;
        }
        return "H264Stream";
    }

}
