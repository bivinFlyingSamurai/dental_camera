/*
 * Copyright (C) 2015 Bilibili
 * Copyright (C) 2015 Zhang Rui <bbcallen@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package tv.danmaku.ijk.media.widget;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.MediaController;
import android.widget.TableLayout;
import android.widget.TextView;

import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

import tv.danmaku.ijk.media.R;
import tv.danmaku.ijk.media.services.MediaPlayerService;
import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;
import tv.danmaku.ijk.media.player.IjkTimedText;
import tv.danmaku.ijk.media.player.misc.IMediaFormat;
import tv.danmaku.ijk.media.player.misc.ITrackInfo;
import tv.danmaku.ijk.media.player.misc.IjkMediaFormat;

public class IjkVideoView extends FrameLayout implements MediaController.MediaPlayerControl {
    private String TAG = "IjkVideoView";
    // settable by the client
    private Uri mUri;
    private Map<String, String> mHeaders;

    private static final int IJK_LOG_LEVEL = IjkMediaPlayer.IJK_LOG_INFO;

    private IjkMpOptions mOptions;

    // FPV
    private boolean vrMode;
    private boolean rotation180;

    // identify the data sent from board
    private static final byte DATA_SIGNATURE_BYTE_1 = 0x69;
    private static final byte DATA_SIGNATURE_BYTE_2 = 0x1e;
    private static final byte DATA_SIGNATURE_BYTE_3 = 0x5a;
    private static final byte DATA_SIGNATURE_BYTE_4 = 0x0f;

    // all possible internal states
    private static final int STATE_ERROR = -1;
    private static final int STATE_IDLE = 0;
    private static final int STATE_PREPARING = 1;
    private static final int STATE_PREPARED = 2;
    private static final int STATE_PLAYING = 3;
    private static final int STATE_PAUSED = 4;
    private static final int STATE_PLAYBACK_COMPLETED = 5;

    // mCurrentState is a VideoView object's current state.
    // mTargetState is the state that a method caller intends to reach.
    // For instance, regardless the VideoView object's current state,
    // calling pause() intends to bring the object to a target state
    // of STATE_PAUSED.
    private int mCurrentState = STATE_IDLE;
    private int mTargetState = STATE_IDLE;

    // All the stuff we need for playing and showing a video
    private IRenderView.ISurfaceHolder mSurfaceHolder = null;
    private IMediaPlayer mMediaPlayer = null;
    // private int         mAudioSession;
    private int mVideoWidth;
    private int mVideoHeight;
    private int mSurfaceWidth;
    private int mSurfaceHeight;
    private int mVideoRotationDegree;
    private IMediaController mMediaController;
    private IVideoView.OnCompletionListener mOnCompletionListener;
    private IVideoView.OnPreparedListener mOnPreparedListener;
    private int mCurrentBufferPercentage;
    private IVideoView.OnErrorListener mOnErrorListener;
    private IVideoView.OnInfoListener mOnInfoListener;
    private IVideoView.OnReceivedRtcpSrDataListener mOnReceivedRtcpSrDataListener;
    private IVideoView.OnReceivedDataListener mOnReceivedDataListener;
    private IVideoView.OnTookPictureListener mOnTookPictureListener;
    private IVideoView.OnRecordVideoListener mOnRecordVideoListener;
    private IVideoView.OnInsertVideoListener mOnInsertVideoListener;
    private IVideoView.OnReceivedFrameListener mOnReceivedFrameListener;
    private IVideoView.OnReceivedOriginalDataListener mOnReceivedOriginalDataListener;
    private IVideoView.OnDeviceConnectedListener mOnDeviceConnectedListener;
    private int mSeekWhenPrepared;  // recording the seek position while preparing
    private boolean mCanPause = true;
    private boolean mCanSeekBack = true;
    private boolean mCanSeekForward = true;

    /** Subtitle rendering widget overlaid on top of the video. */
    // private RenderingWidget mSubtitleWidget;

    /**
     * Listener for changes to subtitle data, used to redraw when needed.
     */
    // private RenderingWidget.OnChangedListener mSubtitlesChangedListener;

    private Context mAppContext;
    private IRenderView mRenderView;
    private int mVideoSarNum;
    private int mVideoSarDen;

    private InfoHudViewHolder mHudViewHolder;

    private long mPrepareStartTime = 0;
    private long mPrepareEndTime = 0;

    private long mSeekStartTime = 0;
    private long mSeekEndTime = 0;

    private TextView subtitleDisplay;

    public IjkVideoView(Context context) {
        super(context);
        initVideoView(context);
    }

    public IjkVideoView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initVideoView(context);
    }

    public IjkVideoView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initVideoView(context);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public IjkVideoView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        initVideoView(context);
    }

    public void setOptions(IjkMpOptions options) {
        mOptions = options;
    }

    // REMOVED: onMeasure
    // REMOVED: onInitializeAccessibilityEvent
    // REMOVED: onInitializeAccessibilityNodeInfo
    // REMOVED: resolveAdjustedSize

    private void initVideoView(Context context) {
        mAppContext = context.getApplicationContext();

        initBackground();
        initRenders();
        mVideoWidth = 0;
        mVideoHeight = 0;
        // REMOVED: getHolder().addCallback(mSHCallback);
        // REMOVED: getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        setFocusable(true);
        setFocusableInTouchMode(true);
        requestFocus();
        // REMOVED: mPendingSubtitleTracks = new Vector<Pair<InputStream, MediaFormat>>();
        mCurrentState = STATE_IDLE;
        mTargetState = STATE_IDLE;

        subtitleDisplay = new TextView(context);
        subtitleDisplay.setTextSize(24);
        subtitleDisplay.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams layoutParams_txt = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        addView(subtitleDisplay, layoutParams_txt);
    }

    public void setRenderView(IRenderView renderView) {
        if (mRenderView != null) {
            if (mMediaPlayer != null)
                mMediaPlayer.setDisplay(null);

            View renderUIView = mRenderView.getView();
            mRenderView.removeRenderCallback(mSHCallback);
            mRenderView = null;
            removeView(renderUIView);
        }

        if (renderView == null)
            return;

        mRenderView = renderView;
        renderView.setAspectRatio(mCurrentAspectRatio);
        if (mVideoWidth > 0 && mVideoHeight > 0)
            renderView.setVideoSize(mVideoWidth, mVideoHeight);
        if (mVideoSarNum > 0 && mVideoSarDen > 0)
            renderView.setVideoSampleAspectRatio(mVideoSarNum, mVideoSarDen);

        View renderUIView = mRenderView.getView();
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        renderUIView.setLayoutParams(lp);
        addView(renderUIView);

        mRenderView.addRenderCallback(mSHCallback);
        mRenderView.setVideoRotation(mVideoRotationDegree);
    }

    public void setRender(int render) {
        switch (render) {
            case RENDER_NONE:
                setRenderView(null);
                break;
            case RENDER_TEXTURE_VIEW: {
                TextureRenderView renderView = new TextureRenderView(getContext());
                if (mMediaPlayer != null) {
                    renderView.getSurfaceHolder().bindToMediaPlayer(mMediaPlayer);
                    renderView.setVideoSize(mMediaPlayer.getVideoWidth(), mMediaPlayer.getVideoHeight());
                    renderView.setVideoSampleAspectRatio(mMediaPlayer.getVideoSarNum(), mMediaPlayer.getVideoSarDen());
                    renderView.setAspectRatio(mCurrentAspectRatio);
                }
                setRenderView(renderView);
                break;
            }
            case RENDER_SURFACE_VIEW: {
                SurfaceRenderView renderView = new SurfaceRenderView(getContext());
                setRenderView(renderView);
                break;
            }
            default:
                Log.e(TAG, String.format(Locale.getDefault(), "invalid render %d\n", render));
                break;
        }

    }

    public void setHudView(TableLayout tableLayout) {
        mHudViewHolder = new InfoHudViewHolder(getContext(), tableLayout);
    }

    /**
     * Sets video path.
     *
     * @param path the path of the video.
     */
    public void setVideoPath(String path) {
        setVideoURI(Uri.parse(path));
    }

    /**
     * Sets video URI.
     *
     * @param uri the URI of the video.
     */
    public void setVideoURI(Uri uri) {
        setVideoURI(uri, null);
    }

    /**
     * Sets video URI using specific headers.
     *
     * @param uri     the URI of the video.
     * @param headers the headers for the URI request.
     *                Note that the cross domain redirection is allowed by default, but that can be
     *                changed with key/value pairs through the headers parameter with
     *                "android-allow-cross-domain-redirect" as the key and "0" or "1" as the value
     *                to disallow or allow cross domain redirection.
     */
    private void setVideoURI(Uri uri, Map<String, String> headers) {
        mUri = uri;
        mHeaders = headers;
        mSeekWhenPrepared = 0;
        openVideo();
        requestLayout();
        invalidate();
    }

    // REMOVED: addSubtitleSource
    // REMOVED: mPendingSubtitleTracks

    public void stopPlayback() {
        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
            mMediaPlayer.release();
            mMediaPlayer = null;
            if (mHudViewHolder != null)
                mHudViewHolder.setMediaPlayer(null);
            mCurrentState = STATE_IDLE;
            mTargetState = STATE_IDLE;
            AudioManager am = (AudioManager) mAppContext.getSystemService(Context.AUDIO_SERVICE);
            am.abandonAudioFocus(null);
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    private void openVideo() {
        if (mUri == null || mSurfaceHolder == null) {
            // not ready for playback just yet, will try again later
            return;
        }
        // we shouldn't clear the target state, because somebody might have
        // called start() previously
        release(false);

        AudioManager am = (AudioManager) mAppContext.getSystemService(Context.AUDIO_SERVICE);
        am.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);

        try {
            mMediaPlayer = createPlayer();

            // TODO: create SubtitleController in MediaPlayer, but we need
            // a context for the subtitle renderers
            final Context context = getContext();
            // REMOVED: SubtitleController

            // REMOVED: mAudioSession
            mMediaPlayer.setOnPreparedListener(mPreparedListener);
            mMediaPlayer.setOnVideoSizeChangedListener(mSizeChangedListener);
            mMediaPlayer.setOnCompletionListener(mCompletionListener);
            mMediaPlayer.setOnErrorListener(mErrorListener);
            mMediaPlayer.setOnInfoListener(mInfoListener);
            mMediaPlayer.setOnBufferingUpdateListener(mBufferingUpdateListener);
            mMediaPlayer.setOnSeekCompleteListener(mSeekCompleteListener);
            mMediaPlayer.setOnTimedTextListener(mOnTimedTextListener);
            mMediaPlayer.setOnReceivedRtcpSrDataListener(mReceivedRtcpSrDataListener);
            mMediaPlayer.setOnTookPictureListener(mTookPictureListener);
            mMediaPlayer.setOnRecordVideoListener(mRecordVideoListener);
            mMediaPlayer.setOnInsertVideoListener(mInsertVideoListener);
            mMediaPlayer.setOnReceivedFrameListener(mReceivedFrameListener);
            mMediaPlayer.setOnReceivedOriginalDataListener(mReceivedOriginalDataListener);
            mMediaPlayer.setOnDeviceConnectedListener(mDeviceConnectedListener);
            mCurrentBufferPercentage = 0;
            String scheme = mUri.getScheme();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                mMediaPlayer.setDataSource(mAppContext, mUri, mHeaders);
            } else {
                mMediaPlayer.setDataSource(mUri.toString());
            }
            bindSurfaceHolder(mMediaPlayer, mSurfaceHolder);
            mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mMediaPlayer.setScreenOnWhilePlaying(true);
            mPrepareStartTime = System.currentTimeMillis();
            mMediaPlayer.prepareAsync();
            if (mHudViewHolder != null)
                mHudViewHolder.setMediaPlayer(mMediaPlayer);

            // REMOVED: mPendingSubtitleTracks

            // we don't set the target state here either, but preserve the
            // target state that was there before.
            mCurrentState = STATE_PREPARING;
            attachMediaController();
        } catch (IOException ex) {
            Log.w(TAG, "Unable to open content: " + mUri, ex);
            mCurrentState = STATE_ERROR;
            mTargetState = STATE_ERROR;
            mErrorListener.onError(mMediaPlayer, MediaPlayer.MEDIA_ERROR_UNKNOWN, 0);
        } catch (IllegalArgumentException ex) {
            Log.w(TAG, "Unable to open content: " + mUri, ex);
            mCurrentState = STATE_ERROR;
            mTargetState = STATE_ERROR;
            mErrorListener.onError(mMediaPlayer, MediaPlayer.MEDIA_ERROR_UNKNOWN, 0);
        } finally {
            // REMOVED: mPendingSubtitleTracks.clear();
        }
    }

    public void setMediaController(IMediaController controller) {
        if (mMediaController != null) {
            mMediaController.hide();
        }
        mMediaController = controller;
        attachMediaController();
    }

    private void attachMediaController() {
        if (mMediaPlayer != null && mMediaController != null) {
            mMediaController.setMediaPlayer(this);
            View anchorView = this.getParent() instanceof View ?
                    (View) this.getParent() : this;
            mMediaController.setAnchorView(anchorView);
            mMediaController.setEnabled(isInPlaybackState());
        }
    }

    IMediaPlayer.OnVideoSizeChangedListener mSizeChangedListener =
            new IMediaPlayer.OnVideoSizeChangedListener() {
                public void onVideoSizeChanged(IMediaPlayer mp, int width, int height, int sarNum, int sarDen) {
                    mVideoWidth = mp.getVideoWidth();
                    mVideoHeight = mp.getVideoHeight();
                    mVideoSarNum = mp.getVideoSarNum();
                    mVideoSarDen = mp.getVideoSarDen();
                    if (mVideoWidth != 0 && mVideoHeight != 0) {
                        if (mRenderView != null) {
                            mRenderView.setVideoSize(mVideoWidth, mVideoHeight);
                            mRenderView.setVideoSampleAspectRatio(mVideoSarNum, mVideoSarDen);
                        }
                        // REMOVED: getHolder().setFixedSize(mVideoWidth, mVideoHeight);
                        requestLayout();
                    }
                }
            };

    IMediaPlayer.OnPreparedListener mPreparedListener = new IMediaPlayer.OnPreparedListener() {
        public void onPrepared(IMediaPlayer mp) {
            mPrepareEndTime = System.currentTimeMillis();
            if (mHudViewHolder != null)
                mHudViewHolder.updateLoadCost(mPrepareEndTime - mPrepareStartTime);
            mCurrentState = STATE_PREPARED;

            rotation180 = false;

            // Get the capabilities of the player for this stream
            // REMOVED: Metadata

            if (mOnPreparedListener != null) {
                mOnPreparedListener.onPrepared(IjkVideoView.this);
            }
            if (mMediaController != null) {
                mMediaController.setEnabled(true);
            }
            mVideoWidth = mp.getVideoWidth();
            mVideoHeight = mp.getVideoHeight();
            int seekToPosition = mSeekWhenPrepared;  // mSeekWhenPrepared may be changed after seekTo() call
            if (seekToPosition != 0) {
                seekTo(seekToPosition);
            }
            if (mVideoWidth != 0 && mVideoHeight != 0) {
                //Log.i("@@@@", "video size: " + mVideoWidth +"/"+ mVideoHeight);
                // REMOVED: getHolder().setFixedSize(mVideoWidth, mVideoHeight);
                if (mRenderView != null) {
                    mRenderView.setVideoSize(mVideoWidth, mVideoHeight);
                    mRenderView.setVideoSampleAspectRatio(mVideoSarNum, mVideoSarDen);
                    if (!mRenderView.shouldWaitForResize() || mSurfaceWidth == mVideoWidth && mSurfaceHeight == mVideoHeight) {
                        // We didn't actually change the size (it was already at the size
                        // we need), so we won't get a "surface changed" callback, so
                        // start the video here instead of in the callback.
                        if (mTargetState == STATE_PLAYING) {
                            start();
                            if (mMediaController != null) {
                                mMediaController.show();
                            }
                        } else if (!isPlaying() &&
                                (seekToPosition != 0 || getCurrentPosition() > 0)) {
                            if (mMediaController != null) {
                                // Show the media controls when we're paused into a video and make 'em stick.
                                mMediaController.show(0);
                            }
                        }
                    }
                }
            } else {
                // We don't know the video size yet, but should start anyway.
                // The video size might be reported to us later.
                if (mTargetState == STATE_PLAYING) {
                    start();
                }
            }
        }
    };

    private IMediaPlayer.OnCompletionListener mCompletionListener =
            new IMediaPlayer.OnCompletionListener() {
                public void onCompletion(IMediaPlayer mp) {
                    mCurrentState = STATE_PLAYBACK_COMPLETED;
                    mTargetState = STATE_PLAYBACK_COMPLETED;
                    if (mMediaController != null) {
                        mMediaController.hide();
                    }
                    if (mOnCompletionListener != null) {
                        mOnCompletionListener.onCompletion(IjkVideoView.this);
                    }
                }
            };

    private IMediaPlayer.OnInfoListener mInfoListener =
            new IMediaPlayer.OnInfoListener() {
                public boolean onInfo(IMediaPlayer mp, int arg1, int arg2) {
                    if (mOnInfoListener != null) {
                        mOnInfoListener.onInfo(IjkVideoView.this, arg1, arg2);
                    }
                    switch (arg1) {
                        case IMediaPlayer.MEDIA_INFO_VIDEO_TRACK_LAGGING:
                            Log.d(TAG, "MEDIA_INFO_VIDEO_TRACK_LAGGING:");
                            break;
                        case IMediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START:
                            Log.d(TAG, "MEDIA_INFO_VIDEO_RENDERING_START:");
                            break;
                        case IMediaPlayer.MEDIA_INFO_BUFFERING_START:
                            Log.d(TAG, "MEDIA_INFO_BUFFERING_START:");
                            break;
                        case IMediaPlayer.MEDIA_INFO_BUFFERING_END:
                            Log.d(TAG, "MEDIA_INFO_BUFFERING_END:");
                            break;
                        case IMediaPlayer.MEDIA_INFO_NETWORK_BANDWIDTH:
                            Log.d(TAG, "MEDIA_INFO_NETWORK_BANDWIDTH: " + arg2);
                            break;
                        case IMediaPlayer.MEDIA_INFO_BAD_INTERLEAVING:
                            Log.d(TAG, "MEDIA_INFO_BAD_INTERLEAVING:");
                            break;
                        case IMediaPlayer.MEDIA_INFO_NOT_SEEKABLE:
                            Log.d(TAG, "MEDIA_INFO_NOT_SEEKABLE:");
                            break;
                        case IMediaPlayer.MEDIA_INFO_METADATA_UPDATE:
                            Log.d(TAG, "MEDIA_INFO_METADATA_UPDATE:");
                            break;
                        case IMediaPlayer.MEDIA_INFO_UNSUPPORTED_SUBTITLE:
                            Log.d(TAG, "MEDIA_INFO_UNSUPPORTED_SUBTITLE:");
                            break;
                        case IMediaPlayer.MEDIA_INFO_SUBTITLE_TIMED_OUT:
                            Log.d(TAG, "MEDIA_INFO_SUBTITLE_TIMED_OUT:");
                            break;
                        case IMediaPlayer.MEDIA_INFO_VIDEO_ROTATION_CHANGED:
                            mVideoRotationDegree = arg2;
                            Log.d(TAG, "MEDIA_INFO_VIDEO_ROTATION_CHANGED: " + arg2);
                            if (mRenderView != null)
                                mRenderView.setVideoRotation(arg2);
                            break;
                        case IMediaPlayer.MEDIA_INFO_AUDIO_RENDERING_START:
                            Log.d(TAG, "MEDIA_INFO_AUDIO_RENDERING_START:");
                            break;
                    }
                    return true;
                }
            };

    private IMediaPlayer.OnErrorListener mErrorListener =
            new IMediaPlayer.OnErrorListener() {
                public boolean onError(IMediaPlayer mp, int framework_err, int impl_err) {
                    Log.d(TAG, "Error: " + framework_err + "," + impl_err);
                    mCurrentState = STATE_ERROR;
                    mTargetState = STATE_ERROR;
                    if (mMediaController != null) {
                        mMediaController.hide();
                    }

                    /* If an error handler has been supplied, use it and finish. */
                    if (mOnErrorListener != null) {
                        if (mOnErrorListener.onError(IjkVideoView.this, framework_err, impl_err)) {
                            return true;
                        }
                    }

                    /* Otherwise, pop up an error dialog so the user knows that
                     * something bad has happened. Only try and pop up the dialog
                     * if we're attached to a window. When we're going away and no
                     * longer have a window, don't bother showing the user an error.
                     */
                    if (getWindowToken() != null) {
                        Resources r = mAppContext.getResources();
                        int messageId;

                        if (framework_err == MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK) {
                            messageId = R.string.VideoView_error_text_invalid_progressive_playback;
                        } else {
                            messageId = R.string.VideoView_error_text_unknown;
                        }

                        new AlertDialog.Builder(getContext())
                                .setMessage(messageId)
                                .setPositiveButton(R.string.VideoView_error_button,
                                        new DialogInterface.OnClickListener() {
                                            public void onClick(DialogInterface dialog, int whichButton) {
                                            /* If we get here, there is no onError listener, so
                                             * at least inform them that the video is over.
                                             */
                                                if (mOnCompletionListener != null) {
                                                    mOnCompletionListener.onCompletion(IjkVideoView.this);
                                                }
                                            }
                                        })
                                .setCancelable(false)
                                .show();
                    }
                    return true;
                }
            };

    private IMediaPlayer.OnBufferingUpdateListener mBufferingUpdateListener =
            new IMediaPlayer.OnBufferingUpdateListener() {
                public void onBufferingUpdate(IMediaPlayer mp, int percent) {
                    mCurrentBufferPercentage = percent;
                }
            };

    private IMediaPlayer.OnSeekCompleteListener mSeekCompleteListener = new IMediaPlayer.OnSeekCompleteListener() {

        @Override
        public void onSeekComplete(IMediaPlayer mp) {
            mSeekEndTime = System.currentTimeMillis();
            if (mHudViewHolder != null)
                mHudViewHolder.updateSeekCost(mSeekEndTime - mSeekStartTime);
        }
    };

    private IMediaPlayer.OnTimedTextListener mOnTimedTextListener = new IMediaPlayer.OnTimedTextListener() {
        @Override
        public void onTimedText(IMediaPlayer mp, IjkTimedText text) {
            if (text != null) {
                subtitleDisplay.setText(text.getText());
            }
        }
    };

    private IMediaPlayer.OnReceivedRtcpSrDataListener mReceivedRtcpSrDataListener = new IMediaPlayer.OnReceivedRtcpSrDataListener() {
        @Override
        public void onReceivedRtcpSrData(IMediaPlayer mp, byte[] data) {
            if (mOnReceivedRtcpSrDataListener != null)
                mOnReceivedRtcpSrDataListener.onReceivedRtcpSrData(IjkVideoView.this, data);
            if(true)
return;
            if (mOnReceivedDataListener != null) {
                if (data.length > 4
                        && data[0] == DATA_SIGNATURE_BYTE_1
                        && data[1] == DATA_SIGNATURE_BYTE_2
                        && data[2] == DATA_SIGNATURE_BYTE_3
                        && data[3] == DATA_SIGNATURE_BYTE_4) {
                    int len = (data[4] + 256) & 0xFF;

                    if (len > 0 && data.length - 5 >= len) {
                        byte[] d = Arrays.copyOfRange(data, 5, 5 + len);
                        mOnReceivedDataListener.onReceivedData(IjkVideoView.this, d);
                    } else if (len == 0) {
                        Log.d(TAG, "onReceivedData: empty message\n");
                    }
                }
            }
        }
    };

    private IMediaPlayer.OnTookPictureListener mTookPictureListener = new IMediaPlayer.OnTookPictureListener() {
        @Override
        public void onTookPicture(IMediaPlayer mp, int resultCode, String fileName) {
            if (mOnTookPictureListener != null)
                mOnTookPictureListener.onTookPicture(IjkVideoView.this, resultCode, fileName);
        }
    };

    private IMediaPlayer.OnRecordVideoListener mRecordVideoListener = new IMediaPlayer.OnRecordVideoListener() {
        @Override
        public void onRecordVideo(IMediaPlayer mp, int resultCode, String fileName) {
            if (mOnRecordVideoListener != null)
                mOnRecordVideoListener.onRecordVideo(IjkVideoView.this, resultCode, fileName);
        }
    };

    private IMediaPlayer.OnInsertVideoListener mInsertVideoListener = new IMediaPlayer.OnInsertVideoListener() {
        @Override
        public void onInsertVideo(IMediaPlayer mp, int resultCode) {
            if (mOnInsertVideoListener != null)
                mOnInsertVideoListener.onInsertVideo(IjkVideoView.this, resultCode);
        }
    };

    private IMediaPlayer.OnReceivedFrameListener mReceivedFrameListener = new IMediaPlayer.OnReceivedFrameListener() {
        @Override
        public void onReceivedFrame(IMediaPlayer mp, byte[] data, int width, int height, int pixelFormat) {

            if (mOnReceivedFrameListener != null){
                mOnReceivedFrameListener.onReceivedFrame(IjkVideoView.this, data, width, height, pixelFormat);}
        }
    };

    private IMediaPlayer.OnReceivedOriginalDataListener mReceivedOriginalDataListener = new IMediaPlayer.OnReceivedOriginalDataListener() {
        @Override
        public void onReceivedOriginalData(IMediaPlayer mp, byte[] data, int width, int height, int pixelFormat, int videoId,int degree) {

            if (mOnReceivedOriginalDataListener != null){

                mOnReceivedOriginalDataListener.onReceivedOriginalData(IjkVideoView.this, data, width, height, pixelFormat, videoId,degree);
            }
        }
    };

    private IMediaPlayer.OnDeviceConnectedListener mDeviceConnectedListener = new IMediaPlayer.OnDeviceConnectedListener() {
        @Override
        public void onDeviceConnected(IMediaPlayer mp) {
            if (mOnDeviceConnectedListener != null)
                mOnDeviceConnectedListener.onDeviceConnected(IjkVideoView.this);
        }
    };

    /**
     * Register a callback to be invoked when the media file
     * is loaded and ready to go.
     *
     * @param l The callback that will be run
     */
    public void setOnPreparedListener(IVideoView.OnPreparedListener l) {
        mOnPreparedListener = l;
    }

    /**
     * Register a callback to be invoked when the end of a media file
     * has been reached during playback.
     *
     * @param l The callback that will be run
     */
    public void setOnCompletionListener(IVideoView.OnCompletionListener l) {
        mOnCompletionListener = l;
    }

    /**
     * Register a callback to be invoked when an error occurs
     * during playback or setup.  If no listener is specified,
     * or if the listener returned false, VideoView will inform
     * the user of any errors.
     *
     * @param l The callback that will be run
     */
    public void setOnErrorListener(IVideoView.OnErrorListener l) {
        mOnErrorListener = l;
    }

    /**
     * Register a callback to be invoked when an informational event
     * occurs during playback or setup.
     *
     * @param l The callback that will be run
     */
    public void setOnInfoListener(IVideoView.OnInfoListener l) {
        mOnInfoListener = l;
    }

    /**
     * Register a callback to receive RTCP SR data
     * Wrapped data packet identification
     *
     * @param l The callback that will be run
     */
    public void setOnReceivedRtcpSrDataListener(IVideoView.OnReceivedRtcpSrDataListener l) {
        mOnReceivedRtcpSrDataListener = l;
    }

    /**
     * Register a callback to receive data
     *
     * @param l The callback that will be run
     */
    public void setOnReceivedDataListener(IVideoView.OnReceivedDataListener l) {
        mOnReceivedDataListener = l;
    }

    public void setOnTookPictureListener(IVideoView.OnTookPictureListener l) {
        mOnTookPictureListener = l;
    }

    public void setOnRecordVideoListener(IVideoView.OnRecordVideoListener l) {
        mOnRecordVideoListener = l;
    }

    public void setOnInsertVideoListener(IVideoView.OnInsertVideoListener l) {
        mOnInsertVideoListener = l;
    }

    public void setOnReceivedFrameListener(IVideoView.OnReceivedFrameListener l) {
        mOnReceivedFrameListener = l;
    }

    public void setOnReceivedOriginalDataListener(IVideoView.OnReceivedOriginalDataListener l) {
        mOnReceivedOriginalDataListener = l;
    }

    public void setOnDeviceConnectedListener(IVideoView.OnDeviceConnectedListener l) {
        mOnDeviceConnectedListener = l;
    }

    // REMOVED: mSHCallback
    private void bindSurfaceHolder(IMediaPlayer mp, IRenderView.ISurfaceHolder holder) {
        if (mp == null)
            return;

        if (holder == null) {
            mp.setDisplay(null);
            return;
        }

        holder.bindToMediaPlayer(mp);
    }

    IRenderView.IRenderCallback mSHCallback = new IRenderView.IRenderCallback() {
        @Override
        public void onSurfaceChanged(@NonNull IRenderView.ISurfaceHolder holder, int format, int w, int h) {
            if (holder.getRenderView() != mRenderView) {
                Log.e(TAG, "onSurfaceChanged: unmatched render callback\n");
                return;
            }

            mSurfaceWidth = w;
            mSurfaceHeight = h;
            boolean isValidState = (mTargetState == STATE_PLAYING);
            boolean hasValidSize = !mRenderView.shouldWaitForResize() || (mVideoWidth == w && mVideoHeight == h);
            if (mMediaPlayer != null && isValidState && hasValidSize) {
                if (mSeekWhenPrepared != 0) {
                    seekTo(mSeekWhenPrepared);
                }
                start();
            }
        }

        @Override
        public void onSurfaceCreated(@NonNull IRenderView.ISurfaceHolder holder, int width, int height) {
            if (holder.getRenderView() != mRenderView) {
                Log.e(TAG, "onSurfaceCreated: unmatched render callback\n");
                return;
            }

            mSurfaceHolder = holder;
            if (mMediaPlayer != null)
                bindSurfaceHolder(mMediaPlayer, holder);
            else
                openVideo();
        }

        @Override
        public void onSurfaceDestroyed(@NonNull IRenderView.ISurfaceHolder holder) {
            if (holder.getRenderView() != mRenderView) {
                Log.e(TAG, "onSurfaceDestroyed: unmatched render callback\n");
                return;
            }

            // after we return from this we can't use the surface any more
            mSurfaceHolder = null;
            // REMOVED: if (mMediaController != null) mMediaController.hide();
            // REMOVED: release(true);
            releaseWithoutStop();
        }
    };

    public void releaseWithoutStop() {
        if (mMediaPlayer != null)
            mMediaPlayer.setDisplay(null);
    }

    /*
     * release the media player in any state
     */
    public void release(boolean cleartargetstate) {
        if (mMediaPlayer != null) {
            mMediaPlayer.reset();
            mMediaPlayer.release();
            mMediaPlayer = null;
            // REMOVED: mPendingSubtitleTracks.clear();
            mCurrentState = STATE_IDLE;
            if (cleartargetstate) {
                mTargetState = STATE_IDLE;
            }
            AudioManager am = (AudioManager) mAppContext.getSystemService(Context.AUDIO_SERVICE);
            am.abandonAudioFocus(null);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (isInPlaybackState() && mMediaController != null) {
            toggleMediaControlsVisiblity();
        }
        return false;
    }

    @Override
    public boolean onTrackballEvent(MotionEvent ev) {
        if (isInPlaybackState() && mMediaController != null) {
            toggleMediaControlsVisiblity();
        }
        return false;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        boolean isKeyCodeSupported = keyCode != KeyEvent.KEYCODE_BACK &&
                keyCode != KeyEvent.KEYCODE_VOLUME_UP &&
                keyCode != KeyEvent.KEYCODE_VOLUME_DOWN &&
                keyCode != KeyEvent.KEYCODE_VOLUME_MUTE &&
                keyCode != KeyEvent.KEYCODE_MENU &&
                keyCode != KeyEvent.KEYCODE_CALL &&
                keyCode != KeyEvent.KEYCODE_ENDCALL;
        if (isInPlaybackState() && isKeyCodeSupported && mMediaController != null) {
            if (keyCode == KeyEvent.KEYCODE_HEADSETHOOK ||
                    keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                if (mMediaPlayer.isPlaying()) {
                    pause();
                    mMediaController.show();
                } else {
                    start();
                    mMediaController.hide();
                }
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY) {
                if (!mMediaPlayer.isPlaying()) {
                    start();
                    mMediaController.hide();
                }
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_MEDIA_STOP
                    || keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE) {
                if (mMediaPlayer.isPlaying()) {
                    pause();
                    mMediaController.show();
                }
                return true;
            } else {
                toggleMediaControlsVisiblity();
            }
        }

        return super.onKeyDown(keyCode, event);
    }

    private void toggleMediaControlsVisiblity() {
        if (mMediaController.isShowing()) {
            mMediaController.hide();
        } else {
            mMediaController.show();
        }
    }

    @Override
    public void start() {
        if (isInPlaybackState()) {
            mMediaPlayer.start();
            mCurrentState = STATE_PLAYING;
        }
        mTargetState = STATE_PLAYING;
    }

    @Override
    public void pause() {
        if (isInPlaybackState()) {
            if (mMediaPlayer.isPlaying()) {
                mMediaPlayer.pause();
                mCurrentState = STATE_PAUSED;
            }
        }
        mTargetState = STATE_PAUSED;
    }

    public void suspend() {
        release(false);
    }

    public void resume() {
        openVideo();
    }

    @Override
    public int getDuration() {
        if (isInPlaybackState()) {
            return (int) mMediaPlayer.getDuration();
        }

        return -1;
    }

    @Override
    public int getCurrentPosition() {
        if (isInPlaybackState()) {
            return (int) mMediaPlayer.getCurrentPosition();
        }
        return 0;
    }

    @Override
    public void seekTo(int msec) {
        if (isInPlaybackState()) {
            mSeekStartTime = System.currentTimeMillis();
            mMediaPlayer.seekTo(msec);
            mSeekWhenPrepared = 0;
        } else {
            mSeekWhenPrepared = msec;
        }
    }

    @Override
    public boolean isPlaying() {
        return isInPlaybackState() && mMediaPlayer.isPlaying();
    }

    @Override
    public int getBufferPercentage() {
        if (mMediaPlayer != null) {
            return mCurrentBufferPercentage;
        }
        return 0;
    }

    private boolean isInPlaybackState() {
        return (mMediaPlayer != null &&
                mCurrentState != STATE_ERROR &&
                mCurrentState != STATE_IDLE &&
                mCurrentState != STATE_PREPARING);
    }

    @Override
    public boolean canPause() {
        return mCanPause;
    }

    @Override
    public boolean canSeekBackward() {
        return mCanSeekBack;
    }

    @Override
    public boolean canSeekForward() {
        return mCanSeekForward;
    }

    @Override
    public int getAudioSessionId() {
        return 0;
    }

    // REMOVED: getAudioSessionId();
    // REMOVED: onAttachedToWindow();
    // REMOVED: onDetachedFromWindow();
    // REMOVED: onLayout();
    // REMOVED: draw();
    // REMOVED: measureAndLayoutSubtitleWidget();
    // REMOVED: setSubtitleWidget();
    // REMOVED: getSubtitleLooper();

    /**
     * 从IMediaPlayer实例中返回IjkMediaPlayer实例
     * @param player IMediaPlayer实例
     * @return 如果IMediaPlayer实例是IjkMediaPlayer实例，则返回，否则，返回null
     */
    IjkMediaPlayer ijkplayerInstance(IMediaPlayer player) {
        if (player instanceof IjkMediaPlayer)
            return (IjkMediaPlayer)player;

        return null;
    }

    /**
     * 创建并运行Background Task
     * @param name 任务名
     * @param r    Runnable
     */
    private void doBackgroundTask(String name, Runnable r) {
        HandlerThread handlerThread = new HandlerThread(name);
        handlerThread.start();
        Handler handler = new Handler(handlerThread.getLooper());
        handler.post(r);
    }

    /**
     * 从RTCP通道发送数据到设备，注意和原RTCP数据做区分
     * @param data 数据
     */
    public void sendRtcpRrData(byte[] data) throws IllegalStateException {
        if (mMediaPlayer != null && isPlaying())
            mMediaPlayer.sendRtcpRrData(data);
    }

    private byte[] _data = null;

    public void sendData(byte[] data) throws IllegalStateException {
        if (mMediaPlayer != null && isPlaying()) {
            if (_data == null
                    || _data.length != data.length + 3) {
                _data = new byte[data.length + 3];
            }

            byte c = 0x00;
            for (byte b : data)
                c ^= b;
            c = (byte)~c;

            _data[0] = (byte)0x66;
            System.arraycopy(data, 0, _data, 1, data.length);
            _data[data.length + 1] = c;
            _data[data.length + 2] = (byte)0x99;
            mMediaPlayer.sendRtcpRrData(_data);
        }
    }

    /**
     * 拍照
     * 宽高同时为-1，则使用原始分辨率保存照片，否则使用指定的尺寸，宽高不能有一个单独是-1
     * @param path      目录路径
     * @param fileName  文件名
     * @param width     宽度
     * @param height    高度
     * @param number    连拍数量
     */
    public void takePicture(final String path, final String fileName, final int width, final int height, final int number)
            throws IOException, IllegalArgumentException, SecurityException, IllegalStateException {
        if (mMediaPlayer != null && isPlaying())
            doBackgroundTask("startRecordVideo", new Runnable() {
                @Override
                public void run() {
                    try {
                        mMediaPlayer.takePicture(path, fileName, width, height, number);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
    }

    /**
     * 开始录像
     * 宽高同时为-1，则使用原始分辨率保存录像，否则使用指定的尺寸，宽高不能有一个单独是-1
     * @param path      目录路径
     * @param fileName  文件名
     * @param width     宽度
     * @param height    高度
     */
    public void startRecordVideo(final String path, final String fileName, final int width, final int height)
            throws IOException, IllegalArgumentException, SecurityException, IllegalStateException {
        if (mMediaPlayer != null && isPlaying())
            doBackgroundTask("startRecordVideo", new Runnable() {
                @Override
                public void run() {
                    try {
                        mMediaPlayer.startRecordVideo(path, fileName, width, height);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
    }

    /**
     * 停止录像
     */
    public void stopRecordVideo() throws IllegalStateException {
        if (mMediaPlayer != null)
            doBackgroundTask("startRecordVideo", new Runnable() {
                @Override
                public void run() {
                    try {
                        mMediaPlayer.stopRecordVideo();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
    }

    /**
     * 开始录像，但并不写入WiFi数据流，为插入外部视频流做准备，其余效果同方法startRecordVideo
     * @param path      目录路径
     * @param fileName  文件名
     * @param width     宽度
     * @param height    高度
     */
    public void prestartInsertVideo(final String path, final String fileName, final int width, final int height)
            throws IOException, IllegalArgumentException, SecurityException, IllegalStateException {
        IjkMediaPlayer player = ijkplayerInstance(mMediaPlayer);
        if (player != null)
            player.prestartInsertVideo(path, fileName, width, height);
    }

    /**
     * 准备开始在WiFi录像中插入视频流
     * @param width     插入图像宽度
     * @param height    插入图像高度
     * @param pix_fmt   插入图像像素格式，使用FFmpeg的枚举
     */
    public void startInsertVideo(final int width, final int height, final int pix_fmt)
            throws IllegalStateException {
        if (mMediaPlayer != null && isPlaying())
            doBackgroundTask("startInsertVideo", new Runnable() {
                @Override
                public void run() {
                    try {
                        mMediaPlayer.startInsertVideo(width, height, pix_fmt);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
    }

    /**
     * 插入图像的数据
     * @param data  数据
     * @param align 对齐
     * @param copy  是否复制数据，如果不复制，在data使用完成后由程序清除
     */
    public void insertVideoData(final byte[] data, final int align, final boolean copy)
            throws IllegalStateException {
        if (mMediaPlayer != null && isPlaying())
            doBackgroundTask("insertVideoData", new Runnable() {
                @Override
                public void run() {
                    try {
                        mMediaPlayer.insertVideoData(data, align, copy);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
    }

    /**
     * 停止插入图像
     */
    public void stopInsertVideo()
            throws IllegalStateException {
        if (mMediaPlayer != null && isPlaying())
            doBackgroundTask("stopInsertVideo", new Runnable() {
                @Override
                public void run() {
                    try {
                        mMediaPlayer.stopInsertVideo();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
    }

    /**
     * 输出视频帧
     */
    public void setOutputVideo(final boolean enable)
            throws IllegalStateException {
        IjkMediaPlayer player = ijkplayerInstance(mMediaPlayer);
        if (player != null)
            mMediaPlayer.setOutputVideo(enable);
    }

    /**
     * 输出原始视频帧数据
     */
    public void setOutputOriginalVideo(final boolean enable)
            throws IllegalStateException {
        IjkMediaPlayer player = ijkplayerInstance(mMediaPlayer);
        if (player != null)
            player.setOutputOriginalVideo(enable);
    }

    /* VR模式，左右眼两个图 */

    public boolean isVrMode() {
        return vrMode;
    }

    public void setVrMode(boolean vrMode) throws IllegalStateException {
        if (mMediaPlayer != null && mMediaPlayer.setVrMode(vrMode))
            this.vrMode = !this.vrMode;
    }

    /**
     * 设置视频旋转
     * @param degree 旋转角度
     */
    public void setVideoRotation(int degree) {
        if (mRenderView != null)
            mRenderView.setVideoRotation(degree);
    }

    /* 旋转180度，使用这个逻辑是为了保持和原来的兼容性 */

    public boolean isRotation180() {
        return rotation180;
    }

    public void setRotation180(boolean rotation180) {
        IjkMediaPlayer player = ijkplayerInstance(mMediaPlayer);
        if (player != null) {
            player.setRotation180(rotation180);
            this.rotation180 = rotation180;
        }
    }

    /* 设置纹理坐标，OpenGL专用 */

    public void setScreenCoordRect(float left, float top, float right, float bottom)
            throws IllegalStateException {
        if (mMediaPlayer != null)
            // Convert screen coordinates to texture coordinates (Inverse Y axis)
            mMediaPlayer.setTexcoordRect(left, bottom, right, top);
    }

    public void resetScreenCoordRect() {
        setScreenCoordRect(0.f, 0.f, 1.f, 1.f);
    }

    /* filter */

    public void setVideoFilter(String nodeName, String filterName, String filterArg, boolean enable) {
        IjkMediaPlayer player = ijkplayerInstance(mMediaPlayer);
        if (player != null)
            player.setVideoFilter(nodeName, filterName, filterArg, enable);
    }

    //-------------------------
    // Extend: Interface
    //-------------------------

    public interface IVideoView {

        void setOnPreparedListener(OnPreparedListener listener);

        void setOnCompletionListener(OnCompletionListener listener);

        void setOnErrorListener(OnErrorListener listener);

        void setOnInfoListener(OnInfoListener listener);

        void setOnReceivedRtcpSrDataListener(OnReceivedRtcpSrDataListener listener);

        void setOnReceivedDataListener(OnReceivedDataListener listener);

        void setOnTookPictureListener(OnTookPictureListener listener);

        void setOnRecordVideoListener(OnRecordVideoListener listener);

        void setOnInsertVideoListener(OnInsertVideoListener listener);

        void setOnReceivedFrameListener(OnReceivedFrameListener listener);

        void setOnReceivedOriginalDataListener(OnReceivedOriginalDataListener listener);

        void setOnDeviceConnectedListener(OnDeviceConnectedListener listener);

        /*--------------------
         * Listeners
         */
        interface OnPreparedListener {
            void onPrepared(IjkVideoView videoView);
        }

        interface OnCompletionListener {
            void onCompletion(IjkVideoView videoView);
        }

        interface OnErrorListener {
            boolean onError(IjkVideoView videoView, int what, int extra);
        }

        interface OnInfoListener {
            boolean onInfo(IjkVideoView videoView, int what, int extra);
        }

        interface OnReceivedRtcpSrDataListener {
            void onReceivedRtcpSrData(IjkVideoView videoView, byte[] data);
        }

        interface OnReceivedDataListener {
            void onReceivedData(IjkVideoView videoView, byte[] data);
        }

        interface OnTookPictureListener {
            void onTookPicture(IjkVideoView videoView, int resultCode, String fileName);
        }

        interface OnRecordVideoListener {
            void onRecordVideo(IjkVideoView videoView, int resultCode, String fileName);
        }

        interface OnInsertVideoListener {
            void onInsertVideo(IjkVideoView videoView, int resultCode);
        }

        interface OnReceivedFrameListener {
            void onReceivedFrame(IjkVideoView videoView, byte[] data, int width, int height, int pixelFormat);
        }

        interface OnReceivedOriginalDataListener {
            void onReceivedOriginalData(IjkVideoView videoView, byte[] data, int width, int height, int pixelFormat, int videoId,int degree);
        }

        interface OnDeviceConnectedListener {
            void onDeviceConnected(IjkVideoView videoView);
        }
    }

    //-------------------------
    // Extend: Aspect Ratio
    //-------------------------

    private static final int[] s_allAspectRatio = {
            IRenderView.AR_ASPECT_FIT_PARENT,
            IRenderView.AR_ASPECT_FILL_PARENT,
            IRenderView.AR_ASPECT_WRAP_CONTENT,
            // IRenderView.AR_MATCH_PARENT,
            IRenderView.AR_16_9_FIT_PARENT,
            IRenderView.AR_4_3_FIT_PARENT,
    };
    private int mCurrentAspectRatioIndex = 0;
    private int mCurrentAspectRatio = s_allAspectRatio[0];

    public int toggleAspectRatio() {
        mCurrentAspectRatioIndex++;
        mCurrentAspectRatioIndex %= s_allAspectRatio.length;

        mCurrentAspectRatio = s_allAspectRatio[mCurrentAspectRatioIndex];
        if (mRenderView != null)
            mRenderView.setAspectRatio(mCurrentAspectRatio);
        return mCurrentAspectRatio;
    }

    public void setAspectRatio(int aspectRatio) {
        for (int i = 0; i < s_allAspectRatio.length; i++)
            if (aspectRatio == s_allAspectRatio[i]) {
                mCurrentAspectRatioIndex = i;
                break;
            }

        mCurrentAspectRatio = aspectRatio;
        if (mRenderView != null)
            mRenderView.setAspectRatio(aspectRatio);
    }

    //-------------------------
    // Extend: Render
    //-------------------------
    public static final int RENDER_NONE = 0;
    public static final int RENDER_SURFACE_VIEW = 1;
    public static final int RENDER_TEXTURE_VIEW = 2;

    private int mCurrentRender = RENDER_NONE;

    private void initRenders() {
        mCurrentRender = RENDER_SURFACE_VIEW;
        setRender(mCurrentRender);
    }

    //-------------------------
    // Extend: Player
    //-------------------------
    public IMediaPlayer createPlayer() {
        IMediaPlayer mediaPlayer = null;

        IjkMediaPlayer ijkMediaPlayer = null;
        if (mUri != null) {
            ijkMediaPlayer = new IjkMediaPlayer();
            ijkMediaPlayer.native_setLogLevel(IJK_LOG_LEVEL);

            if (mOptions != null)
                mOptions.applyToMediaPlayer(ijkMediaPlayer);
        }
        mediaPlayer = ijkMediaPlayer;

//        if (mSettings.getEnableDetachedSurfaceTextureView()) {
//            mediaPlayer = new TextureMediaPlayer(mediaPlayer);
//        }

        return mediaPlayer;
    }

    //-------------------------
    // Extend: Background
    //-------------------------

    private boolean mEnableBackgroundPlay = false;

    private void initBackground() {
        mEnableBackgroundPlay = false;
        if (mEnableBackgroundPlay) {
            MediaPlayerService.intentToStart(getContext());
            mMediaPlayer = MediaPlayerService.getMediaPlayer();
            if (mHudViewHolder != null)
                mHudViewHolder.setMediaPlayer(mMediaPlayer);
        }
    }

    public boolean isBackgroundPlayEnabled() {
        return mEnableBackgroundPlay;
    }

    public void enterBackground() {
        MediaPlayerService.setMediaPlayer(mMediaPlayer);
    }

    public void stopBackgroundPlay() {
        MediaPlayerService.setMediaPlayer(null);
    }

    //-------------------------
    // Extend: Background
    //-------------------------
    public void showMediaInfo() {
        if (mMediaPlayer == null)
            return;

        int selectedVideoTrack = MediaPlayerCompat.getSelectedTrack(mMediaPlayer, ITrackInfo.MEDIA_TRACK_TYPE_VIDEO);
        int selectedAudioTrack = MediaPlayerCompat.getSelectedTrack(mMediaPlayer, ITrackInfo.MEDIA_TRACK_TYPE_AUDIO);
        int selectedSubtitleTrack = MediaPlayerCompat.getSelectedTrack(mMediaPlayer, ITrackInfo.MEDIA_TRACK_TYPE_TIMEDTEXT);

        TableLayoutBinder builder = new TableLayoutBinder(getContext());
        builder.appendSection(R.string.mi_player);
        builder.appendRow2(R.string.mi_player, MediaPlayerCompat.getName(mMediaPlayer));
        builder.appendSection(R.string.mi_media);
        builder.appendRow2(R.string.mi_resolution, buildResolution(mVideoWidth, mVideoHeight, mVideoSarNum, mVideoSarDen));
        builder.appendRow2(R.string.mi_length, buildTimeMilli(mMediaPlayer.getDuration()));

        ITrackInfo trackInfos[] = mMediaPlayer.getTrackInfo();
        if (trackInfos != null) {
            int index = -1;
            for (ITrackInfo trackInfo : trackInfos) {
                index++;

                int trackType = trackInfo.getTrackType();
                if (index == selectedVideoTrack) {
                    builder.appendSection(getContext().getString(R.string.mi_stream_fmt1, index) + " " + getContext().getString(R.string.mi__selected_video_track));
                } else if (index == selectedAudioTrack) {
                    builder.appendSection(getContext().getString(R.string.mi_stream_fmt1, index) + " " + getContext().getString(R.string.mi__selected_audio_track));
                } else if (index == selectedSubtitleTrack) {
                    builder.appendSection(getContext().getString(R.string.mi_stream_fmt1, index) + " " + getContext().getString(R.string.mi__selected_subtitle_track));
                } else {
                    builder.appendSection(getContext().getString(R.string.mi_stream_fmt1, index));
                }
                builder.appendRow2(R.string.mi_type, buildTrackType(trackType));
                builder.appendRow2(R.string.mi_language, buildLanguage(trackInfo.getLanguage()));

                IMediaFormat mediaFormat = trackInfo.getFormat();
                if (mediaFormat == null) {
                } else if (mediaFormat instanceof IjkMediaFormat) {
                    switch (trackType) {
                        case ITrackInfo.MEDIA_TRACK_TYPE_VIDEO:
                            builder.appendRow2(R.string.mi_codec, mediaFormat.getString(IjkMediaFormat.KEY_IJK_CODEC_LONG_NAME_UI));
                            builder.appendRow2(R.string.mi_profile_level, mediaFormat.getString(IjkMediaFormat.KEY_IJK_CODEC_PROFILE_LEVEL_UI));
                            builder.appendRow2(R.string.mi_pixel_format, mediaFormat.getString(IjkMediaFormat.KEY_IJK_CODEC_PIXEL_FORMAT_UI));
                            builder.appendRow2(R.string.mi_resolution, mediaFormat.getString(IjkMediaFormat.KEY_IJK_RESOLUTION_UI));
                            builder.appendRow2(R.string.mi_frame_rate, mediaFormat.getString(IjkMediaFormat.KEY_IJK_FRAME_RATE_UI));
                            builder.appendRow2(R.string.mi_bit_rate, mediaFormat.getString(IjkMediaFormat.KEY_IJK_BIT_RATE_UI));
                            break;
                        case ITrackInfo.MEDIA_TRACK_TYPE_AUDIO:
                            builder.appendRow2(R.string.mi_codec, mediaFormat.getString(IjkMediaFormat.KEY_IJK_CODEC_LONG_NAME_UI));
                            builder.appendRow2(R.string.mi_profile_level, mediaFormat.getString(IjkMediaFormat.KEY_IJK_CODEC_PROFILE_LEVEL_UI));
                            builder.appendRow2(R.string.mi_sample_rate, mediaFormat.getString(IjkMediaFormat.KEY_IJK_SAMPLE_RATE_UI));
                            builder.appendRow2(R.string.mi_channels, mediaFormat.getString(IjkMediaFormat.KEY_IJK_CHANNEL_UI));
                            builder.appendRow2(R.string.mi_bit_rate, mediaFormat.getString(IjkMediaFormat.KEY_IJK_BIT_RATE_UI));
                            break;
                        default:
                            break;
                    }
                }
            }
        }

        AlertDialog.Builder adBuilder = builder.buildAlertDialogBuilder();
        adBuilder.setTitle(R.string.media_information);
        adBuilder.setNegativeButton(R.string.close, null);
        adBuilder.show();
    }

    private String buildResolution(int width, int height, int sarNum, int sarDen) {
        StringBuilder sb = new StringBuilder();
        sb.append(width);
        sb.append(" x ");
        sb.append(height);

        if (sarNum > 1 || sarDen > 1) {
            sb.append("[");
            sb.append(sarNum);
            sb.append(":");
            sb.append(sarDen);
            sb.append("]");
        }

        return sb.toString();
    }

    private String buildTimeMilli(long duration) {
        long total_seconds = duration / 1000;
        long hours = total_seconds / 3600;
        long minutes = (total_seconds % 3600) / 60;
        long seconds = total_seconds % 60;
        if (duration <= 0) {
            return "--:--";
        }
        if (hours >= 100) {
            return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
        } else if (hours > 0) {
            return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format(Locale.US, "%02d:%02d", minutes, seconds);
        }
    }

    private String buildTrackType(int type) {
        Context context = getContext();
        switch (type) {
            case ITrackInfo.MEDIA_TRACK_TYPE_VIDEO:
                return context.getString(R.string.TrackType_video);
            case ITrackInfo.MEDIA_TRACK_TYPE_AUDIO:
                return context.getString(R.string.TrackType_audio);
            case ITrackInfo.MEDIA_TRACK_TYPE_SUBTITLE:
                return context.getString(R.string.TrackType_subtitle);
            case ITrackInfo.MEDIA_TRACK_TYPE_TIMEDTEXT:
                return context.getString(R.string.TrackType_timedtext);
            case ITrackInfo.MEDIA_TRACK_TYPE_METADATA:
                return context.getString(R.string.TrackType_metadata);
            case ITrackInfo.MEDIA_TRACK_TYPE_UNKNOWN:
            default:
                return context.getString(R.string.TrackType_unknown);
        }
    }

    private String buildLanguage(String language) {
        if (TextUtils.isEmpty(language))
            return "und";
        return language;
    }

    public ITrackInfo[] getTrackInfo() {
        if (mMediaPlayer == null)
            return null;

        return mMediaPlayer.getTrackInfo();
    }

    public void selectTrack(int stream) {
        MediaPlayerCompat.selectTrack(mMediaPlayer, stream);
    }

    public void deselectTrack(int stream) {
        MediaPlayerCompat.deselectTrack(mMediaPlayer, stream);
    }

    public int getSelectedTrack(int trackType) {
        return MediaPlayerCompat.getSelectedTrack(mMediaPlayer, trackType);
    }
}
