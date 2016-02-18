package com.wterry.jmpegts.com.wterry.jmpegts.parser;

/**
 * Created by Fei Wang on 2/18/2016.
 */

public class Mp2vStream extends VideoStream {

        int locateFrame(final byte [] buffer, int startp, int size,  Frame  outFrame) {
            int flag = 0, nflg = 0;
            int  fstart, fend;
            final int endp = startp + size;
            outFrame.mData = -1;
            outFrame.mFlag = 0;
            outFrame.mSize = 0;
            if ((fstart = findStartCode(buffer, startp, endp)) < 0) {
                return -1;
            }
            outFrame.mData = fstart;
            while((fend = findStartCode(buffer, fstart+1, endp)) >= 0) {
                final byte code = buffer[fstart+3];
                if (code == 0xB3) {
                    flag |= Frame.FLAG_CODEC_CONF;
                } else if (code == 0x00) {
                    if (((buffer[fstart+4]>>3)&0x07) == 1) {
                        flag |= Frame.FLAG_IFRAME;
                    }
                }
                if (buffer[fend+3] == 0) {
                    break;
                }
                fstart = fend;
            }

            outFrame.mFlag = flag;
            if (fend) {
                outFrame.mSize = fend - outFrame.mData;
                // LOGI("got frame size = %d", outFrame.mSize);
                return 1;
            }
            return 0;
        }

    @Override
    void parseMediaFormat(Frame frm) {

    }

    virtual CodecInfo * parseCodecInfo(const Frame & frm) const {
            const uint8_t * frame = frm.getData();
            const uint8_t * endp = frame + frm.getSize();
            while(frame != NULL && frame < endp - 4) {
                if (frame[0] == 0 && frame[1] == 0 && frame[2] == 1 && frame[3] == 0xB3) {
                    break;
                }
                frame = VideoStream::findStartCode(frame + 1, endp);
            }
            if (frame == NULL) {
                return NULL;
            }
            CodecInfo * info =  new CodecInfo();
            info->mName = "MPEG 2 Video";
            info->mFourcc = FOURCC("MP2V");

            info->mWidth = (frame[4] << 4) | (frame[5] >> 4);
            info->mHeigh = (frame[5] << 8) | frame[6];

            info->mAspectRatio = frame[7] >> 4;
            int32_t fpsidx = frame[7] & 0x0F;
            info->mBitrate = (frame[8] << 10) | (frame[9] << 2) | (frame[10] >> 6);


            static const uint32_t fpsNum[9] = {0, 24000, 24, 25, 30000, 30, 50, 60000, 60};
            static const uint32_t fpsDen[9] = {0, 1001, 1, 1, 1001, 1, 1, 1001, 1};
            if (fpsidx > 0 && fpsidx < 9) {
                info->mFrameRate = (fpsNum[fpsidx] + fpsDen[fpsidx] - 1)/fpsDen[fpsidx];
                info->mFrameRateAccuracy = 100;
            } else {
                info->mFrameRate = VIDEO_DEFAULT_FPS;
                info->mFrameRateAccuracy = VIDEO_DEFAULT_FPSACC;
            }
            LOGI("Got MPEG 2 Video Fourcc(MP2V), width(%d) heigh(%d), Aspect ratio(%d), Frame rate(%d), Bit rate(%d)",
                    info->mWidth, info->mHeigh, info->mAspectRatio, info->mFrameRate, info->mBitrate * 400);

            return info;
        }

        static const uint8_t * findFrameStart(const uint8_t * start, const uint8_t * endp, uint32_t * frameFlag) {
            assert(endp > start);
            if (endp - start < 4) {
                return NULL;
            }
            uint32_t code = *start++;
            code = (code << 8) | * start++;
            code = (code << 8) | * start++;
            while(start < endp) {
                code = (code << 8) | * start++;
                if (code == 0x000001B3) {
                    *frameFlag |= IFrame::FLAG_CODEC_CONF;
                    return start-4;
                } else if (code == 0x00000100) {
                    if (((start[1]>>3) & 0x07) == 1) {
                        *frameFlag |= IFrame::FLAG_IFRAME;
                    }
                    return start-4;
                }
            }
            return NULL;
        }
        public:
        Mp2vStream(ParserImpl * tsParser, uint16_t pid, uint8_t streamType, const uint8_t * info, int32_t infoSize) :
        VideoStream(tsParser, pid, streamType, info, infoSize) {
        }

        virtual int32_t getRequiredBufferSize() const {
            int32_t s1 = mMaxFrameSize * 6;
            int32_t s2 = VideoStream::getRequiredBufferSize();
            return s1 > s2 ? s1: s2;
        }
    };
}
}
