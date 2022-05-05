package cn.com.buildwin.gosky.activities;

import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.hjq.permissions.OnPermissionCallback;
import com.hjq.permissions.Permission;
import com.hjq.permissions.XXPermissions;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Timer;

import cn.com.buildwin.gosky.R;
import cn.com.buildwin.gosky.application.Constants;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;
import tv.danmaku.ijk.media.widget.IRenderView;
import tv.danmaku.ijk.media.widget.IjkMpOptions;
import tv.danmaku.ijk.media.widget.IjkVideoView;

public class PreviewActivity extends AppCompatActivity {

    private static final String TAG = "PreviewActivity";

    private Button mTakePictureButton;
    private Button mRecordVideoButton;
    private Button mSetVrModeButton;
    private Button mSetVideoRotationButton;
    private Button mSetVideoRotation180Button;


    private ProgressBar mProgressBar;

    /* 预览设置 */
    // 渲染视图，不需要更改
    private static final int VIDEO_VIEW_RENDER = IjkVideoView.RENDER_TEXTURE_VIEW;
    // 拉伸方式，根据需要选择等比例拉伸或者全屏拉伸等
    private static final int VIDEO_VIEW_ASPECT = IRenderView.AR_4_3_FIT_PARENT;
    // 重连等待间隔，单位ms
    private static final int RECONNECT_INTERVAL = 500;

    private String mVideoPath;
    private IjkVideoView mVideoView;

    private byte[] cifVideoData;
    private final boolean inserting = false;
    private Timer mInsertTimer;

    private boolean bOutputVideo = false;

    // 状态
    private boolean recording = false;
    private static int videoRotationDegree = 0;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_preview);
        try {
            TCPClient.getInstance().getInfo();
        } catch (IOException e) {
            e.printStackTrace();
        }
        reqWrite();
        // init player
        IjkMediaPlayer.loadLibrariesOnce(null);
        IjkMediaPlayer.native_profileBegin("libijkplayer.so");

        // Dental camera ip address and port
        mVideoPath = Constants.RTSP_ADDRESS;

        mVideoView = (IjkVideoView) findViewById(R.id.video_view);
//         init player
        if (!initVideoView(mVideoView, mVideoPath)) {
            Log.e(TAG, "initVideoView fail");
            finish();
        }

        // load video data
        InputStream inStream = getResources().openRawResource(R.raw.cif);
        try {
            cifVideoData = new byte[inStream.available()];
            inStream.read(cifVideoData);
        } catch (Exception e) {
            e.printStackTrace();
        }

        /* 按键 */

        mTakePictureButton = (Button) findViewById(R.id.take_picture_button);
        mTakePictureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                takePhoto(1);
                // mVideoView.setOutputOriginalVideo(true);
            }
        });

        mRecordVideoButton = (Button) findViewById(R.id.record_video_button);
        mRecordVideoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                recordVideo();
            }
        });


        mSetVrModeButton = (Button) findViewById(R.id.set_vr_mode_button);
        mSetVrModeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean isVrMode = mVideoView.isVrMode();
                setVrMode(!isVrMode);
            }
        });

        mSetVideoRotationButton = (Button) findViewById(R.id.set_video_rotation_button);
        mSetVideoRotationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                videoRotationDegree += 90;
                videoRotationDegree %= 360;
                setVideoRotation(videoRotationDegree);
            }
        });

        mSetVideoRotation180Button = (Button) findViewById(R.id.set_video_rotation_180_button);
        mSetVideoRotation180Button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean isRotation180 = mVideoView.isRotation180();
                setVideoRotation180(!isRotation180);
            }
        });


        /* 进度条 */
        mProgressBar = (ProgressBar) findViewById(R.id.progressBar);
        setTakePhotoCallBack();


    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
        // Activity slide from left
        overridePendingTransition(
                android.R.anim.slide_in_left,
                android.R.anim.slide_out_right
        );
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (!mVideoView.isBackgroundPlayEnabled()) {
            mVideoView.stopPlayback();
            mVideoView.release(true);
            mVideoView.stopBackgroundPlay();
        } else {
            mVideoView.enterBackground();
        }
        mProgressBar.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 开启屏幕常亮
