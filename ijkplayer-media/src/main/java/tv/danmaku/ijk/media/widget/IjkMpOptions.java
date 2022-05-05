package tv.danmaku.ijk.media.widget;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import tv.danmaku.ijk.media.player.IjkMediaPlayer;

public class IjkMpOptions {
    // RTP JPEG parse packet method
    public static final int RTP_JPEG_PARSE_PACKET_METHOD_ORIGIN = 0;
    public static final int RTP_JPEG_PARSE_PACKET_METHOD_DROP = 1;
    public static final int RTP_JPEG_PARSE_PACKET_METHOD_FILL = 2;

    // Preferred image type for taking pictures
    public static final int PREFERRED_IMAGE_TYPE_JPEG  = 0;
    public static final int PREFERRED_IMAGE_TYPE_PNG   = 1;

    // Preferred video type for recording video
    public static final int PREFERRED_VIDEO_TYPE_MJPEG = 0;
    public static final int PREFERRED_VIDEO_TYPE_MPEG4 = 1;
    public static final int PREFERRED_VIDEO_TYPE_H264  = 2;

    // MJPEG pixel format
    public static final int MJPEG_PIX_FMT_YUVJ420P     = 0;
    public static final int MJPEG_PIX_FMT_YUVJ422P     = 1;
    public static final int MJPEG_PIX_FMT_YUVJ444P     = 2;

    // x264 preset
    public static final int X264_PRESET_ULTRAFAST      = 0;
    public static final int X264_PRESET_SUPERFAST      = 1;
    public static final int X264_PRESET_VERYFAST       = 2;
    public static final int X264_PRESET_FASTER         = 3;
    public static final int X264_PRESET_FAST           = 4;
    public static final int X264_PRESET_MEDIUM         = 5;
    public static final int X264_PRESET_SLOW           = 6;
    public static final int X264_PRESET_SLOWER         = 7;
    public static final int X264_PRESET_VERYSLOW       = 8;
    public static final int X264_PRESET_PLACEBO        = 9;

    // x264 tune
    public static final int X264_TUNE_FILM             = 0;
    public static final int X264_TUNE_ANIMATION        = 1;
    public static final int X264_TUNE_GRAIN            = 2;
    public static final int X264_TUNE_STILLIMAGE       = 3;
    public static final int X264_TUNE_FASTDECODE       = 4;
    public static final int X264_TUNE_ZEROLATENCY      = 5;
    public static final int X264_TUNE_PSNR             = 6;
    public static final int X264_TUNE_SSIM             = 7;

    // x264 profile
    public static final int X264_PROFILE_BASELINE      = 0;
    public static final int X264_PROFILE_MAIN          = 1;
    public static final int X264_PROFILE_HIGH          = 2;
    public static final int X264_PROFILE_HIGH10        = 3;
    public static final int X264_PROFILE_HIGH422       = 4;
    public static final int X264_PROFILE_HIGH444       = 5;

    private HashMap<Integer, HashMap> optionCategories;

    private HashMap playerOptions;
    private HashMap formatOptions;
    private HashMap codecOptions;
    private HashMap swsOptions;
    private HashMap swrOptions;

    public static IjkMpOptions defaultOptions() {
        IjkMpOptions options = new IjkMpOptions();

        options.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 1);

        options.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "opensles", 0);
        options.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "overlay-format", IjkMediaPlayer.SDL_FCC_RV32);
        options.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", 1);
        options.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", 0);

        options.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "initial_timeout", 500 * 1000);
        options.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "stimeout", 500 * 1000);

        options.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "http-detect-range-support", 0);
        options.setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "skip_loop_filter", 48);

        options.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "rtp-jpeg-parse-packet-method", RTP_JPEG_PARSE_PACKET_METHOD_FILL);

        return options;
    }

    public IjkMpOptions() {
        playerOptions = new HashMap();
        formatOptions = new HashMap();
        codecOptions = new HashMap();
        swsOptions = new HashMap();
        swrOptions = new HashMap();

        optionCategories = new HashMap<>();
        optionCategories.put(IjkMediaPlayer.OPT_CATEGORY_PLAYER, playerOptions);
        optionCategories.put(IjkMediaPlayer.OPT_CATEGORY_FORMAT, formatOptions);
        optionCategories.put(IjkMediaPlayer.OPT_CATEGORY_CODEC, codecOptions);
        optionCategories.put(IjkMediaPlayer.OPT_CATEGORY_SWS, swsOptions);
        optionCategories.put(IjkMediaPlayer.OPT_CATEGORY_SWR, swrOptions);
    }

    public void applyToMediaPlayer(IjkMediaPlayer mediaPlayer) {
        Iterator itCategories = optionCategories.entrySet().iterator();
        while (itCategories.hasNext()) {
            Map.Entry categoryPair = (Map.Entry)itCategories.next();

            int category = (Integer)categoryPair.getKey();
            HashMap options = (HashMap)categoryPair.getValue();
            Iterator itOptions = options.entrySet().iterator();
            while(itOptions.hasNext()) {
                Map.Entry optionPair = (Map.Entry)itOptions.next();

                String optionKey = (String)optionPair.getKey();
                Object optionValue = optionPair.getValue();
                if (optionValue instanceof Long) {
                    mediaPlayer.setOption(category, optionKey, (Long)optionValue);
                } else if (optionValue instanceof String) {
                    mediaPlayer.setOption(category, optionKey, (String)optionValue);
                }
            }
        }
    }

    public void setOption(int category, String name, String value) {
        if (name == null)
            return;

        HashMap options = optionCategories.get(category);
        if (options != null) {
            options.put(name, value);
        }
    }

    public void setOption(int category, String name, long value) {
        if (name == null)
            return;

        HashMap options = optionCategories.get(category);
        if (options != null) {
            options.put(name, value);
        }
    }

    /* Common Helper */

    public void setFormatOption(String name, String value) {
        setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, name, value);
    }

    public void setCodecOption(String name, String value) {
        setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, name, value);
    }

    public void setSwsOption(String name, String value) {
        setOption(IjkMediaPlayer.OPT_CATEGORY_SWS, name, value);
    }

    public void setPlayerOption(String name, String value) {
        setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, name, value);
    }

    public void setSwrOption(String name, String value) {
        setOption(IjkMediaPlayer.OPT_CATEGORY_SWR, name, value);
    }

    public void setFormatOption(String name, long value) {
        setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, name, value);
    }

    public void setCodecOption(String name, long value) {
        setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, name, value);
    }

    public void setSwsOption(String name, long value) {
        setOption(IjkMediaPlayer.OPT_CATEGORY_SWS, name, value);
    }

    public void setPlayerOption(String name, long value) {
        setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, name, value);
    }

    public void setSwrOption(String name, long value) {
        setOption(IjkMediaPlayer.OPT_CATEGORY_SWR, name, value);
    }
}
