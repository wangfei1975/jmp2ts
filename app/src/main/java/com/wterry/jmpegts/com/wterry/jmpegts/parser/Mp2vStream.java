package com.wterry.jmpegts.com.wterry.jmpegts.parser;

import android.provider.MediaStore;

/**
 * Created by Fei Wang on 2/18/2016.
 */

public class Mp2vStream extends VideoStream {
    static final int [] fpsNum = {0, 24000, 24, 25, 30000, 30, 50, 60000, 60};
    static final int [] fpsDen = {0, 1001, 1, 1, 1001, 1, 1, 1001, 1};

    Mp2vStream(ParserImpl tp, int pid, int streamType) {
        super(tp, pid, streamType);
    }

    int locateFrame(final byte [] buffer, int startp, int size,  Frame  outFrame) {
            int flag = 0;
            int  fstart, fend;
            final int endp = startp + size;
            outFrame.mData = -1;
            outFrame.mFlag = 0;
            outFrame.mSize = 0;
            if ((fstart = findStartCode(buffer, startp, endp)) < 0) {
                return -1;
            }
            int code = buffer[fstart+3]&0xFF;
            if (code == 0xB3) {
                flag |= Frame.FLAG_CODEC_CONF;
            } else if (code == 0x00) {
                if (((buffer[fstart+4]>>3)&0x07) == 1) {
                    flag |= Frame.FLAG_IFRAME;
                }
            }
            outFrame.mData = fstart;
            while((fend = findStartCode(buffer, fstart+1, endp)) >= 0) {
                code = buffer[fstart+3]&0xFF;
                if (code == 0xB3) {
                    flag |= Frame.FLAG_CODEC_CONF;
                } else if (code == 0x00) {
                    int iflag = buffer[fstart+5]&0xFF;
                    if (((iflag>>3)&0x07) == 1) {
                        flag |= Frame.FLAG_IFRAME;
                    }
                }
                if (fend + 3 < endp) {
                    if (buffer[fend+3] == 0 && flag != Frame.FLAG_CODEC_CONF) {
                        break;
                    }
                }
                fstart = fend;
            }

            outFrame.mFlag = flag;
            if (fend >= 0) {
                outFrame.mSize = fend - outFrame.mData;
                // LOGI("got frame size = %d", outFrame.mSize);
                return 1;
            }
            return 0;
        }
    @Override
    void parseMediaFormat(final Frame frm) {
        final byte [] buffer = frm.getBuffer();
        int frame = frm.getOffset();
        int endp = frame + frm.getSize();
        while(frame >= 0 && frame < endp - 4) {
            if (buffer[frame] == 0 && buffer[frame+1] == 0 && buffer[frame+2] == 1 && (buffer[frame+3]&0xFF) == 0xB3) {
                break;
            }
            frame = findStartCode(buffer, frame + 1, endp);
        }
        if (frame < 0) {
            Log.w(TAG, "could not find codec conf data");
            return ;
        }
        Log.i(TAG, "H264 parse codec info");
        MediaFormat fmt = new MediaFormat();
        fmt.setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_VIDEO_MPEG2);
        String mName = "MPEG 2 Video";
        fmt.setString(MediaFormat.KEY_DESCRIPTION, mName);
        fmt.setInteger(MediaFormat.KEY_FOURCC, Mp4Descriptors.fourcc("MP2V"));

        int width = ((buffer[frame+4]&0xFF) << 4) | ((buffer[frame+5]&0xF0) >> 4);
        int heigh = ((buffer[frame+5]&0x0F) << 8) | (buffer[frame+6]&0xFF);
        fmt.setInteger(MediaFormat.KEY_WIDTH, width);
        fmt.setInteger(MediaFormat.KEY_HEIGHT, heigh);

        int aspectRatio = (buffer[frame+7]&0xF0) >> 4;
        int fpsidx = buffer[frame+7] & 0x0F;
        int bitrate = ((buffer[frame+8]&0xFF) << 10) | ((buffer[frame+9]&0xFF) << 2) | ((buffer[frame+10]&0xFF) >> 6);

        int fps = VIDEO_DEFAULT_FPS;
        int frameRateAccuracy = VIDEO_DEFAULT_FPSACC;

        if (fpsidx > 0 && fpsidx < 9) {
            fps = (fpsNum[fpsidx] + fpsDen[fpsidx] - 1)/fpsDen[fpsidx];
            frameRateAccuracy = 100;
        }
        fmt.setInteger(MediaFormat.KEY_FRAME_RATE, fps);
        fmt.setInteger(MediaFormat.KEY_FRAME_RATE_ACCURACY, frameRateAccuracy);
        fmt.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);

        Log.i(TAG, String.format("Got MPEG 2 Video Fourcc(MP2V), width(%d) heigh(%d), Aspect ratio(%d), Frame rate(%d), Bit rate(%d)",
                width, heigh, aspectRatio, fps, bitrate * 400));
        this.mMediaFormat = fmt;

    }

}
