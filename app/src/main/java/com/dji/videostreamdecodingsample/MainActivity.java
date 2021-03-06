package com.dji.videostreamdecodingsample;

import android.Manifest;
import android.app.Activity;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.YuvImage;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.util.TypedValue;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.dji.videostreamdecodingsample.media.DJIVideoStreamDecoder;
import com.dji.videostreamdecodingsample.media.NativeHelper;
import com.dji.videostreamdecodingsample.utils.ThreadPoolUtil;
import com.tencent.rtmp.ITXLivePushListener;
import com.tencent.rtmp.TXLiveConstants;
import com.tencent.rtmp.TXLivePushConfig;
import com.tencent.rtmp.TXLivePusher;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import dji.common.product.Model;
import dji.sdk.base.BaseProduct;
import dji.sdk.camera.Camera;
import dji.sdk.camera.VideoFeeder;
import dji.sdk.codec.DJICodecManager;

public class MainActivity extends Activity implements DJIVideoStreamDecoder.IYuvDataListener, ITXLivePushListener {
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int MSG_WHAT_SHOW_TOAST = 0;
    private static final int MSG_WHAT_UPDATE_TITLE = 1;
    private static final boolean useSurface = true;

    private static final String DA_NIU_BASE_URL = "rtmp://player.daniulive.com:1935/hls/compass";
    private static final String TX_BASE_URL = "rtmp://18407.livepush.myqcloud.com/live/18407_uav23test?bizid=18407&txSecret=32a74ec0c341fc4a486fe9e97822f6e1&txTime=5A4271FF";

    private String mLastUrl = DA_NIU_BASE_URL;

    private TextView titleTv;
    private TextureView videostreamPreviewTtView;
    private SurfaceView videostreamPreviewSf;
    private SurfaceHolder videostreamPreviewSh;

    private BaseProduct mProduct;
    private Camera mCamera;
    private DJICodecManager mCodecManager;

    private TextView savePath;
    private TextView screenShot;

    private StringBuilder stringBuilder;

    protected VideoFeeder.VideoDataCallback mReceivedVideoDataCallBack = null;
    private boolean mIsVideoPublishing = false;
    private Button mBtnPlay;
    private TXLivePushConfig mLivePushConfig;
    private TXLivePusher mLivePusher;

    private Button mBtnSwitchUrl;
    private boolean mIsStart = false;

    @Override
    protected void onResume() {
        super.onResume();
        if (useSurface) {
            initPreviewerSurfaceView();
        } else {
            initPreviewerTextureView();
        }
        notifyStatusChange();
    }

