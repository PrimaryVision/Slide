package me.edgan.redditslide.Views;

import android.content.Context;
import android.graphics.Color;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.media.AudioAttributesCompat;
import androidx.media.AudioFocusRequestCompat;
import androidx.media.AudioManagerCompat;

import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.SimpleExoPlayer;
import androidx.media3.common.Tracks;
import androidx.media3.common.Tracks.Group;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.exoplayer.dash.DashMediaSource;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerControlView;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.VideoSize;

import me.edgan.redditslide.R;
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.util.BlendModeUtil;
import me.edgan.redditslide.util.GifUtils;
import me.edgan.redditslide.util.LogUtil;
import me.edgan.redditslide.util.NetworkUtil;

/** View containing an ExoPlayer */
public class ExoVideoView extends RelativeLayout {
    private Context context;
    private SimpleExoPlayer player;
    private DefaultTrackSelector trackSelector;
    private PlayerControlView playerUI;
    private boolean muteAttached = false;
    private boolean hqAttached = false;
    private AudioFocusHelper audioFocusHelper;
    private Handler handler = new Handler(Looper.getMainLooper());

    public ExoVideoView(final Context context) {
        this(context, null, true);
    }

    public ExoVideoView(final Context context, final boolean ui) {
        this(context, null, ui);
    }

    public ExoVideoView(final Context context, final AttributeSet attrs) {
        this(context, attrs, true);
    }

    public ExoVideoView(final Context context, final AttributeSet attrs, final boolean ui) {
        super(context, attrs);
        this.context = context;

        setupPlayer();
        if (ui) {
            setupUI();
        }
    }

    /** Initializes the view to render onto and the SimpleExoPlayer instance */
    private void setupPlayer() {
        // Create a track selector so we can set specific video quality for DASH
        trackSelector = new DefaultTrackSelector(context);
        if ((SettingValues.lowResAlways
                || (NetworkUtil.isConnected(context)
                    && !NetworkUtil.isConnectedWifi(context)
                    && SettingValues.lowResMobile))
            && SettingValues.lqVideos) {
            trackSelector.setParameters(
                    trackSelector.buildUponParameters().setForceLowestBitrate(true));
        } else {
            trackSelector.setParameters(
                    trackSelector.buildUponParameters().setForceHighestSupportedBitrate(true));
        }

        // If this method can be called multiple times, release the old player first
        if (player != null) {
            player.release();
            player = null;
        }

        // Create the player
        player = new SimpleExoPlayer.Builder(context)
                .setTrackSelector(trackSelector)
                .build();

        // Create an AspectRatioFrameLayout to size the video correctly
        AspectRatioFrameLayout frame = new AspectRatioFrameLayout(context);
        LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        params.addRule(CENTER_IN_PARENT, TRUE);
        frame.setLayoutParams(params);
        frame.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);

