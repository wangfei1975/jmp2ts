package com.wterry.jmpegts.com.wterry.jmpegts.parser;

public final class BitBufferReader extends BufferWrapper {

    public BitBufferReader(byte[] data, int offset, int size) {
        super(data, offset, size);
    }

    void skip(int bits) {
        // TODO check
        mPos += bits;
    }

    int read() {
        // TODO check
        int byteoff = mPos >> 3;
        int bitoff = mPos & 0x07;
        mPos++;
        return ((mData[byteoff] >> (7 - bitoff)) & 0x01);
    }

    long readExpGolomb() {
        int leadingZeroBits = 0;
        while (read() == 0 && leadingZeroBits < 32) {
            leadingZeroBits++;
        }
        long code = (1L << leadingZeroBits) - 1 + read(leadingZeroBits);
        return code;
    }

    long readExpGolombSigned() {
        long ue = readExpGolomb();
        return (ue & 0x01) != 0 ? (ue + 1) / 2 : -(ue / 2);
    }

    long readLong(int size) {
        if (size <= 32) {
            return read(size);
        }
        long r = (((long) read(32)) << (size - 32));
        return r | read(size - 32);
    }

    // size <= 32
    // read bits
    int read(int size) {
        // TODO check
        int byteoff = mPos >> 3;
        int bitoff = mPos & 0x07;

        mPos += size;
        if (8 - bitoff >= size) {
            return ((mData[byteoff] << bitoff)&0xFF) >> (8 - size);
        }

        int r = ((mData[byteoff++] << bitoff)&0xFF) >> bitoff;
        size -= (8 - bitoff);

        while (size > 8) {
            r = (r << 8) | (mData[byteoff++]&0xFF);
            size -= 8;
        }
        r <<= size;
        r |= ((mData[byteoff]&0xFF) >> (8 - size));
        return r;
    }
}
