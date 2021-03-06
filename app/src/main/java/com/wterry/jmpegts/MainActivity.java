package com.wterry.jmpegts;

import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;

import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    TextureView mTextureView;
    Surface mSurface;

    VideoDecoder mDecoder;

    MptsVideoDecoder_o mMp2tsDecoder;

    static final String ts1 = "/sdcard/BigBunny_mp2v_mpga_480p_20.ts";
    static final String ts2 = "/sdcard/BigBunny_h264_aac_480p_20.ts";



    void playWithVideoDecoder() {
        try {
            mDecoder = new VideoDecoder(ts1, mSurface);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                    do {
                        int idx = mDecoder.dequeueOutputBuffer(-1, info);
                        if (idx >= 0) {
                            mDecoder.releaseOutputBuffer(idx, true);
                        }
                    } while((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) == 0);

                }
            }).start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    void playWithMptsVideoDecodero() {
        try {
            final  MptsVideoDecoder_o  decoder_o = new MptsVideoDecoder_o(ts1, mSurface);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                    do {
                        int idx = 0;

                        try {
                            idx = decoder_o.dequeueOutputBuffer(10, info);
                            if (idx >= 0) {
                                decoder_o.releaseOutputBuffer(idx, true);
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }



                    } while((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) == 0);

                }
            }).start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    MptsVideoDecoder mMptsDecoder;
    void playWithMptsVideoDecoder() {
        try {
            mMptsDecoder = new MptsVideoDecoder(ts1, mSurface);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                    do {
                        int idx = 0;

                            idx = mMptsDecoder.dequeueOutputBuffer(10, info);
                            if (idx >= 0) {
                                mMptsDecoder.releaseOutputBuffer(idx, true);
                            }


                    } while((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) == 0);

                }
            }).start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });

        mTextureView = (TextureView)findViewById(R.id.texture_view);
        mTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                mSurface = new Surface(surface);
                //playWithVideoDecoder();
                playWithMptsVideoDecoder();
                //playWithMptsVideoDecodero();
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {

                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {

            }
        });

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