    @Override
    protected void onPause() {
        if (mCamera != null) {
            if (VideoFeeder.getInstance().getPrimaryVideoFeed() != null) {
                VideoFeeder.getInstance().getPrimaryVideoFeed().setCallback(null);
            }
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (useSurface) {

        }
        if (mCodecManager != null) {
            mCodecManager.destroyCodec();
        }
        if (mLivePusher!=null && mIsVideoPublishing) {
            stopPublishRtmp();
        }

        super.onDestroy();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // When the compile and target version is higher than 22, please request the
        // following permissions at runtime to ensure the
        // SDK work well.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.VIBRATE,
                            Manifest.permission.INTERNET, Manifest.permission.ACCESS_WIFI_STATE,
                            Manifest.permission.WAKE_LOCK, Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_NETWORK_STATE, Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.CHANGE_WIFI_STATE, Manifest.permission.MOUNT_UNMOUNT_FILESYSTEMS,
                            Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.SYSTEM_ALERT_WINDOW,
                            Manifest.permission.READ_PHONE_STATE,
                    }
                    , 1);
        }

        setContentView(R.layout.activity_main);
        initUi();

    }

    public Handler mainHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_WHAT_SHOW_TOAST:
                    Toast.makeText(getApplicationContext(), (String) msg.obj, Toast.LENGTH_SHORT).show();
                    break;
                case MSG_WHAT_UPDATE_TITLE:
                    if (titleTv != null) {
                        titleTv.setText((String) msg.obj);
                    }
                    break;
                default:
                    break;
            }
        }
    };

    private void showToast(String s) {
        mainHandler.sendMessage(
                mainHandler.obtainMessage(MSG_WHAT_SHOW_TOAST, s)
        );
    }

    private void updateTitle(String s) {
        mainHandler.sendMessage(
                mainHandler.obtainMessage(MSG_WHAT_UPDATE_TITLE, s)
        );
    }


    private Bitmap decodeResource(Resources resources, int id) {
        TypedValue value = new TypedValue();
        resources.openRawResource(id, value);
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inTargetDensity = value.density;
        return BitmapFactory.decodeResource(resources, id, opts);
    }

    private void startPublishRtmp() {
        //设置为推自定义视频模式
        int customModeType = 0;
        customModeType |= TXLiveConstants.CUSTOM_MODE_VIDEO_CAPTURE;
        mLivePushConfig.setCustomModeType(customModeType);
        //选择就近CDN
        mLivePushConfig.enableNearestIP(true);
        //设置硬编码是否支持MainProfile，与软编无关
        mLivePushConfig.enableVideoHardEncoderMainProfile(true);
        //自动选择是否启用硬件加速
        mLivePushConfig.setHardwareAcceleration( TXLiveConstants.ENCODE_VIDEO_HARDWARE);
        //设置推流暂停时,后台播放的暂停图片和暂停图片的方式.
        Bitmap bitmap = decodeResource(getResources(),R.drawable.pause_publish);
        mLivePushConfig.setPauseImg(bitmap);
        mLivePushConfig.setPauseImg(300,5);
        //设置推流暂停时,后台推流的选项
        mLivePushConfig.setPauseFlag(TXLiveConstants.PAUSE_FLAG_PAUSE_VIDEO | TXLiveConstants.PAUSE_FLAG_PAUSE_AUDIO);
        //设置分辨率，由于自己推流，使用和摄像机同样的
        mLivePushConfig.setVideoResolution(TXLiveConstants.VIDEO_RESOLUTION_TYPE_1280_720);
        //设置推流事件监听
        mLivePusher.setPushListener(this);
        //不插入关键帧，因为由我们自己编码
        mLivePushConfig.setVideoEncodeGop(-1);

        mLivePusher.setConfig(mLivePushConfig);
        //设置推流地址
        mLivePusher.startPusher(mLastUrl);

        mIsVideoPublishing = true;

        mBtnPlay.setBackgroundResource(R.drawable.play_pause);

    }

    private void initUi() {
        savePath = (TextView) findViewById(R.id.activity_main_save_path);
        screenShot = (TextView) findViewById(R.id.activity_main_screen_shot);
        screenShot.setSelected(false);
        titleTv = (TextView) findViewById(R.id.title_tv);
        videostreamPreviewTtView = (TextureView) findViewById(R.id.livestream_preview_ttv);
        videostreamPreviewSf = (SurfaceView) findViewById(R.id.livestream_preview_sf);
        if (useSurface) {
            videostreamPreviewSf.setVisibility(View.VISIBLE);
            videostreamPreviewTtView.setVisibility(View.GONE);

        } else {
            videostreamPreviewSf.setVisibility(View.GONE);
            videostreamPreviewTtView.setVisibility(View.VISIBLE);
        }

        mLivePusher = new TXLivePusher(this);
        mLivePushConfig = new TXLivePushConfig();

        mBtnPlay = findViewById(R.id.btn_play_or_stop);
        mBtnPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!mIsVideoPublishing) {
                    startPublishRtmp();
                } else {
                    stopPublishRtmp();
                }
            }
        });

        mBtnSwitchUrl = (Button) findViewById(R.id.btn_switch_url);
        mBtnSwitchUrl.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!mIsStart) {
                    mLastUrl = DA_NIU_BASE_URL;
                    mIsStart = true;
                    mBtnSwitchUrl.setText("当前使用daniu");
                } else {
                    mLastUrl = TX_BASE_URL;
                    mIsStart = false;
                    mBtnSwitchUrl.setText("当前使用TX");
                }
            }
        });
    }



    private void stopPublishRtmp() {
        mIsVideoPublishing = false;
        mLivePusher.stopBGM();
        mLivePusher.stopScreenCapture();
        mLivePusher.setPushListener(null);
        mLivePusher.stopPusher();
        mBtnPlay.setBackgroundResource(R.drawable.play_start);

        if(mLivePushConfig != null) {
            mLivePushConfig.setPauseImg(null);
        }
    }

    private void notifyStatusChange() {

        mProduct = VideoDecodingApplication.getProductInstance();

        Log.d(TAG, "notifyStatusChange: " + (mProduct == null ? "Disconnect" : (mProduct.getModel() == null ? "null model" : mProduct.getModel().name())));
        if (mProduct != null && mProduct.isConnected() && mProduct.getModel() != null) {
            updateTitle(mProduct.getModel().name() + " Connected");
        } else {
            updateTitle("Disconnected");
        }

        // The callback for receiving the raw H264 video data for camera live view
        mReceivedVideoDataCallBack = new VideoFeeder.VideoDataCallback() {

            @Override
            public void onReceive(final byte[] videoBuffer, int size) {
                //Log.d(TAG, "camera recv video data size: " + size);
                if (useSurface) {
                    DJIVideoStreamDecoder.getInstance().parse(videoBuffer, size);
                } else if (mCodecManager != null) {
                    mCodecManager.sendDataToDecoder(videoBuffer, size);
                }
            }
        };

        if (null == mProduct || !mProduct.isConnected()) {
            mCamera = null;
            showToast("Disconnected");
        } else {
            if (!mProduct.getModel().equals(Model.UNKNOWN_AIRCRAFT)) {
                if (VideoFeeder.getInstance().getPrimaryVideoFeed() != null) {
                    VideoFeeder.getInstance().getPrimaryVideoFeed().setCallback(mReceivedVideoDataCallBack);
                }
            }
        }
    }



    /**
     * Init a fake texture view to for the codec manager, so that the video raw data can be received
     * by the camera
     */
    private void initPreviewerTextureView() {
        videostreamPreviewTtView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                Log.d(TAG, "real onSurfaceTextureAvailable");
                if (mCodecManager == null) {
                    mCodecManager = new DJICodecManager(getApplicationContext(), surface, width, height);
                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                if (mCodecManager != null) mCodecManager.cleanSurface();
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {

            }
        });
    }

    /**
     * Init a surface view for the DJIVideoStreamDecoder
     */
    private void initPreviewerSurfaceView(){
        videostreamPreviewSh = videostreamPreviewSf.getHolder();
        videostreamPreviewSh.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                NativeHelper.getInstance().init();
                DJIVideoStreamDecoder.getInstance().init(getApplicationContext(), videostreamPreviewSh.getSurface());
                DJIVideoStreamDecoder.getInstance().setYuvDataListener(MainActivity.this);
                DJIVideoStreamDecoder.getInstance().resume();
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                DJIVideoStreamDecoder.getInstance().changeSurface(holder.getSurface());
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                DJIVideoStreamDecoder.getInstance().stop();
                DJIVideoStreamDecoder.getInstance().destroy();
                NativeHelper.getInstance().release();
            }

        });
    }


    private byte[] mYuvFrame;
    private int width,height;

   Runnable mRunnable =  new Runnable() {
        @Override
        public void run() {
            if (mIsVideoPublishing) {
                mLivePusher.sendCustomVideoData(mYuvFrame, TXLivePusher.YUV_420P,width,height);
            }
        }
    };
    @Override
    public void onYuvDataReceived(byte[] yuvFrame, int width, int height) {
        if (mIsVideoPublishing) {
            mLivePusher.sendCustomVideoData(yuvFrame, TXLivePusher.YUV_420P,width,height);
        }
//        this.mYuvFrame = yuvFrame;
//        this.width = width;
//        this.height = height;
//
//        ThreadPoolUtil.getmSingleThreadPoolExecutor().execute(mRunnable);
//        //In this demo, we test the YUV data by saving it into JPG files.
//        if (DJIVideoStreamDecoder.getInstance().frameIndex % 30 == 0) {
//
//            byte[] y = new byte[width * height];
//            byte[] u = new byte[width * height / 4];
//            byte[] v = new byte[width * height / 4];
//            byte[] nu = new byte[width * height / 4]; //
//            byte[] nv = new byte[width * height / 4];
//            System.arraycopy(yuvFrame, 0, y, 0, y.length);
//            for (int i = 0; i < u.length; i++) {
//                v[i] = yuvFrame[y.length + 2 * i];
//                u[i] = yuvFrame[y.length + 2 * i + 1];
//            }
//            int uvWidth = width / 2;
//            int uvHeight = height / 2;
//            for (int j = 0; j < uvWidth / 2; j++) {
//                for (int i = 0; i < uvHeight / 2; i++) {
//                    byte uSample1 = u[i * uvWidth + j];
//                    byte uSample2 = u[i * uvWidth + j + uvWidth / 2];
//                    byte vSample1 = v[(i + uvHeight / 2) * uvWidth + j];
//                    byte vSample2 = v[(i + uvHeight / 2) * uvWidth + j + uvWidth / 2];
//                    nu[2 * (i * uvWidth + j)] = uSample1;
//                    nu[2 * (i * uvWidth + j) + 1] = uSample1;
//                    nu[2 * (i * uvWidth + j) + uvWidth] = uSample2;
//                    nu[2 * (i * uvWidth + j) + 1 + uvWidth] = uSample2;
//                    nv[2 * (i * uvWidth + j)] = vSample1;
//                    nv[2 * (i * uvWidth + j) + 1] = vSample1;
//                    nv[2 * (i * uvWidth + j) + uvWidth] = vSample2;
//                    nv[2 * (i * uvWidth + j) + 1 + uvWidth] = vSample2;
//                }
//            }
//            //nv21test
//            byte[] bytes = new byte[yuvFrame.length];
//            System.arraycopy(y, 0, bytes, 0, y.length);
//            for (int i = 0; i < u.length; i++) {
//                bytes[y.length + (i * 2)] = nv[i];
//                bytes[y.length + (i * 2) + 1] = nu[i];
//            }
//            Log.d(TAG,
//                    "onYuvDataReceived: frame index: "
//                            + DJIVideoStreamDecoder.getInstance().frameIndex
//                            + ",array length: "
//                            + bytes.length);
//            screenShot(bytes, Environment.getExternalStorageDirectory() + "/DJI_ScreenShot");
//        }
    }

    /**
     * Save the buffered data into a JPG image file
     */
    private void screenShot(byte[] buf, String shotDir) {
        File dir = new File(shotDir);
        if (!dir.exists() || !dir.isDirectory()) {
            dir.mkdirs();
        }
        YuvImage yuvImage = new YuvImage(buf,
                ImageFormat.NV21,
                DJIVideoStreamDecoder.getInstance().width,
                DJIVideoStreamDecoder.getInstance().height,
                null);
        OutputStream outputFile;
        final String path = dir + "/ScreenShot_" + System.currentTimeMillis() + ".jpg";
        try {
            outputFile = new FileOutputStream(new File(path));
        } catch (FileNotFoundException e) {
            Log.e(TAG, "test screenShot: new bitmap output file error: " + e);
            return;
        }
        if (outputFile != null) {
            yuvImage.compressToJpeg(new Rect(0,
                    0,
                    DJIVideoStreamDecoder.getInstance().width,
                    DJIVideoStreamDecoder.getInstance().height), 100, outputFile);
        }
        try {
            outputFile.close();
        } catch (IOException e) {
            Log.e(TAG, "test screenShot: compress yuv image error: " + e);
            e.printStackTrace();
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                displayPath(path);
            }
        });
    }

    public void onClick(View v) {
        if (screenShot.isSelected()) {
            screenShot.setText("YUV Screen Shot");
            screenShot.setSelected(false);
            if (useSurface) {
                DJIVideoStreamDecoder.getInstance().changeSurface(videostreamPreviewSh.getSurface());
            }
            savePath.setText("");
            savePath.setVisibility(View.INVISIBLE);
            stringBuilder = null;
        } else {
            screenShot.setText("Live Stream");
            screenShot.setSelected(true);
            if (useSurface) {
                DJIVideoStreamDecoder.getInstance().changeSurface(null);
            }
            savePath.setText("");
            savePath.setVisibility(View.VISIBLE);
        }
    }

    private void displayPath(String path) {
        if (stringBuilder == null) {
            stringBuilder = new StringBuilder();
        }

        path = path + "\n";
        stringBuilder.append(path);
        savePath.setText(stringBuilder.toString());
    }

    @Override
    public void onPushEvent(int i, Bundle bundle) {
        Toast.makeText(this, i+"----"+bundle.getString(TXLiveConstants.EVT_DESCRIPTION), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onNetStatus(Bundle bundle) {

    }
}
