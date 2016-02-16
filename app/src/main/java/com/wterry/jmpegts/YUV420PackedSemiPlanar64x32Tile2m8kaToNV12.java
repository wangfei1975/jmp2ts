/**
 * Created by feiwang on 15-12-11.
 */

package com.wterry.jmpegts;
public final class YUV420PackedSemiPlanar64x32Tile2m8kaToNV12 {
    static final int Scan_Init = 0;
    static final int Scan_Hor = 1;
    static final int Scan_VerDown = 2;
    static final int Scan_VerUp = 3;
    //static final int MAX_RESOLUTION_X = 1920; // 4096
    //static final int MAX_RESOLUTION_Y = 1088;// // 3072
    //static final int MAX_TILES_NUM = (((MAX_RESOLUTION_X + 63) >> 6) * ((MAX_RESOLUTION_Y + 31) >> 5));

    static final class MbGroup {
        int startMbIndex;
        int numMBs;
        boolean lastTileInVer;
    };

    static int align(int x, int a) {
        return (x + a - 1) & (~(a - 1));
    }

    MbGroup[] myTileToMb;
    MbGroup[] muvTileToMb;

    void initTileToMbs(int width, int height) {

        int srcStrideY = align(width, 128);
        int srcHeightY = align(height, 32);
        int srcStrideUV = srcStrideY; // v/u interlaced
        int srcHeightUV = align((height >> 1), 32);
        int wTiles = (width + 63) >> 6;
        int hTilesY = (height + 31) >> 5;
        int hTilesUV = (height / 2 + 31) >> 5;
        int numTilesY = wTiles * hTilesY;
        int numTilesUV = wTiles * hTilesUV;
        int wMacroblocks = (width + 15) >> 4;
        int hMacroblocks = (height + 15) >> 4;
        int numMbInTile = 4 * 2; // y: 4*2; uv: 4*4
        int mbOffsetTileHor = 4;
        int mbOffsetTileVer = (wMacroblocks << 1); // y: (wMacroblocks<<1); uv:
        // (wMacroblocks<<2)
        myTileToMb = new MbGroup[numTilesY];// = {0}; // each Tile index storing
        // according MB index
        muvTileToMb = new MbGroup[numTilesUV];// = {0}; // each Tile index
        // storing according MB index

        for (int i = 0; i < myTileToMb.length; i++) {
            myTileToMb[i] = new MbGroup();
        }
        for (int i = 0; i < muvTileToMb.length; i++) {
            muvTileToMb[i] = new MbGroup();
        }
        MbGroup[] yTileToMb = myTileToMb;
        MbGroup[] uvTileToMb = muvTileToMb;
        int availableTilesY = numTilesY;
        int availableTilesUV = numTilesUV;
        int numTilesYPerScanUnit = (wTiles << 1);
        int tileIndex = 0;
        int preMode = Scan_Init;
        int curMode = Scan_Hor;
        int scanedTiles = 0;
        int hMbMultiple = 0;
        int cntScanTimesInPeriod = 0; // maximal scan times is up to 4
        int cnt1stLineTiles = 0;
        int cnt2ndLineTiles = 0;
        int mbPosition = 0;
        int lastMbIdx = mbPosition;
        int firstMbIdxUnit = mbPosition;
        boolean noEnoughMbInTile = false;

        // construct yTileToMb table
        while (availableTilesY > 0) {
            if (availableTilesY >= numTilesYPerScanUnit) {
                preMode = Scan_Init;
                curMode = Scan_Hor;
                lastMbIdx = mbPosition;
                firstMbIdxUnit = mbPosition;
                cntScanTimesInPeriod = 0; // maximal scan times is up to 4
                cnt1stLineTiles = 0;
                cnt2ndLineTiles = 0;
                noEnoughMbInTile = false;
                scanedTiles = 0;

                while (scanedTiles < numTilesYPerScanUnit) {
                    if ((tileIndex & 3) == 0) {
                        firstMbIdxUnit = mbPosition;
                    }
                    noEnoughMbInTile = false;
                    if (curMode == Scan_Hor) {
                        if ((preMode == Scan_VerUp && cnt1stLineTiles + 1 >= wTiles)
                                || (preMode == Scan_VerDown && cnt2ndLineTiles + 1 >= wTiles)) {
                            noEnoughMbInTile = (align(width, 16) < srcStrideY);
                            // yTileToMb[tileIndex].lastTileInHor = true;
                        }
                        yTileToMb[tileIndex].startMbIndex = mbPosition;
                        yTileToMb[tileIndex].numMBs = noEnoughMbInTile ? ((4 - ((srcStrideY - align(width, 16)) >> 4)) << 1)
                                : numMbInTile;
                        ++tileIndex;
                        ++cntScanTimesInPeriod;
                        if (noEnoughMbInTile && cntScanTimesInPeriod == 1) {
                            if (preMode == Scan_VerDown) {
                                preMode = curMode; // scan mode change need
                                // upate preMode
                                curMode = Scan_VerUp;
                                ++cnt2ndLineTiles;
                                mbPosition = firstMbIdxUnit - mbOffsetTileVer;
                            } else if (preMode == Scan_VerUp) {
                                preMode = curMode; // scan mode change need
                                // upate preMode
                                curMode = Scan_VerDown;
                                ++cnt1stLineTiles;
                                mbPosition = firstMbIdxUnit + mbOffsetTileVer;
                            }
                        } else if (cntScanTimesInPeriod == 2) {
                            if (preMode == Scan_Init || preMode == Scan_VerUp) {
                                preMode = curMode; // scan mode change need
                                // upate preMode
                                curMode = Scan_VerDown;
                                ++cnt1stLineTiles;
                                mbPosition = firstMbIdxUnit + mbOffsetTileVer;
                            } else if (preMode == Scan_VerDown) {
                                preMode = curMode; // scan mode change need
                                // upate preMode
                                curMode = Scan_VerUp;
                                ++cnt2ndLineTiles;
                                mbPosition = firstMbIdxUnit - mbOffsetTileVer;
                            }
                        } else if (cntScanTimesInPeriod == 4) {
                            if (preMode == Scan_VerDown) {
                                ++cnt2ndLineTiles;
                                mbPosition += mbOffsetTileHor;
                            } else if (preMode == Scan_VerUp) {
                                ++cnt1stLineTiles;
                                mbPosition += mbOffsetTileHor;
                            }
                        } else {
                            if (preMode == Scan_Init) {
                                ++cnt1stLineTiles;
                            } else if (preMode == Scan_VerDown) {
                                ++cnt2ndLineTiles;
                            } else if (preMode == Scan_VerUp) {
                                ++cnt1stLineTiles;
                            }
                            if (cnt2ndLineTiles >= wTiles && preMode == Scan_VerDown) {
                                preMode = curMode; // scan mode change need
                                // upate preMode
                                curMode = Scan_VerUp;
                                mbPosition = firstMbIdxUnit - mbOffsetTileVer;
                            } else if (cnt1stLineTiles >= wTiles && preMode == Scan_VerUp) {
                                preMode = curMode; // scan mode change need
                                // upate preMode
                                curMode = Scan_VerDown;
                                mbPosition = firstMbIdxUnit + mbOffsetTileVer;
                            } else {
                                mbPosition += mbOffsetTileHor;
                            }
                        }
                    } else if (curMode == Scan_VerUp) {
                        if (cnt1stLineTiles + 1 >= wTiles) {
                            noEnoughMbInTile = (align(width, 16) < srcStrideY);
                            // yTileToMb[tileIndex].lastTileInHor = true;
                        }
                        yTileToMb[tileIndex].startMbIndex = mbPosition;
                        yTileToMb[tileIndex].numMBs = noEnoughMbInTile ? ((4 - ((srcStrideY - align(width, 16)) >> 4)) << 1)
                                : numMbInTile;
                        ++tileIndex;
                        mbPosition += mbOffsetTileHor;
                        ++cntScanTimesInPeriod;
                        ++cnt1stLineTiles;
                        preMode = curMode; // scan mode change need upate
                        // preMode
                        curMode = Scan_Hor;
                    } else if (curMode == Scan_VerDown) {
                        if (cnt2ndLineTiles + 1 >= wTiles) {
                            noEnoughMbInTile = (align(width, 16) < srcStrideY);
                            // yTileToMb[tileIndex].lastTileInHor = true;
                        }
                        yTileToMb[tileIndex].startMbIndex = mbPosition;
                        yTileToMb[tileIndex].numMBs = noEnoughMbInTile ? ((4 - ((srcStrideY - align(width, 16)) >> 4)) << 1)
                                : numMbInTile;
                        ++tileIndex;
                        mbPosition += mbOffsetTileHor;
                        ++cntScanTimesInPeriod;
                        ++cnt2ndLineTiles;
                        preMode = curMode; // scan mode change need upate
                        // preMode
                        curMode = Scan_Hor;
                    }
                    cntScanTimesInPeriod &= 0x03;
                    ++scanedTiles;
                }
                mbPosition = lastMbIdx + (mbOffsetTileVer << 1);
                availableTilesY -= numTilesYPerScanUnit;
            } else {
                scanedTiles = 0;
                hMbMultiple = hMacroblocks - (tileIndex / wTiles) * 2;
                noEnoughMbInTile = false;
                while (scanedTiles < wTiles) {
                    yTileToMb[tileIndex].startMbIndex = mbPosition;
                    yTileToMb[tileIndex].lastTileInVer = true;
                    if (scanedTiles + 1 == wTiles) {
                        noEnoughMbInTile = (align(width, 16) < srcStrideY);
                        // yTileToMb[tileIndex].lastTileInHor = true;
                    }
                    yTileToMb[tileIndex].numMBs = noEnoughMbInTile ? ((4 - ((srcStrideY - align(width, 16)) >> 4)) * hMbMultiple)
                            : (4 * hMbMultiple);
                    ++tileIndex;
                    mbPosition += mbOffsetTileHor;
                    ++scanedTiles;
                }
                availableTilesY -= wTiles;
            }
        }

        numMbInTile = 4 * 4;
        mbOffsetTileVer = (wMacroblocks << 2);
        mbPosition = 0;
        tileIndex = 0;
        // construct uvTileToMb table
        while (availableTilesUV > 0) {
            if (availableTilesUV >= numTilesYPerScanUnit) {
                preMode = Scan_Init;
                curMode = Scan_Hor;
                lastMbIdx = mbPosition;
                firstMbIdxUnit = mbPosition;
                cntScanTimesInPeriod = 0; // maximal scan times is up to 4
                cnt1stLineTiles = 0;
                cnt2ndLineTiles = 0;
                noEnoughMbInTile = false;
                scanedTiles = 0;

                while (scanedTiles < numTilesYPerScanUnit) {
                    if ((tileIndex & 3) == 0) {
                        firstMbIdxUnit = mbPosition;
                    }
                    noEnoughMbInTile = false;
                    if (curMode == Scan_Hor) {
                        if ((preMode == Scan_VerUp && cnt1stLineTiles + 1 >= wTiles)
                                || (preMode == Scan_VerDown && cnt2ndLineTiles + 1 >= wTiles)) {
                            noEnoughMbInTile = (align(width, 16) < srcStrideY);
                            // uvTileToMb[tileIndex].lastTileInHor = true;
                        }
                        uvTileToMb[tileIndex].startMbIndex = mbPosition;
                        uvTileToMb[tileIndex].numMBs = noEnoughMbInTile ? ((4 - ((srcStrideY - align(width, 16)) >> 4)) << 2)
                                : numMbInTile;
                        ++tileIndex;
                        ++cntScanTimesInPeriod;
                        if (noEnoughMbInTile && cntScanTimesInPeriod == 1) {
                            if (preMode == Scan_VerDown) {
                                preMode = curMode; // scan mode change need
                                // upate preMode
                                curMode = Scan_VerUp;
                                ++cnt2ndLineTiles;
                                mbPosition = firstMbIdxUnit - mbOffsetTileVer;
                            } else if (preMode == Scan_VerUp) {
                                preMode = curMode; // scan mode change need
                                // upate preMode
                                curMode = Scan_VerDown;
                                ++cnt1stLineTiles;
                                mbPosition = firstMbIdxUnit + mbOffsetTileVer;
                            }
                        } else if (cntScanTimesInPeriod == 2) {
                            if (preMode == Scan_Init || preMode == Scan_VerUp) {
                                preMode = curMode; // scan mode change need
                                // upate preMode
                                curMode = Scan_VerDown;
                                ++cnt1stLineTiles;
                                mbPosition = firstMbIdxUnit + mbOffsetTileVer;
                            } else if (preMode == Scan_VerDown) {
                                preMode = curMode; // scan mode change need
                                // upate preMode
                                curMode = Scan_VerUp;
                                ++cnt2ndLineTiles;
                                mbPosition = firstMbIdxUnit - mbOffsetTileVer;
                            }
                        } else if (cntScanTimesInPeriod == 4) {
                            if (preMode == Scan_VerDown) {
                                ++cnt2ndLineTiles;
                                mbPosition += mbOffsetTileHor;
                            } else if (preMode == Scan_VerUp) {
                                ++cnt1stLineTiles;
                                mbPosition += mbOffsetTileHor;
                            }
                        } else {
                            if (preMode == Scan_Init) {
                                ++cnt1stLineTiles;
                            } else if (preMode == Scan_VerDown) {
                                ++cnt2ndLineTiles;
                            } else if (preMode == Scan_VerUp) {
                                ++cnt1stLineTiles;
                            }
                            if (cnt2ndLineTiles >= wTiles && preMode == Scan_VerDown) {
                                preMode = curMode; // scan mode change need
                                // upate preMode
                                curMode = Scan_VerUp;
                                mbPosition = firstMbIdxUnit - mbOffsetTileVer;
                            } else if (cnt1stLineTiles >= wTiles && preMode == Scan_VerUp) {
                                preMode = curMode; // scan mode change need
                                // upate preMode
                                curMode = Scan_VerDown;
                                mbPosition = firstMbIdxUnit + mbOffsetTileVer;
                            } else {
                                mbPosition += mbOffsetTileHor;
                            }
                        }
                    } else if (curMode == Scan_VerUp) {
                        if (cnt1stLineTiles + 1 >= wTiles) {
                            noEnoughMbInTile = (align(width, 16) < srcStrideY);
                            // uvTileToMb[tileIndex].lastTileInHor = true;
                        }
                        uvTileToMb[tileIndex].startMbIndex = mbPosition;
                        uvTileToMb[tileIndex].numMBs = noEnoughMbInTile ? ((4 - ((srcStrideY - align(width, 16)) >> 4)) << 2)
                                : numMbInTile;
                        ++tileIndex;
                        mbPosition += mbOffsetTileHor;
                        ++cntScanTimesInPeriod;
                        ++cnt1stLineTiles;
                        preMode = curMode; // scan mode change need upate
                        // preMode
                        curMode = Scan_Hor;
                    } else if (curMode == Scan_VerDown) {
                        if (cnt2ndLineTiles + 1 >= wTiles) {
                            noEnoughMbInTile = (align(width, 16) < srcStrideY);
                            // uvTileToMb[tileIndex].lastTileInHor = true;
                        }
                        uvTileToMb[tileIndex].startMbIndex = mbPosition;
                        uvTileToMb[tileIndex].numMBs = noEnoughMbInTile ? ((4 - ((srcStrideY - align(width, 16)) >> 4)) << 2)
                                : numMbInTile;
                        ++tileIndex;
                        mbPosition += mbOffsetTileHor;
                        ++cntScanTimesInPeriod;
                        ++cnt2ndLineTiles;
                        preMode = curMode; // scan mode change need upate
                        // preMode
                        curMode = Scan_Hor;
                    }
                    cntScanTimesInPeriod &= 0x03;
                    ++scanedTiles;
                }
                mbPosition = lastMbIdx + (mbOffsetTileVer << 1);
                availableTilesUV -= numTilesYPerScanUnit;
            } else {
                scanedTiles = 0;
                hMbMultiple = hMacroblocks - (tileIndex / wTiles) * 4;
                noEnoughMbInTile = false;
                while (scanedTiles < wTiles) {
                    uvTileToMb[tileIndex].startMbIndex = mbPosition;
                    uvTileToMb[tileIndex].lastTileInVer = true;
                    if (scanedTiles + 1 == wTiles) {
                        noEnoughMbInTile = (align(width, 16) < srcStrideY);
                        // uvTileToMb[tileIndex].lastTileInHor = true;
                    }
                    uvTileToMb[tileIndex].numMBs = noEnoughMbInTile ? ((4 - ((srcStrideY - align(width, 16)) >> 4)) * hMbMultiple)
                            : (4 * hMbMultiple);
                    ++tileIndex;
                    mbPosition += mbOffsetTileHor;
                    ++scanedTiles;
                }
                availableTilesUV -= wTiles;
            }
        }

    }

