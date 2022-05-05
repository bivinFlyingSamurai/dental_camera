/*
 * Copyright (C) 2013-2014 Bilibili
 * Copyright (C) 2013-2014 Zhang Rui <bbcallen@gmail.com>
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

package tv.danmaku.ijk.media.player;

import tv.danmaku.ijk.media.player.misc.IMediaDataSource;

@SuppressWarnings("WeakerAccess")
public abstract class AbstractMediaPlayer implements IMediaPlayer {
    private OnPreparedListener mOnPreparedListener;
    private OnCompletionListener mOnCompletionListener;
    private OnBufferingUpdateListener mOnBufferingUpdateListener;
    private OnSeekCompleteListener mOnSeekCompleteListener;
    private OnVideoSizeChangedListener mOnVideoSizeChangedListener;
    private OnErrorListener mOnErrorListener;
    private OnInfoListener mOnInfoListener;
    private OnTimedTextListener mOnTimedTextListener;
    private OnReceivedRtcpSrDataListener mOnReceivedRtcpSrDataListener;
    private OnTookPictureListener mOnTookPictureListener;
    private OnRecordVideoListener mOnRecordVideoListener;
    private OnInsertVideoListener mOnInsertVideoListener;
    private OnReceivedFrameListener mOnReceivedFrameListener;
    private OnReceivedOriginalDataListener mOnReceivedOriginalDataListener;
    private OnDeviceConnectedListener mOnDeviceConnectedListener;

    public final void setOnPreparedListener(OnPreparedListener listener) {
        mOnPreparedListener = listener;
    }

    public final void setOnCompletionListener(OnCompletionListener listener) {
        mOnCompletionListener = listener;
    }

    public final void setOnBufferingUpdateListener(
            OnBufferingUpdateListener listener) {
        mOnBufferingUpdateListener = listener;
    }

    public final void setOnSeekCompleteListener(OnSeekCompleteListener listener) {
        mOnSeekCompleteListener = listener;
    }

    public final void setOnVideoSizeChangedListener(
            OnVideoSizeChangedListener listener) {
        mOnVideoSizeChangedListener = listener;
    }

    public final void setOnErrorListener(OnErrorListener listener) {
        mOnErrorListener = listener;
    }

    public final void setOnInfoListener(OnInfoListener listener) {
        mOnInfoListener = listener;
    }

    public final void setOnTimedTextListener(OnTimedTextListener listener) {
        mOnTimedTextListener = listener;
    }

    public final void setOnReceivedRtcpSrDataListener(OnReceivedRtcpSrDataListener listener) {
        mOnReceivedRtcpSrDataListener = listener;
    }

    public final void setOnTookPictureListener(OnTookPictureListener listener) {
        mOnTookPictureListener = listener;
    }

    public final void setOnRecordVideoListener(OnRecordVideoListener listener) {
        mOnRecordVideoListener = listener;
    }

    public final void setOnInsertVideoListener(OnInsertVideoListener listener) {
        mOnInsertVideoListener = listener;
    }

    public final void setOnReceivedFrameListener(OnReceivedFrameListener listener) {
        mOnReceivedFrameListener = listener;
    }

    public final void setOnReceivedOriginalDataListener(OnReceivedOriginalDataListener listener) {
        mOnReceivedOriginalDataListener = listener;
    }

    public final void setOnDeviceConnectedListener(OnDeviceConnectedListener listener) {
        mOnDeviceConnectedListener = listener;
    }

    public void resetListeners() {
        mOnPreparedListener = null;
        mOnBufferingUpdateListener = null;
        mOnCompletionListener = null;
        mOnSeekCompleteListener = null;
        mOnVideoSizeChangedListener = null;
        mOnErrorListener = null;
        mOnInfoListener = null;
        mOnTimedTextListener = null;
        mOnReceivedRtcpSrDataListener = null;
        mOnTookPictureListener = null;
        mOnRecordVideoListener = null;
        mOnInsertVideoListener = null;
        mOnReceivedFrameListener = null;
        mOnReceivedOriginalDataListener = null;
        mOnDeviceConnectedListener = null;
    }

    protected final void notifyOnPrepared() {
        if (mOnPreparedListener != null)
            mOnPreparedListener.onPrepared(this);
    }

    protected final void notifyOnCompletion() {
        if (mOnCompletionListener != null)
            mOnCompletionListener.onCompletion(this);
    }

    protected final void notifyOnBufferingUpdate(int percent) {
        if (mOnBufferingUpdateListener != null)
            mOnBufferingUpdateListener.onBufferingUpdate(this, percent);
    }

    protected final void notifyOnSeekComplete() {
        if (mOnSeekCompleteListener != null)
            mOnSeekCompleteListener.onSeekComplete(this);
    }

    protected final void notifyOnVideoSizeChanged(int width, int height,
                                                  int sarNum, int sarDen) {
        if (mOnVideoSizeChangedListener != null)
            mOnVideoSizeChangedListener.onVideoSizeChanged(this, width, height,
                    sarNum, sarDen);
    }

    protected final boolean notifyOnError(int what, int extra) {
        return mOnErrorListener != null && mOnErrorListener.onError(this, what, extra);
    }

    protected final boolean notifyOnInfo(int what, int extra) {
        return mOnInfoListener != null && mOnInfoListener.onInfo(this, what, extra);
    }

    protected final void notifyOnTimedText(IjkTimedText text) {
        if (mOnTimedTextListener != null)
            mOnTimedTextListener.onTimedText(this, text);
    }

    protected final void notifyOnReceivedRtcpSrData(byte[] data) {
        if (mOnReceivedRtcpSrDataListener != null)
            mOnReceivedRtcpSrDataListener.onReceivedRtcpSrData(this, data);
    }

    protected final void notifyOnTookPicture(int resultCode, String fileName) {
        if (mOnTookPictureListener != null)
            mOnTookPictureListener.onTookPicture(this, resultCode, fileName);
    }

    protected final void notifyOnRecordVideo(int resultCode, String fileName) {
        if (mOnRecordVideoListener != null)
            mOnRecordVideoListener.onRecordVideo(this, resultCode, fileName);
    }

    protected final void notifyOnInsertVideo(int resultCode) {
        if (mOnInsertVideoListener != null)
            mOnInsertVideoListener.onInsertVideo(this, resultCode);
    }

    protected final void notifyOnReceivedFrame(byte[] data, int width, int height, int pixelFormat) {
        if (mOnReceivedFrameListener != null)
            mOnReceivedFrameListener.onReceivedFrame(this, data, width, height, pixelFormat);
    }

    protected final void notifyOnReceivedOriginalData(byte[] data, int width, int height, int pixelFormat, int videoId,int degree) {
        if (mOnReceivedOriginalDataListener != null)
            mOnReceivedOriginalDataListener.onReceivedOriginalData(this, data, width, height, pixelFormat, videoId,degree);
    }

    protected final void notifyOnDeviceConnected() {
        if (mOnDeviceConnectedListener != null)
            mOnDeviceConnectedListener.onDeviceConnected(this);
    }

    public void setDataSource(IMediaDataSource mediaDataSource) {
        throw new UnsupportedOperationException();
    }
}