//        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
//
//        mVideoView.setRender(VIDEO_VIEW_RENDER);
//        mVideoView.setAspectRatio(VIDEO_VIEW_ASPECT);
        mVideoView.setVideoPath(mVideoPath);
        mVideoView.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 关闭屏幕常亮
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // 停止录像
        if (recording)
            mVideoView.stopRecordVideo();
        UdpTaskCenter.sharedCenter().setSendHeartBeat(false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        IjkMediaPlayer.native_profileEnd();
        removetTakePhotoCallBack();
    }

    private void reqWrite() {

        XXPermissions.with(this)
                // 申请安装包权限
                //.permission(Permission.REQUEST_INSTALL_PACKAGES)
                // 申请悬浮窗权限
                //.permission(Permission.SYSTEM_ALERT_WINDOW)
                // 申请通知栏权限
                //.permission(Permission.NOTIFICATION_SERVICE)
                // 申请系统设置权限

                // 申请单个权限
                // .permission(Permission.READ_EXTERNAL_STORAGE)
                //.permission(Permission.WRITE_EXTERNAL_STORAGE)
                .permission(Permission.MANAGE_EXTERNAL_STORAGE)
                //.permission(Permission.ACCESS_FINE_LOCATION)
                // 申请多个权限
                // .permission(Permission.Group.STORAGE)
                .request(new OnPermissionCallback() {

                    @Override
                    public void onGranted(List<String> permissions, boolean all) {
                        if (all) {
                            //  warnningtoast("获取权限成功");
                        } else {
                            //   warnningtoast("获取部分权限成功，但部分权限未正常授予");
                        }
                    }

                    @Override
                    public void onDenied(List<String> permissions, boolean never) {
                        if (never) {
                            //  warnningtoast("被永久拒绝授权，请手动授予");
                            // 如果是被永久拒绝就跳转到应用权限系统设置页面
                            XXPermissions.startPermissionActivity(PreviewActivity.this, permissions);
                        } else {
                            //   warnningtoast("获取权限失败");
                        }
                    }
                });
    }
    /* IjkPlayer */

    // play video from dental camera
    private boolean initVideoView(IjkVideoView videoView, String videoPath) {
        if (videoView == null)
            return false;

        // init player
        videoView.setRender(VIDEO_VIEW_RENDER);
        videoView.setAspectRatio(VIDEO_VIEW_ASPECT);

        // Ready to start preview callback
        videoView.setOnPreparedListener(mPlayerPreparedListener);
        // error callback
        videoView.setOnErrorListener(mPlayerErrorListener);
        // Receipt of image transmission board data callback
        videoView.setOnReceivedRtcpSrDataListener(mReceivedRtcpSrDataListener);
        // The data receiving callback has encapsulated the data verification, and the received data can be used directly
        // Need to use with the new firmware API
        // The data is received through the UDP protocol, which occupies less resources. Although it cannot guarantee 100% receipt, the success rate is acceptable.
        // If you need to ensure 100% successful reception, you can create a new TCP Socket for sending and receiving data
        videoView.setOnReceivedDataListener(mReceivedDataListener);
        // camera callback
        // resultCode, <0, an error occurred, =0 to take the next photo, =1, to complete the photo
        videoView.setOnTookPictureListener(mTookPictureListener);
        // Video callback
        // resultCode, < 0, an error occurs, = 0 to start recording, otherwise the recording is successfully saved
        videoView.setOnRecordVideoListener(mRecordVideoListener);
        // output image data
        videoView.setOnReceivedFrameListener(mReceivedFrameListener);
        // output raw image data
        videoView.setOnReceivedOriginalDataListener(mReceivedOriginalDataListener);
        // After playing
        videoView.setOnCompletionListener(mPlayerCompletionListener);

        // set options before setVideoPath
        applyOptionsToVideoView(videoView);

        // prefer mVideoPath
        if (videoPath != null)
            videoView.setVideoPath(videoPath);
        else {
            Log.e(TAG, "Null Data Source\n");
            return false;
        }

        return true;
    }

    private void applyOptionsToVideoView(IjkVideoView videoView) {
        // default options
        IjkMpOptions options = IjkMpOptions.defaultOptions();
        // custom options


        //            if (TextUtils.isEmpty(pixelFormat)) {

//            } else {
//                ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "overlay-format", "fcc-_es2"); // OpenGL ES2
//            }
        options.setPlayerOption("framedrop", 1);
        options.setPlayerOption("start-on-prepared", 0);

//            ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "rtsp_transport", "tcp");
        options.setPlayerOption("initial_timeout", 500000);
        options.setPlayerOption("stimeout", 500000);

        options.setPlayerOption("http-detect-range-support", 0);

        options.setPlayerOption("iformat", "mjpeg");
        options.setPlayerOption("skip_loop_filter", 48);
        options.setPlayerOption("mediacodec", 0);
        // JPEG解析方式，默认使用填充方式（即网络数据包丢失，则用上一帧数据补上），可以改为DROP（丢失数据包则丢掉整帧，网络不好不要使用），ORIGIN（原始方式，不要使用）
        options.setPlayerOption("rtp-jpeg-parse-packet-method", IjkMpOptions.RTP_JPEG_PARSE_PACKET_METHOD_FILL);
        // 读图像帧超时时间，单位us。如果在这个时间内接收不到一个完整图像，则断开连接
        options.setPlayerOption("readtimeout", 5000 * 1000);
        // Image type (PREFERRED_IMAGE_TYPE_*)
        options.setPlayerOption("preferred-image-type", IjkMpOptions.PREFERRED_IMAGE_TYPE_JPEG);
        // Image quality, available for lossy format (min and max are both from 1 to 51, 0 < min <= max, smaller is better, default is 2 and 31)
        options.setPlayerOption("image-quality-min", 2);
        options.setPlayerOption("image-quality-max", 20);
        // video
        options.setPlayerOption("preferred-video-type", IjkMpOptions.PREFERRED_VIDEO_TYPE_H264);
        options.setPlayerOption("video-need-transcoding", 1);
        options.setPlayerOption("mjpeg-pix-fmt", IjkMpOptions.MJPEG_PIX_FMT_YUVJ422P);
        // video quality, for MJPEG and MPEG4
        options.setPlayerOption("video-quality-min", 2);
        options.setPlayerOption("video-quality-max", 20);
        // x264 preset, tune and profile, for H264
        options.setPlayerOption("x264-option-preset", IjkMpOptions.X264_PRESET_ULTRAFAST);
        options.setPlayerOption("x264-option-tune", IjkMpOptions.X264_TUNE_ZEROLATENCY);
        options.setPlayerOption("x264-option-profile", IjkMpOptions.X264_PROFILE_MAIN);
        options.setPlayerOption("x264-params", "crf=20");
        // apply options to VideoView
        videoView.setOptions(options);
    }

    private final IjkVideoView.IVideoView.OnPreparedListener mPlayerPreparedListener
            = new IjkVideoView.IVideoView.OnPreparedListener() {
        @Override
        public void onPrepared(IjkVideoView videoView) {
            onStartPlayback();
        }
    };

    private final IjkVideoView.IVideoView.OnErrorListener mPlayerErrorListener
            = new IjkVideoView.IVideoView.OnErrorListener() {
        @Override
        public boolean onError(IjkVideoView videoView, int what, int extra) {
            stopAndRestartPlayback();
            return true;
        }
    };

    private final IjkVideoView.IVideoView.OnReceivedRtcpSrDataListener mReceivedRtcpSrDataListener
            = new IjkVideoView.IVideoView.OnReceivedRtcpSrDataListener() {
        @Override
        public void onReceivedRtcpSrData(IjkVideoView videoView, byte[] data) {
// Because the data channel is shared with RTCP, the return data needs to be distinguished from RTCP's Sender Report, and its own flag needs to be added to distinguish it.
// RTCP sends a Sender Report every 5 seconds by default
            Log.d(TAG, new String(data) + Arrays.toString(data));
        }
    };

    private final IjkVideoView.IVideoView.OnReceivedDataListener mReceivedDataListener
            = new IjkVideoView.IVideoView.OnReceivedDataListener() {
        @Override
        public void onReceivedData(IjkVideoView videoView, byte[] data) {
            // work with firmware api -> wifi_data_send
            String cmd = new String(data, StandardCharsets.UTF_8);
            if (cmd.equals("TAKE PHOTO")) {
                takePhoto(1);
            } else if (cmd.equals("RECORD VIDEO")) {
                recordVideo();
            }
        }
    };

    private final IjkVideoView.IVideoView.OnTookPictureListener mTookPictureListener
            = new IjkVideoView.IVideoView.OnTookPictureListener() {
        @Override
        public void onTookPicture(IjkVideoView videoView, int resultCode, String fileName) {
            if (resultCode == 1) {
                showToast("photo down");
            } else if (resultCode == 0) {
                showToast("photo saved：" + fileName);
            } else if (resultCode < 0) {
                showToast("photo error");
            }
        }
    };

    private final IjkVideoView.IVideoView.OnRecordVideoListener mRecordVideoListener
            = new IjkVideoView.IVideoView.OnRecordVideoListener() {
        @Override
        public void onRecordVideo(IjkVideoView videoView, final int resultCode, final String fileName) {
            Handler handler = new Handler(getMainLooper());
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (resultCode < 0) {
                        recording = false;

                        mRecordVideoButton.setText("start record");
                        showToast("record error");
                    } else if (resultCode == 0) {
                        recording = true;

                        mRecordVideoButton.setText("stop record");
                        showToast("start record");
                    } else {
                        mRecordVideoButton.setText("start record");
                        showToast("record over");

                        // set flag
                        recording = false;
                    }
                }
            });
        }
    };


    private final IjkVideoView.IVideoView.OnReceivedFrameListener mReceivedFrameListener
            = new IjkVideoView.IVideoView.OnReceivedFrameListener() {
        @Override
        public void onReceivedFrame(IjkVideoView videoView, byte[] data, int width, int height, int pixelFormat) {
            Log.d(TAG, "OnReceivedFrameListener: len = " + data.length + ", w = " + width + ", h = " + height + ", pf = " + pixelFormat);
        }
    };

    private final IjkVideoView.IVideoView.OnReceivedOriginalDataListener mReceivedOriginalDataListener
            = new IjkVideoView.IVideoView.OnReceivedOriginalDataListener() {
        @Override
        public void onReceivedOriginalData(IjkVideoView videoView, byte[] data, int width, int height, int pixelFormat, int videoId, int degree) {

            final int degreenow = (degree) % 360;
            PreviewActivity.this.runOnUiThread((new Runnable() {
                @Override
                public void run() {
                    // mVideoView.setmVideoRotationDegree(-degreenow);

                }
            }));
            Log.e("arsen", "degreenow" + degreenow);
            // Log.e(TAG, "OnReceivedOriginalDataListener: len = " + data.length + ", w = " + width + ", h = " + height + ", pf = " + pixelFormat + ", v = " + videoId+"degree"+degree);
        }
    };

    private final IjkVideoView.IVideoView.OnCompletionListener mPlayerCompletionListener
            = new IjkVideoView.IVideoView.OnCompletionListener() {
        @Override
        public void onCompletion(IjkVideoView videoView) {
            mVideoView.stopPlayback();
            mVideoView.release(true);
            mVideoView.stopBackgroundPlay();
        }
    };

    /**
     * Executed after playback starts
     */
    private void onStartPlayback() {
        showToast("start show");
        // 隐藏ProgressBar
        mProgressBar.setVisibility(View.INVISIBLE);
        UdpTaskCenter.sharedCenter().listen("192.168.1.1", 8990);
        UdpTaskCenter.sharedCenter().heartBeatTask();
        UdpTaskCenter.sharedCenter().setSendHeartBeat(true);
    }

    /**
     * Close the player and restart playback
     * Called when an error occurs
     */
    private void stopAndRestartPlayback() {
        mProgressBar.setVisibility(View.VISIBLE);

        mVideoView.post(new Runnable() {
            @Override
            public void run() {
                mVideoView.stopPlayback();
                mVideoView.release(true);
                mVideoView.stopBackgroundPlay();
            }
        });
        mVideoView.postDelayed(new Runnable() {
            @Override
            public void run() {
                mVideoView.setRender(VIDEO_VIEW_RENDER);
                mVideoView.setAspectRatio(VIDEO_VIEW_ASPECT);
                mVideoView.setVideoPath(mVideoPath);
                mVideoView.start();
            }
        }, RECONNECT_INTERVAL);
    }


    // 新API
    public void sendData(byte[] data) {
        // Send
        try {
            mVideoView.sendData(data);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 拍照
     */
    private void takePhoto(int num) {
        // Take a photo
        String photoFilePath = getPhotoDirPath();
        String photoFileName = getMediaFileName();
        try {
            // 拍照参数说明
            // 1、目录路径，目录需要先创建，否则返回错误
            // 2、文件名，不需要指定扩展名
            // 3和4、保存图像的宽高，如果都是-1（不允许只有一个-1），则保存原图像大小，如果是其他，则拉伸为设定值
            // 5、连续拍照数量，连续拍照，中间不设间隔
            mVideoView.takePicture(photoFilePath, photoFileName, -1, -1, num);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 录像
     * 目前MJPEG格式为YUV420P，部分手机的默认播放器不支持，只支持YUV422P，不支持直接播放和使用什么容器（比如AVI、MP4）无关
     * 如果在applyOptionsToVideoView方法中，录像格式和像素格式和原来的格式不一样，则启用自动转码录制，具体参见SDK说明
     */
    private void recordVideo() {
        if (recording) {
            if (inserting)
                mVideoView.stopInsertVideo();
            mVideoView.stopRecordVideo();
        } else {
            String videoFilePath = getVideoDirPath();
            String videoFileName = getMediaFileName();
            // Start to record video
            try {
                // Description of recording parameters
                // 1. Directory path, the directory needs to be created first, otherwise an error will be returned
                // 2, the file name, automatically specify the extension
                // 3 and 4, the width and height of the video, currently not used, reserved

                mVideoView.startRecordVideo(videoFilePath, videoFileName, -1, -1);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 在WiFi图像录制中插入视频
     * ！！！播放器配置中需要显示或者隐式地打开"video-need-transcoding"
     */
    private void insertVideo() {
        if (inserting) {
            if (mInsertTimer != null) {
                mInsertTimer.purge();
                mInsertTimer.cancel();
            }
            mVideoView.stopInsertVideo();
        } else {
            // cif, yuv444p
            mVideoView.startInsertVideo(356, 288, 5);
        }
    }

    /**
     * 输出图像数据
     */
    private void setOutputVideo() {
        bOutputVideo = !bOutputVideo;
        mVideoView.setOutputVideo(bOutputVideo);
        mVideoView.setOutputOriginalVideo(!bOutputVideo);
    }

    /**
     * 设置VR模式（左右分屏显示）
     */
    private void setVrMode(boolean en) {
        mVideoView.setVrMode(en);
    }

    /**
     * 软件旋转屏幕（显示旋转），顺时针旋转（非Sensor旋转，图传板传过来的图旋转角度不变，改变的是渲染的图像角度，不影响拍照和录像）
     */
    private void setVideoRotation(int degree) {
        mVideoView.setVideoRotation(degree);
    }

    /**
     * 软件旋转屏幕（图像帧旋转），因为需要保持宽高一直，所以只支持180°（非Sensor旋转，图传板传过来的图旋转角度不变，改变的是输出图像的角度，拍照和录像角度会改变）
     */
    private void setVideoRotation180(boolean enable) {
        mVideoView.setRotation180(enable);
    }


    private void showToast(String s) {
        Toast.makeText(PreviewActivity.this, s, Toast.LENGTH_SHORT).show();
    }






    /* 以下是Demo使用到的方法 */

    // 主目录名
    private static final String HOME_PATH_NAME = "MediaStream";

    // 照片和视频的子目录名
    private static final String PHOTO_PATH_NAME = "Image";
    private static final String VIDEO_PATH_NAME = "Movie";

    /**
     * 获取应用数据主目录
     *
     * @return 主目录路径
     */
    static public String getHomePath() {
        String homePath = null;

        try {
            String extStoragePath = Environment.getExternalStorageDirectory().getCanonicalPath();
            File homeFile = new File(extStoragePath, HOME_PATH_NAME);
            homePath = homeFile.getCanonicalPath();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return homePath;
    }

    /**
     * 获取父目录下子目录
     */
    static public String getSubDir(String parent, String dir) {
        if (parent == null)
            return null;

        String subDirPath = null;

        try {
            // 获取展开的子目录路径
            File subDirFile = new File(parent, dir);

            if (!subDirFile.exists())
                subDirFile.mkdirs();

            subDirPath = subDirFile.getCanonicalPath();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return subDirPath;
    }

    /**
     * 获取主目录下照片目录
     *
     * @return 照片目录路径
     */
    static public String getPhotoPath() {
        return getSubDir(getHomePath(), PHOTO_PATH_NAME);
    }

    /**
     * 获取主目录下视频目录
     *
     * @return 视频目录路径
     */
    static public String getVideoPath() {
        return getSubDir(getHomePath(), VIDEO_PATH_NAME);
    }

    /**
     * 获取图片目录路径
     *
     * @return 图片目录路径
     */
    static public String getPhotoDirPath() {
        String photoPath = getPhotoPath();
        if (photoPath == null)
            return null;

        // 如果文件夹不存在, 则创建
        File photoDir = new File(photoPath);
        if (!photoDir.exists()) {
            // 创建失败则返回null
            if (!photoDir.mkdirs()) return null;
        }

        return photoDir.getAbsolutePath();
    }

    /**
     * 获取视频目录路径
     *
     * @return 视频目录路径
     */
    static public String getVideoDirPath() {
        String videoPath = getVideoPath();
        if (videoPath == null)
            return null;

// If the folder doesn't exist, create it]
        File videoDir = new File(videoPath);
        if (!videoDir.exists()) {
// If the creation fails, return null
            if (!videoDir.mkdirs()) return null;
        }

        return videoDir.getAbsolutePath();
    }

    /**
     * Get media file name
     *
     * @return media file name
     */

    static public String getMediaFileName() {
        // 由日期创建文件名
        Date date = new Date();
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd_HHmmsss", Locale.getDefault());
        String dateString = format.format(date);
//        String photoFileName = dateString + "." + PHOTO_FILE_EXTENSION;
        String photoFileName = dateString;

        return photoFileName;
    }

    private void setTakePhotoCallBack() {
        UdpTaskCenter.sharedCenter().setOnTakePhoto(new UdpTaskCenter.OnTakePhoto() {
            @Override
            public void takercvPhoto() {
                takePhoto(1);
            }
        });
    }

    private void removetTakePhotoCallBack() {
        UdpTaskCenter.sharedCenter().removeTakePhoto();
    }
}