        if (SettingValues.oldSwipeMode) {
            TextureView textureView = new TextureView(context);

            frame.addView(textureView, new LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT
            ));
            player.setVideoTextureView(textureView);
        } else {
            SurfaceView surfaceView = new SurfaceView(context);
            frame.addView(surfaceView, new LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT
            ));
            player.setVideoSurfaceView(surfaceView);
        }

        addView(frame);

        // Make the video repeat infinitely
        player.setRepeatMode(Player.REPEAT_MODE_ALL);

        // Mute by default unless setting is enabled
        player.setVolume(SettingValues.unmuteDefault ? 1f : 0f);
        SettingValues.isMuted = !SettingValues.unmuteDefault;

        // Create audio focus helper
        audioFocusHelper = new AudioFocusHelper(
                ContextCompat.getSystemService(context, AudioManager.class)
        );

        // Add a Player.Listener for aspect ratio changes, logging, etc.
        player.addListener(
                new Player.Listener() {
                    // Make the video use the correct aspect ratio
                    @Override
                    public void onVideoSizeChanged(@NonNull VideoSize videoSize) {
                        frame.setAspectRatio(
                                videoSize.height == 0 || videoSize.width == 0
                                        ? 1
                                        : videoSize.width
                                                * videoSize.pixelWidthHeightRatio
                                                / videoSize.height);
                    }

                    // Logging
                    @Override
                    public void onTracksChanged(@NonNull Tracks tracks) {
                        StringBuilder toLog = new StringBuilder();
                        for (int groupIndex = 0; groupIndex < tracks.getGroups().size(); groupIndex++) {
                            Tracks.Group group = tracks.getGroups().get(groupIndex);
                            for (int trackIndex = 0; trackIndex < group.getMediaTrackGroup().length; trackIndex++) {
                                Format format = group.getTrackFormat(trackIndex);
                                boolean isSelected = group.isTrackSelected(trackIndex);

                                toLog.append("Format:\t")
                                        .append(format)
                                        .append(isSelected ? " (selected)" : "")
                                        .append("\n");
                            }
                        }
                        Log.v(LogUtil.getTag(), toLog.toString());
                    }
                });
    }

    /** Sets up the player UI */
    private void setupUI() {
        // Create a PlayerControlView for our video controls and add it
        playerUI = new PlayerControlView(context);
        playerUI.setPlayer(player);
        playerUI.setShowTimeoutMs(2000);

        if (!SettingValues.oldSwipeMode) {
            playerUI.hide();
	}

        addView(playerUI);

        // Show/hide the player UI on tap
        setOnClickListener(
                (v) -> {
                    playerUI.clearAnimation();
                    if (playerUI.isVisible()) {
                        playerUI.startAnimation(new PlayerUIFadeInAnimation(playerUI, false, 300));
                    } else {
                        playerUI.startAnimation(new PlayerUIFadeInAnimation(playerUI, true, 300));
                    }
                });
    }

    /**
     * Sets the player's URI and prepares for playback
     *
     * @param uri URI
     * @param type Type of video
     * @param listener Additional Player.Listener (optional)
     */
    public void setVideoURI(Uri uri, VideoType type, Player.Listener listener) {
        // Create the data sources used to retrieve and cache the video
        DataSource.Factory downloader =
                new OkHttpDataSource.Factory(Reddit.client)
                        .setDefaultRequestProperties(
                                GifUtils.AsyncLoadGif.makeHeaderMap(uri.getHost()));
        DataSource.Factory cacheDataSourceFactory =
                new CacheDataSource.Factory()
                        .setCache(Reddit.videoCache)
                        .setUpstreamDataSourceFactory(downloader);

        // Create an appropriate media source for the video type
        MediaSource videoSource;
        switch (type) {
            // DASH video, e.g. v.redd.it video
            case DASH:
                videoSource =
                        new DashMediaSource.Factory(cacheDataSourceFactory)
                                .createMediaSource(MediaItem.fromUri(uri));
                break;

            // Standard video, e.g. MP4 file
            case STANDARD:
            default:
                videoSource =
                        new ProgressiveMediaSource.Factory(cacheDataSourceFactory)
                                .createMediaSource(MediaItem.fromUri(uri));
                break;
        }

        player.setMediaSource(videoSource);
        player.prepare();
        if (listener != null) {
            player.addListener(listener);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
        // If we don't release the player here, hardware decoders won't be released, breaking
        // ExoPlayer device-wide
        stop();
    }

    /** Plays the video */
    public void play() {
        player.play();
    }

    /** Pauses the video */
    public void pause() {
        player.pause();
    }

    /** Stops the video and releases the player */
    public void stop() {
        if (player != null) {
            player.stop();
            player.release();
            player = null;
        }
        audioFocusHelper.loseFocus(); // do this last so audio doesn't overlap
    }

    public boolean isPlaying() {
        return player.getPlaybackState() == Player.STATE_READY && player.getPlayWhenReady();
    }

    /**
     * Seeks to a specific timestamp
     *
     * @param time timestamp (ms)
     */
    public void seekTo(long time) {
        player.seekTo(time);
    }

    /**
     * Gets the current timestamp
     *
     * @return current position in ms
     */
    public long getCurrentPosition() {
        return player.getCurrentPosition();
    }

    /**
     * Attach a mute button to the view. The view will then handle hiding/showing that button as
     * appropriate. If this is not called, audio will be permanently muted.
     *
     * @param mute Mute button
     */
    public void attachMuteButton(final ImageView mute) {
        // Hide the mute button by default
        mute.setVisibility(GONE);

        player.addListener(
                new Player.Listener() {
                    @Override
                    public void onTracksChanged(@NonNull Tracks tracks) {
                        // Only run on first valid track change
                        if (muteAttached && !tracks.getGroups().isEmpty()) {
                            return;
                        } else {
                            muteAttached = true;
                        }

                        // Check if we have any selected audio track
                        boolean foundAudio = false;
                        for (Tracks.Group group : tracks.getGroups()) {
                            for (int trackIndex = 0;
                                    trackIndex < group.getMediaTrackGroup().length;
                                    trackIndex++) {
                                if (group.isTrackSelected(trackIndex)) {
                                    Format format = group.getTrackFormat(trackIndex);
                                    if (format != null
                                            && MimeTypes.isAudio(format.sampleMimeType)) {
                                        foundAudio = true;
                                        break;
                                    }
                                }
                            }
                            if (foundAudio) {
                                break;
                            }
                        }

                        // If an audio track is present/selected, show the mute button
                        if (foundAudio) {
                            mute.setVisibility(VISIBLE);
                            // Set initial volume state based on settings
                            if (!SettingValues.isMuted) {
                                player.setVolume(1f);
                                mute.setImageResource(R.drawable.ic_volume_on);
                                BlendModeUtil.tintImageViewAsSrcAtop(mute, Color.WHITE);
                                audioFocusHelper.gainFocus();
                            } else {
                                player.setVolume(0f);
                                mute.setImageResource(R.drawable.ic_volume_off);
                                BlendModeUtil.tintImageViewAsSrcAtop(
                                        mute, getResources().getColor(R.color.md_red_500));
                            }

                            // Toggle mute on button click
                            mute.setOnClickListener(
                                    (v) -> {
                                        if (SettingValues.isMuted) {
                                            player.setVolume(1f);
                                            SettingValues.isMuted = false;
                                            SettingValues.prefs
                                                    .edit()
                                                    .putBoolean(SettingValues.PREF_MUTE, false)
                                                    .apply();
                                            mute.setImageResource(R.drawable.ic_volume_on);
                                            BlendModeUtil.tintImageViewAsSrcAtop(mute, Color.WHITE);
                                            audioFocusHelper.gainFocus();
                                        } else {
                                            player.setVolume(0f);
                                            SettingValues.isMuted = true;
                                            SettingValues.prefs
                                                    .edit()
                                                    .putBoolean(SettingValues.PREF_MUTE, true)
                                                    .apply();
                                            mute.setImageResource(R.drawable.ic_volume_off);
                                            BlendModeUtil.tintImageViewAsSrcAtop(
                                                    mute,
                                                    getResources().getColor(R.color.md_red_500));
                                            audioFocusHelper.loseFocus();
                                        }
                                    });
                        }
                    }
                });
    }

    /**
     * Attach an HQ button to the view. The view will then handle hiding/showing that button as
     * appropriate.
     *
     * @param hq HQ button
     */
    public void attachHqButton(final ImageView hq) {
        // Hidden by default - we don't yet know if we'll have multiple qualities to select from
        hq.setVisibility(GONE);

        player.addListener(
                new Player.Listener() {
                    @Override
                    public void onTracksChanged(@NonNull Tracks tracks) {
                        // Only run if not already attached, we have track groups,
                        // and we're not already forcing highest bitrates
                        if (hqAttached
                                || tracks.getGroups().isEmpty()
                                || trackSelector.getParameters().forceHighestSupportedBitrate) {
                            return;
                        } else {
                            hqAttached = true;
                        }
                        // Lopp through the tracks, check if they're video. If we have at least 2
                        // video tracks we can set
                        // up quality selection.

                        int videoTrackCounter = 0;
                        for (Tracks.Group group : tracks.getGroups()) {
                            for (int trackIndex = 0;
                                    trackIndex < group.getMediaTrackGroup().length;
                                    trackIndex++) {
                                Format format = group.getTrackFormat(trackIndex);
                                if (format != null && MimeTypes.isVideo(format.sampleMimeType)) {
                                    videoTrackCounter++;
                                    if (videoTrackCounter > 1) {
                                        break;
                                    }
                                }
                            }
                            if (videoTrackCounter > 1) {
                                break;
                            }
                        }

                        // If we have enough video tracks to have a quality button, set it up.
                        if (videoTrackCounter > 1) {
                            hq.setVisibility(VISIBLE);
                            hq.setOnClickListener(
                                    (v) -> {
                                        trackSelector.setParameters(
                                                trackSelector
                                                        .buildUponParameters()
                                                        .setForceLowestBitrate(false)
                                                        .setForceHighestSupportedBitrate(true));
                                        hq.setVisibility(GONE);
                                    });
                        }
                    }
                });
    }

    public enum VideoType {
        STANDARD,
        DASH
    }

    /** Helps manage audio focus */
    private class AudioFocusHelper implements AudioManager.OnAudioFocusChangeListener {
        private AudioManager manager;
        private boolean wasPlaying;
        private AudioFocusRequestCompat request;

        AudioFocusHelper(AudioManager manager) {
            this.manager = manager;

            if (request == null) {
                AudioAttributesCompat audioAttributes =
                        new AudioAttributesCompat.Builder()
                                .setContentType(AudioAttributesCompat.CONTENT_TYPE_MOVIE)
                                .setUsage(AudioAttributesCompat.USAGE_MEDIA)
                                .build();
                request =
                        new AudioFocusRequestCompat.Builder(
                                        AudioManagerCompat.AUDIOFOCUS_GAIN_TRANSIENT)
                                .setAudioAttributes(audioAttributes)
                                .setOnAudioFocusChangeListener(this)
                                .setWillPauseWhenDucked(true)
                                .build();
            }
        }

        /** Lose audio focus */
        void loseFocus() {
            AudioManagerCompat.abandonAudioFocusRequest(manager, request);
        }

        /** Gain audio focus */
        void gainFocus() {
            AudioManagerCompat.requestAudioFocus(manager, request);
        }

        @Override
        public void onAudioFocusChange(int focusChange) {
            // Pause on audiofocus loss, play on gain
            if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                wasPlaying = player.getPlayWhenReady();
                player.pause();
            } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                player.setPlayWhenReady(wasPlaying);
            }
        }
    }

    static class PlayerUIFadeInAnimation extends AnimationSet {
        private PlayerControlView animationView;
        private boolean toVisible;

        PlayerUIFadeInAnimation(PlayerControlView view, boolean toVisible, long duration) {
            super(false);
            this.toVisible = toVisible;
            this.animationView = view;

            float startAlpha = toVisible ? 0 : 1;
            float endAlpha = toVisible ? 1 : 0;

            AlphaAnimation alphaAnimation = new AlphaAnimation(startAlpha, endAlpha);
            alphaAnimation.setDuration(duration);

            addAnimation(alphaAnimation);
            setAnimationListener(new PlayerUIFadeInAnimationListener());
        }

        private class PlayerUIFadeInAnimationListener implements AnimationListener {

            @Override
            public void onAnimationStart(Animation animation) {
                animationView.show();
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                if (toVisible) {
                    animationView.show();
                } else {
                    animationView.hide();
                }
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
                // Purposefully left blank
            }
        }
    }
}