    public YUV420PackedSemiPlanar64x32Tile2m8kaToNV12(int w, int h) {
        initTileToMbs(w, h);
        mWidth = w;
        mHeight = h;
    }

    final int mWidth;
    final int mHeight;

    public void convert(byte[] src, byte[] dst) {
        int stride_y = mWidth;
        int stride_u = mWidth/2;
        int srcStrideY = align(mWidth, 128);
        int srcHeightY = align(mHeight, 32);
        int srcStrideUV = srcStrideY; // v/u interlaced
        int srcHeightUV = align((mHeight >> 1), 32);
        int srcSizeY = align((srcStrideY * srcHeightY), 8192);
        int srcSizeUV = align((srcStrideUV * srcHeightUV), 8192);

        byte[] src_y = src;
        int src_uv_off = srcSizeY;
        int uoffset = mWidth * mHeight;
        //int voffset = uoffset + srcSizeY / 4;

        int wTiles = (mWidth + 63) >> 6;
        int hTilesY = (mHeight + 31) >> 5;
        int hTilesUV = (mHeight / 2 + 31) >> 5;
        int numTilesY = wTiles * hTilesY;
        int numTilesUV = wTiles * hTilesUV;
        int wMacroblocks = (mWidth + 15) >> 4;
        int hMacroblocks = (mHeight + 15) >> 4;
        int numMbInTile = 4 * 2; // y: 4*2; uv: 4*4
        int mbOffsetTileHor = 4;
        int mbOffsetTileVer = (wMacroblocks << 1); // y: (wMacroblocks<<1); uv:
        // (wMacroblocks<<2)

        MbGroup[] yTileToMb = myTileToMb;// new MbGroup[MAX_TILES_NUM];// = {0};
        // // each Tile index storing according
        // MB index
        MbGroup[] uvTileToMb = muvTileToMb;// new MbGroup[MAX_TILES_NUM];// =
        // {0}; // each Tile index storing
        // according MB index

        byte[] py = src_y;
        int py_offset = 0;
        int tileIndex = 0;
        // converting luma componet with yTileToMb
        while (tileIndex < numTilesY) {
            int startMbIndex = yTileToMb[tileIndex].startMbIndex;
            final int startMbX = (startMbIndex % wMacroblocks);
            final int startMbY = (startMbIndex / wMacroblocks);

            int mb_x = startMbX;
            int mb_y = startMbY;
            final int cntMbLines = yTileToMb[tileIndex].lastTileInVer ? (hMacroblocks - (tileIndex / wTiles) * 2) : 2;
            final int numMbPerLine = yTileToMb[tileIndex].numMBs / cntMbLines;
            final int sizePixelLine = (numMbPerLine << 4);
            int mbLine = 0;
            while (mbLine < cntMbLines) {
                // assert( mb_y < hMacroblocks && mb_x < wMacroblocks );
                final int dstOffsetY = (mb_y * stride_y + mb_x) << 4;
                int _l = 0;
                // luma

                while (_l < 16) {
                    // memcpy( dst_y + dstOffsetY + _l * stride_y, py,
                    // sizePixelLine );
                    System.arraycopy(py, py_offset, dst, dstOffsetY + _l * stride_y, sizePixelLine);
                    py_offset += 64; // eliminate padding (64-sizePixelLine)
                    ++_l;
                }
                mb_x = startMbX;
                ++mb_y;
                ++mbLine;
            }
            ++tileIndex;
        }

        int puv = src_uv_off;
        tileIndex = 0;
        // convering cb/cr componets with uvTileToMb
        while (tileIndex < numTilesUV) {
            int startMbIndex = uvTileToMb[tileIndex].startMbIndex;
            final int startMbX = (startMbIndex % wMacroblocks);
            final int startMbY = (startMbIndex / wMacroblocks);

            int mb_x = startMbX;
            int mb_y = startMbY;
            final int cntMbLines = uvTileToMb[tileIndex].lastTileInVer ? (hMacroblocks - (tileIndex / wTiles) * 4) : 4;
            final int numMbPerLine = uvTileToMb[tileIndex].numMBs / cntMbLines;
            int mbLine = 0;
            while (mbLine < cntMbLines) {
                // assert( mb_y < hMacroblocks && mb_x < wMacroblocks );
                // cb/cr
                int mbIndex = 0;
                while (mbIndex < numMbPerLine) {
                    // assert( mb_y < hMacroblocks && mb_x < wMacroblocks );
                    final int dstOffsetUV = (mb_y * stride_u + mb_x) << 3;
                    int _l = 0;
                    while (_l < 8) {
                        int _offset = dstOffsetUV + _l * stride_u;
                        int _u = uoffset + _offset*2;
                        //int _v = voffset + _offset*2;
                        int _src_vu_off = puv + (mbIndex << 4) + (_l << 6);
                        int _interlace = 0;
                        for (int ichroma = 0; ichroma < 16;  ichroma+=2) {
                            dst[_u + ichroma] = src[_src_vu_off + _interlace++];
                            dst[_u + ichroma+1] = src[_src_vu_off + _interlace++];
                        }
                        ++_l;
                    }
                    ++mb_x;
                    ++mbIndex;
                }
                puv += 64 * 8;
                mb_x = startMbX;
                ++mb_y;
                ++mbLine;
            }
            if (cntMbLines < 4) {
                puv += 64 * (4 - cntMbLines) * 8;
            }
            ++tileIndex;
        }

    }
}