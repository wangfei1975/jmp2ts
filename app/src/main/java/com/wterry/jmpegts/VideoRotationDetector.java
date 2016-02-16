package com.wterry.jmpegts;

import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.os.Build;

import java.io.IOException;

/**
 * Created by Fei Wang on 11/6/2015.
 */
public final class VideoRotationDetector {

    int mVideoRotation = 0;
    public final int detect(String videoFile) {
        MediaMetadataRetriever m = new MediaMetadataRetriever();
        m.setDataSource(videoFile);
        String sRot = null;
        if (Build.VERSION.SDK_INT >= 17) {
            sRot = m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
        } else {
            sRot = m.extractMetadata(24);
        }
        if (sRot != null) {
            try {
                int rot = Integer.parseInt(sRot);
                if (rot == 0 || rot == 90 || rot == 180 || rot == 270) {
                    return rot;
                }
            } catch (NumberFormatException e) {
            }
        }
        m.release();
        MediaPlayer mp = new MediaPlayer();
        try {
            mp.setDataSource(videoFile);
            mp.setOnVideoSizeChangedListener(new MediaPlayer.OnVideoSizeChangedListener() {
                @Override
                public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
                    if (width < height) {
                        mVideoRotation = 90;
                        synchronized (this) {
                            this.notifyAll();
                        }
                    }
                }
            });
            mp.prepare();
            synchronized (this) {
                try {
                    this.wait(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        mp.release();
        return mVideoRotation;
    }
}
