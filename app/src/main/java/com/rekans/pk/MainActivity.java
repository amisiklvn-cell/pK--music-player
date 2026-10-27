package com.rekans.pk;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.widget.PopupMenu;
import android.content.ContentUris;
import android.content.Intent;
import android.provider.DocumentsContract;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.EditText;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;
import java.util.Random;

public class MainActivity extends Activity {

    private int BLACK = Color.rgb(12, 12, 15);
    private int SURFACE = Color.rgb(25, 25, 31);
    private int RED = Color.rgb(255, 48, 69);
    private int SKY_BLUE = Color.rgb(135, 206, 235);
    private int WHITE = Color.WHITE;
    private int SECONDARY = Color.rgb(176, 176, 186);

    private String themeName = "Dark";
    private boolean searchVisible = false;
    private static final String PREFS_NAME = "pk_music_settings";
    private static final String THEME_KEY = "app_theme";

    private LinearLayout songList;
    private LinearLayout libraryScreen;
    private LinearLayout miniPlayer;
    private TextView miniTitle;
    private TextView miniArtist;
    private TextView miniPlayButton;
    private LinearLayout playerScreen;
    private boolean showingPlayerScreen = false;
    private EditText searchInput;
    private Spinner sortSpinner;
    private final ArrayList<SongInfo> songInfo = new ArrayList<>();
    private TextView countText;
    private TextView statusText;
    private TextView nowTitle;
    private TextView nowArtist;
    private TextView coverPlaceholder;
    private FrameLayout vinylDisc;
    private ImageView albumArtwork;
    private TextView artworkFallback;
    private TextView quoteText;
    private android.view.animation.Animation vinylRotation;
    private String quoteSongId = "";
    private final Random quoteRandom = new Random();
    private int lastQuoteIndex = -1;

    private static final String[] MUSIC_QUOTES = {
            "Music heals what words cannot explain.",
            "Where words fail, music speaks.",
            "Let the music take you somewhere beautiful.",
            "Every song holds a memory.",
            "Life sounds better with music.",
            "Music is the soundtrack of the soul.",
            "Some feelings are best heard, not spoken.",
            "Turn the noise of life into a melody.",
            "A good song can change your whole day.",
            "Music connects hearts without saying a word.",
            "When in doubt, let the music play.",
            "Find your peace between the notes.",
            "A melody can say what the heart cannot.",
            "Music makes ordinary moments unforgettable.",
            "Let every beat remind you to keep going.",
            "Sometimes one song is all you need.",
            "Feel the rhythm, remember the moment.",
            "Music is a little piece of freedom.",
            "Your mood has a melody. Find it.",
            "Headphones on, worries down.",
            "The right song finds you at the right time.",
            "Collect moments, memories, and melodies.",
            "A song can bring yesterday back to life.",
            "Breathe in the beat and let go.",
            "Music gives silence a beautiful voice.",
            "Keep calm and let the rhythm flow.",
            "One playlist, a thousand feelings.",
            "Let your heart dance to its own rhythm.",
            "Music turns feelings into sound.",
            "Every beat is a reason to feel alive."
    };
    private TextView timeText;
    private TextView shuffleButton;
    private TextView repeatButton;
    private Button playButton;
    private SeekBar seekBar;

    private MediaController controller;
    private ListenableFuture<MediaController> controllerFuture;

    private final ArrayList<MediaItem> songs = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private boolean firstResume = true;

    private boolean userSeeking = false;
    private boolean shuffleEnabled = false;
    private int repeatMode = Player.REPEAT_MODE_OFF;


    private static class SongInfo {
        final MediaItem item;
        final String title;
        final String artist;
        final String titleLower;
        final String artistLower;
        final long duration;
        final long dateAdded;

        SongInfo(MediaItem item, String title, String artist,
                 long duration, long dateAdded) {
            this.item = item;
            this.title = title;
            this.artist = artist;
            this.titleLower = title.toLowerCase(Locale.ROOT);
            this.artistLower = artist.toLowerCase(Locale.ROOT);
            this.duration = duration;
            this.dateAdded = dateAdded;
        }
    }

    private static final int AUDIO_PERMISSION_CODE = 101;

    private final Runnable progressUpdater = new Runnable() {
        @Override
        public void run() {
            updateProgress();
            handler.postDelayed(this, 500);
        }
    };

    private final Player.Listener playerListener = new Player.Listener() {
        @Override
        public void onMediaItemTransition(MediaItem mediaItem, int reason) {
            updateNowPlaying();
            chooseQuoteForCurrentSong();
            updateMiniPlayer();
            renderSongRows();
        }

        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            updatePlayButton();
            updateVinylAnimation(isPlaying);
            updateMiniPlayer();
        }

        @Override
        public void onPlaybackStateChanged(int playbackState) {
            updateNowPlaying();
            updateProgress();
        }

        @Override
        public void onShuffleModeEnabledChanged(boolean enabled) {
            shuffleEnabled = enabled;
            updateModeButtons();
        }

        @Override
        public void onRepeatModeChanged(int mode) {
            repeatMode = mode;
            updateModeButtons();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loadSavedTheme();
        buildScreen();
        connectPlaybackService();

        if (hasAudioPermission()) {
            loadSongs();
        } else {
            requestAudioPermission();
        }

        handler.post(progressUpdater);
    }

    private LinearLayout createMiniPlayer() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.HORIZONTAL);
        panel.setGravity(Gravity.CENTER_VERTICAL);
        panel.setPadding(dp(12), dp(8), dp(10), dp(8));

        GradientDrawable background = new GradientDrawable();
        background.setColor(SURFACE);
        background.setCornerRadius(dp(14));
        background.setStroke(dp(1), RED);
        panel.setBackground(background);
        panel.setVisibility(View.GONE);
        panel.setClickable(true);
        panel.setFocusable(true);
        panel.setContentDescription("Open full music player");

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);

        miniTitle = new TextView(this);
        miniTitle.setTextColor(WHITE);
        miniTitle.setTextSize(14);
        miniTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        miniTitle.setMaxLines(1);
        miniTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);

        miniArtist = new TextView(this);
        miniArtist.setTextColor(SECONDARY);
        miniArtist.setTextSize(12);
        miniArtist.setMaxLines(1);
        miniArtist.setEllipsize(android.text.TextUtils.TruncateAt.END);

        labels.addView(miniTitle);
        labels.addView(miniArtist);

        panel.addView(labels, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        miniPlayButton = new TextView(this);
        miniPlayButton.setText("▶");
        miniPlayButton.setTextColor(RED);
        miniPlayButton.setTextSize(25);
        miniPlayButton.setGravity(Gravity.CENTER);
        miniPlayButton.setContentDescription("Play or pause");
        miniPlayButton.setPadding(dp(12), 0, dp(8), 0);

        panel.addView(miniPlayButton, new LinearLayout.LayoutParams(
                dp(48), dp(48)));

        panel.setOnClickListener(v -> {
            if (controller != null
                    && controller.getCurrentMediaItem() != null) {
                showPlayerScreen();
            }
        });

        miniPlayButton.setOnClickListener(v -> {
            if (controller == null) {
                toast("Audio player is still connecting. Try again.");
                return;
            }
            if (controller.getCurrentMediaItem() == null) {
                toast("Choose a song from your library first.");
                return;
            }
            if (controller.isPlaying()) {
                controller.pause();
            } else {
                controller.play();
            }
            updateMiniPlayer();
        });

        return panel;
    }

    private void updateMiniPlayButton() {
        if (miniPlayButton != null) {
            miniPlayButton.setText(
                    controller != null && controller.isPlaying() ? "⏸" : "▶");
        }
    }

    private void updateMiniPlayer() {
        if (miniPlayer == null) return;

        if (controller == null || controller.getCurrentMediaItem() == null) {
            miniPlayer.setVisibility(View.GONE);
            updateMiniPlayButton();
            return;
        }

        MediaMetadata metadata = controller.getCurrentMediaItem().mediaMetadata;
        miniTitle.setText(metadata.title != null
                ? metadata.title : "Unknown title");
        miniArtist.setText(metadata.artist != null
                ? metadata.artist : "Unknown artist");
        miniPlayer.setVisibility(View.VISIBLE);
        updateMiniPlayButton();
    }

    private void showLibraryScreen() {
        showingPlayerScreen = false;
        if (libraryScreen != null) {
            setContentView(libraryScreen);
        }
    }

    private void showPlayerScreen() {
        showingPlayerScreen = true;
        if (playerScreen != null) {
            setContentView(playerScreen);
        }
    }

    @Override
    public void onBackPressed() {
        if (showingPlayerScreen) {
            showLibraryScreen();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        firstResume = false;
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BLACK);
        root.setPadding(dp(18), dp(12), dp(18), dp(8));

        LinearLayout logo = new LinearLayout(this);
        logo.setGravity(Gravity.CENTER);
        logo.setOrientation(LinearLayout.HORIZONTAL);

        TextView letterP = new TextView(this);
        letterP.setText("p");
        letterP.setTextColor(SKY_BLUE);
        letterP.setTextSize(66);
        letterP.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

        TextView letterK = new TextView(this);
        letterK.setText("k");
        letterK.setTextColor(RED);
        letterK.setTextSize(66);
        letterK.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

        logo.addView(letterP);
        logo.addView(letterK);

        TextView heading = new TextView(this);
        heading.setText("MUSIC PLAYER");
        heading.setTextColor(SKY_BLUE);
        heading.setTextSize(16);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.setGravity(Gravity.CENTER);

        TextView libraryTitle = new TextView(this);
        libraryTitle.setText("YOUR MUSIC");
        libraryTitle.setTextColor(WHITE);
        libraryTitle.setTextSize(20);
        libraryTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        libraryTitle.setPadding(0, dp(12), 0, dp(3));

        countText = new TextView(this);
        countText.setTextColor(SECONDARY);
        countText.setTextSize(13);

        statusText = new TextView(this);
        statusText.setTextColor(SECONDARY);
        statusText.setTextSize(13);
        statusText.setPadding(0, dp(4), 0, dp(6));

        searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setVisibility(searchVisible ? View.VISIBLE : View.GONE);
        searchInput.setTextColor(WHITE);
        searchInput.setHintTextColor(SECONDARY);
        searchInput.setHint("Search title or artist");
        searchInput.setTextSize(15);
        searchInput.setPadding(dp(12), dp(6), dp(12), dp(6));
        searchInput.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(SKY_BLUE));

        sortSpinner = new Spinner(this);
        String[] sortOptions = {
                "Newest first", "Oldest first", "Title A-Z", "Artist A-Z"
        };
        ArrayAdapter<String> sortAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, sortOptions);
        sortAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        sortSpinner.setAdapter(sortAdapter);
        sortSpinner.setVisibility(View.GONE);
        sortSpinner.setOnItemSelectedListener(
                new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent,
                    View view, int position, long id) {
                sortAndRenderSongs();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });

        searchInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start,
                    int count, int after) { }

            @Override
            public void onTextChanged(CharSequence text, int start,
                    int before, int count) {
                renderSongRows();
            }

            @Override
            public void afterTextChanged(android.text.Editable text) { }
        });

        songList = new LinearLayout(this);
        songList.setOrientation(LinearLayout.VERTICAL);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(false);
        scrollView.addView(songList);

        miniPlayer = createMiniPlayer();

        LinearLayout nowPanel = new LinearLayout(this);
        nowPanel.setOrientation(LinearLayout.VERTICAL);
        nowPanel.setPadding(dp(12), dp(10), dp(12), dp(8));
        nowPanel.setBackgroundColor(SURFACE);

        TextView nowHeading = new TextView(this);
        nowHeading.setText("NOW PLAYING");
        nowHeading.setTextColor(RED);
        nowHeading.setTextSize(12);
        nowHeading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

        nowTitle = new TextView(this);
        nowTitle.setText("No song selected");
        nowTitle.setTextColor(WHITE);
        nowTitle.setTextSize(16);
        nowTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        nowTitle.setMaxLines(1);

        nowArtist = new TextView(this);
        nowArtist.setText("Choose a song from your library");
        nowArtist.setTextColor(SECONDARY);
        nowArtist.setTextSize(12);
        nowArtist.setMaxLines(1);

        // Responsive album artwork sized for the available phone height.
        float density = getResources().getDisplayMetrics().density;
        int screenHeightDp = (int) (
                getResources().getDisplayMetrics().heightPixels / density);
        // Larger responsive artwork for the dedicated full-screen player.
        // Leave enough room for the title, quote, timeline and controls.
        int coverSize = Math.max(150,
                Math.min(320, screenHeightDp - 440));

        // Vinyl record with grooves and a replaceable centre album-art image.
        vinylDisc = new FrameLayout(this);
        vinylDisc.setBackground(makeCircleDrawable(Color.rgb(18, 18, 22),
                Color.rgb(72, 72, 82), dp(2)));
        vinylDisc.setElevation(dp(5));

        TextView groove1 = new TextView(this);
        groove1.setBackground(makeCircleDrawable(Color.TRANSPARENT,
                Color.rgb(48, 48, 56), dp(1)));
        FrameLayout.LayoutParams groove1Params = new FrameLayout.LayoutParams(
                dp((int) (coverSize * 0.86f)), dp((int) (coverSize * 0.86f)),
                Gravity.CENTER);
        vinylDisc.addView(groove1, groove1Params);

        TextView groove2 = new TextView(this);
        groove2.setBackground(makeCircleDrawable(Color.TRANSPARENT,
                Color.rgb(55, 55, 64), dp(1)));
        FrameLayout.LayoutParams groove2Params = new FrameLayout.LayoutParams(
                dp((int) (coverSize * 0.68f)), dp((int) (coverSize * 0.68f)),
                Gravity.CENTER);
        vinylDisc.addView(groove2, groove2Params);

        albumArtwork = new ImageView(this);
        albumArtwork.setScaleType(ImageView.ScaleType.CENTER_CROP);
        albumArtwork.setImageDrawable(makeFallbackArtwork());
        albumArtwork.setBackground(makeCircleDrawable(RED, RED, dp(2)));
        albumArtwork.setClipToOutline(true);
        FrameLayout.LayoutParams artParams = new FrameLayout.LayoutParams(
                dp((int) (coverSize * 0.42f)), dp((int) (coverSize * 0.42f)),
                Gravity.CENTER);
        vinylDisc.addView(albumArtwork, artParams);

        artworkFallback = new TextView(this);
        artworkFallback.setText("♫");
        artworkFallback.setTextColor(WHITE);
        artworkFallback.setTextSize(Math.max(24, coverSize * 0.17f));
        artworkFallback.setGravity(Gravity.CENTER);
        artworkFallback.setClickable(false);
        vinylDisc.addView(artworkFallback, artParams);

        // PK brand logo in the centre of the vinyl.
        albumArtwork.setVisibility(View.GONE);
        artworkFallback.setVisibility(View.GONE);

        TextView pkLogo = new TextView(this);
        android.text.SpannableString pkText =
                new android.text.SpannableString("PK");
        pkText.setSpan(
                new android.text.style.ForegroundColorSpan(SKY_BLUE),
                0, 1,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        pkText.setSpan(
                new android.text.style.ForegroundColorSpan(RED),
                1, 2,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        pkLogo.setText(pkText);
        pkLogo.setTextSize(30);
        pkLogo.setTypeface(android.graphics.Typeface.DEFAULT,
                android.graphics.Typeface.BOLD);
        pkLogo.setGravity(Gravity.CENTER);
        pkLogo.setBackground(makeCircleDrawable(
                Color.rgb(12, 12, 16), SKY_BLUE, dp(2)));
        pkLogo.setElevation(dp(3));

        FrameLayout.LayoutParams pkLogoParams =
                new FrameLayout.LayoutParams(
                        dp((int) (coverSize * 0.30f)),
                        dp((int) (coverSize * 0.30f)),
                        Gravity.CENTER);
        vinylDisc.addView(pkLogo, pkLogoParams);

        LinearLayout.LayoutParams coverParams =
                new LinearLayout.LayoutParams(dp(coverSize), dp(coverSize));
        coverParams.gravity = Gravity.CENTER_HORIZONTAL;
        coverParams.topMargin = dp(6);
        coverParams.bottomMargin = dp(8);
        nowPanel.addView(vinylDisc, coverParams);

        quoteText = new TextView(this);
        quoteText.setText("\u201cMusic heals what words cannot explain.\u201d");
        quoteText.setTextColor(SKY_BLUE);
        quoteText.setTextSize(14);
        quoteText.setGravity(Gravity.CENTER);
        quoteText.setTypeface(Typeface.DEFAULT, Typeface.ITALIC);
        quoteText.setPadding(dp(8), dp(4), dp(8), dp(10));
        quoteText.setMaxLines(2);

        seekBar = new SeekBar(this);
        seekBar.setMax(1000);
        seekBar.setProgressTintList(android.content.res.ColorStateList.valueOf(RED));
        seekBar.setThumbTintList(android.content.res.ColorStateList.valueOf(RED));
        seekBar.setProgressBackgroundTintList(
                android.content.res.ColorStateList.valueOf(SECONDARY));

        timeText = new TextView(this);
        timeText.setText("0:00 / 0:00");
        timeText.setTextColor(SECONDARY);
        timeText.setTextSize(11);

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) userSeeking = true;
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
                userSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                if (controller != null && controller.isCommandAvailable(
                        Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) {
                    long duration = controller.getDuration();
                    if (duration > 0) {
                        controller.seekTo((long) (duration * bar.getProgress() / 1000.0));
                    }
                }
                userSeeking = false;
                updateProgress();
            }
        });

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        controls.setOrientation(LinearLayout.HORIZONTAL);

        Button previousButton = makeButton("⏮");
        playButton = makeButton("▶");
        Button nextButton = makeButton("⏭");

        previousButton.setOnClickListener(v -> {
            if (controller != null && controller.hasPreviousMediaItem()) {
                controller.seekToPreviousMediaItem();
                controller.play();
            }
        });

        playButton.setOnClickListener(v -> {
            if (controller == null) {
                toast("Player is connecting. Try again.");
                return;
            }

            if (controller.isPlaying()) {
                controller.pause();
            } else if (controller.getMediaItemCount() > 0) {
                if (controller.getPlaybackState() == Player.STATE_IDLE) {
                    controller.prepare();
                }
                controller.play();
            } else if (!songs.isEmpty()) {
                playSong(0);
            } else {
                toast("No songs found");
            }
        });

        nextButton.setOnClickListener(v -> {
            if (controller != null && controller.hasNextMediaItem()) {
                controller.seekToNextMediaItem();
                controller.play();
            }
        });

        controls.addView(previousButton);
        controls.addView(playButton);
        controls.addView(nextButton);

        LinearLayout modes = new LinearLayout(this);
        modes.setGravity(Gravity.CENTER);
        modes.setOrientation(LinearLayout.HORIZONTAL);

        shuffleButton = new TextView(this);
        shuffleButton.setTextSize(13);
        shuffleButton.setPadding(dp(16), dp(6), dp(16), dp(6));

        repeatButton = new TextView(this);
        repeatButton.setTextSize(13);
        repeatButton.setPadding(dp(16), dp(6), dp(16), dp(6));

        shuffleButton.setOnClickListener(v -> {
            shuffleEnabled = !shuffleEnabled;
            if (controller != null) {
                controller.setShuffleModeEnabled(shuffleEnabled);
            }
            updateModeButtons();
        });

        repeatButton.setOnClickListener(v -> {
            if (repeatMode == Player.REPEAT_MODE_OFF) {
                repeatMode = Player.REPEAT_MODE_ALL;
            } else if (repeatMode == Player.REPEAT_MODE_ALL) {
                repeatMode = Player.REPEAT_MODE_ONE;
            } else {
                repeatMode = Player.REPEAT_MODE_OFF;
            }

            if (controller != null) {
                controller.setRepeatMode(repeatMode);
            }
            updateModeButtons();
        });

        modes.addView(shuffleButton);
        modes.addView(repeatButton);


        nowPanel.addView(nowHeading);
        nowPanel.addView(nowTitle);
        nowPanel.addView(nowArtist);
        nowPanel.addView(quoteText);
        nowPanel.addView(seekBar);
        nowPanel.addView(timeText);
        nowPanel.addView(controls);
        nowPanel.addView(modes);

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);

        topBar.addView(logo, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView menuButton = new TextView(this);
        menuButton.setText("\u22ee");
        menuButton.setTextColor(WHITE);
        menuButton.setTextSize(30);
        menuButton.setGravity(Gravity.CENTER);
        menuButton.setContentDescription("Open app menu");
        menuButton.setPadding(dp(12), 0, dp(4), 0);
        menuButton.setOnClickListener(v -> showTopMenu(menuButton));
        topBar.addView(menuButton, new LinearLayout.LayoutParams(
                dp(48), dp(52)));

        root.addView(topBar);
        root.addView(heading);
        root.addView(libraryTitle);
        root.addView(countText);
        root.addView(statusText);
        root.addView(searchInput, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));
        root.addView(sortSpinner, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));
        root.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        root.addView(miniPlayer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(70)));
        libraryScreen = root;

        playerScreen = new LinearLayout(this);
        playerScreen.setOrientation(LinearLayout.VERTICAL);
        playerScreen.setBackgroundColor(BLACK);
        playerScreen.setPadding(dp(18), dp(12), dp(18), dp(12));

        LinearLayout playerTopBar = new LinearLayout(this);
        playerTopBar.setOrientation(LinearLayout.HORIZONTAL);
        playerTopBar.setGravity(Gravity.CENTER_VERTICAL);

        TextView backButton = new TextView(this);
        backButton.setText("‹");
        backButton.setTextColor(WHITE);
        backButton.setTextSize(42);
        backButton.setGravity(Gravity.CENTER);
        backButton.setContentDescription("Back to music library");
        playerTopBar.addView(backButton, new LinearLayout.LayoutParams(
                dp(52), dp(52)));

        TextView playerHeading = new TextView(this);
        playerHeading.setText("NOW PLAYING");
        playerHeading.setTextColor(SKY_BLUE);
        playerHeading.setTextSize(17);
        playerHeading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        playerHeading.setGravity(Gravity.CENTER_VERTICAL);
        playerTopBar.addView(playerHeading, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        backButton.setOnClickListener(v -> showLibraryScreen());

        playerScreen.addView(playerTopBar);
        nowPanel.setBackgroundColor(BLACK);
        // Center the complete playback layout vertically instead of leaving
        // unused space beneath the controls on tall phones.
        nowPanel.setGravity(Gravity.CENTER);
        playerScreen.addView(nowPanel, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(showingPlayerScreen ? playerScreen : libraryScreen);
        updateModeButtons();
    }

    private void loadSavedTheme() {
        themeName = "Dark";
        applyThemeColors();
    }

    private void applyThemeColors() {
        switch (themeName) {
            case "Light":
                BLACK = Color.rgb(245, 247, 250);
                SURFACE = Color.WHITE;
                RED = Color.rgb(210, 35, 55);
                SKY_BLUE = Color.rgb(25, 115, 190);
                WHITE = Color.rgb(28, 30, 36);
                SECONDARY = Color.rgb(100, 105, 115);
                break;
            case "Purple Neon":
                BLACK = Color.rgb(15, 7, 27);
                SURFACE = Color.rgb(35, 16, 55);
                RED = Color.rgb(220, 65, 255);
                SKY_BLUE = Color.rgb(100, 245, 255);
                WHITE = Color.rgb(250, 240, 255);
                SECONDARY = Color.rgb(190, 160, 210);
                break;
            case "Red":
                BLACK = Color.rgb(35, 5, 8);
                SURFACE = Color.rgb(65, 12, 17);
                RED = Color.rgb(255, 45, 55);
                SKY_BLUE = Color.rgb(255, 125, 125);
                WHITE = Color.rgb(255, 245, 245);
                SECONDARY = Color.rgb(220, 170, 175);
                break;
            default:
                themeName = "Dark";
                BLACK = Color.rgb(12, 12, 15);
                SURFACE = Color.rgb(25, 25, 31);
                RED = Color.rgb(255, 48, 69);
                SKY_BLUE = Color.rgb(135, 206, 235);
                WHITE = Color.WHITE;
                SECONDARY = Color.rgb(176, 176, 186);
                break;
        }
    }

    private void showTopMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, 1, 0, "Search music");
        popup.getMenu().add(0, 2, 1, "Sort songs");
        popup.getMenu().add(0, 3, 2, "Refresh library");
        popup.getMenu().add(0, 6, 3, "Browse folders");
        popup.getMenu().add(0, 5, 4, "About / Developer");
        popup.getMenu().add(0, 7, 5, "App Update");

        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    searchVisible = !searchVisible;
                    searchInput.setVisibility(
                            searchVisible ? View.VISIBLE : View.GONE);
                    if (searchVisible) {
                        searchInput.requestFocus();
                    } else {
                        searchInput.setText("");
                    }
                    return true;
                case 2:
                    showSortPicker();
                    return true;
                case 6:
                    browseFolders();
                    return true;
                case 3:
                    if (hasAudioPermission()) {
                        loadSongs();
                    } else {
                        requestAudioPermission();
                    }
                    return true;
                case 7:
                    showUpdateMessage();
                    return true;
                case 5:
                    new AlertDialog.Builder(this)
                            .setTitle("PK Music Player")
                            .setMessage("Developed by rEKANS\n"
                                    + "Contact: amisiklvn@gmail.com\n"
                                    + "Theme: " + themeName)
                            .setPositiveButton("OK", null)
                            .show();
                    return true;
                default:
                    return false;
            }
        });
        popup.show();
    }


    private void showUpdateMessage() {
        new AlertDialog.Builder(this)
                .setTitle("🎉 Update Available!")
                .setMessage("Thank you for using PK Music Player! ❤️\\n\\n"
                        + "A new update may be available. Please make sure "
                        + "you have an active internet connection before updating.\\n\\n"
                        + "Thank you for your time and for supporting PK Music Player!\\n\\n"
                        + "— Developer: rEKANS")
                .setPositiveButton("Update Now", (dialog, which) -> {
                    new AlertDialog.Builder(this)
                            .setTitle("Update source not connected")
                            .setMessage("The online update service is not set up yet. "
                                    + "We will connect it in a later step. "
                                    + "For now, PK Music Player will continue working normally.")
                            .setPositiveButton("OK", null)
                            .show();
                })
                .setNegativeButton("Later", null)
                .show();
    }

    private void showSortPicker() {
        String[] options = {
                "Newest first", "Oldest first", "Title A-Z", "Artist A-Z"
        };
        new AlertDialog.Builder(this)
                .setTitle("Sort songs")
                .setSingleChoiceItems(options,
                        sortSpinner.getSelectedItemPosition(),
                        (dialog, which) -> {
                            sortSpinner.setSelection(which);
                            sortAndRenderSongs();
                            dialog.dismiss();
                        })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showThemePicker() {
        String[] themes = {"Dark", "Light", "Purple Neon", "Red"};
        int selected = 0;
        for (int i = 0; i < themes.length; i++) {
            if (themes[i].equals(themeName)) {
                selected = i;
                break;
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("App Theme")
                .setSingleChoiceItems(themes, selected, (dialog, which) -> {
                    String query = searchInput.getText().toString();
                    int sort = sortSpinner.getSelectedItemPosition();

                    themeName = themes[which];
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                            .edit().putString(THEME_KEY, themeName).apply();
                    applyThemeColors();

                    buildScreen();
                    sortSpinner.setSelection(sort);
                    searchInput.setText(query);
                    renderSongRows();

                    dialog.dismiss();
                    toast(themeName + " theme applied");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private Button makeButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(WHITE);
        button.setTextSize(24);
        button.setMinHeight(dp(64));
        GradientDrawable solidButton = new GradientDrawable();
        solidButton.setColor(RED);
        solidButton.setCornerRadius(dp(12));
        button.setBackground(solidButton);
        button.setStateListAnimator(null);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                dp(92), dp(76));
        params.setMargins(dp(7), dp(5), dp(7), dp(5));
        button.setLayoutParams(params);
        return button;
    }

    private void connectPlaybackService() {
        SessionToken token = new SessionToken(
                this,
                new android.content.ComponentName(this, PlaybackService.class));

        controllerFuture = new MediaController.Builder(this, token).buildAsync();

        controllerFuture.addListener(() -> {
            try {
                controller = controllerFuture.get();
                controller.addListener(playerListener);
                controller.setRepeatMode(repeatMode);
                controller.setShuffleModeEnabled(shuffleEnabled);
                updateNowPlaying();
                updatePlayButton();
                updateModeButtons();
            } catch (Exception e) {
                toast("Could not connect the audio player: " + e.getMessage());
            }
        }, getMainExecutor());
    }

    private String audioPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            return Manifest.permission.READ_MEDIA_AUDIO;
        }
        return Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    private boolean hasAudioPermission() {
        return checkSelfPermission(audioPermission())
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestAudioPermission() {
        requestPermissions(new String[]{audioPermission()}, AUDIO_PERMISSION_CODE);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == AUDIO_PERMISSION_CODE) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadSongs();
            } else {
                countText.setText("Audio permission required");
                statusText.setText("Allow music access in Android Settings.");
            }
        }
    }

    private static final int FOLDER_PICKER_CODE = 202;

    private void browseFolders() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(intent, FOLDER_PICKER_CODE);
        } catch (Exception e) {
            toast("Could not open the folder picker.");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode != FOLDER_PICKER_CODE || resultCode != RESULT_OK
                || data == null || data.getData() == null) {
            return;
        }

        Uri treeUri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(
                    treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            loadFolderSongs(treeUri);
        } catch (Exception e) {
            toast("Could not access that folder: " + e.getMessage());
        }
    }

    private void loadFolderSongs(Uri treeUri) {
        songList.removeAllViews();
        songInfo.clear();
        songs.clear();
        statusText.setText("Scanning selected folder...");

        try {
            String rootId = DocumentsContract.getTreeDocumentId(treeUri);
            collectFolderSongs(treeUri, rootId, 0);
            sortAndRenderSongs();
            statusText.setText(songInfo.isEmpty()
                    ? "No audio files found in this folder."
                    : "Folder loaded. Tap a song to play it.");
            toast(songInfo.size() + " audio files loaded");
        } catch (Exception e) {
            statusText.setText("Could not read folder: " + e.getMessage());
            toast("Folder scan failed.");
        }
    }

    private void collectFolderSongs(Uri treeUri, String documentId, int depth) {
        if (depth > 12) return;

        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                treeUri, documentId);

        String[] projection = {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
        };

        try (Cursor cursor = getContentResolver().query(
                childrenUri, projection, null, null, null)) {
            if (cursor == null) return;

            int idCol = cursor.getColumnIndexOrThrow(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID);
            int nameCol = cursor.getColumnIndexOrThrow(
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME);
            int mimeCol = cursor.getColumnIndexOrThrow(
                    DocumentsContract.Document.COLUMN_MIME_TYPE);

            while (cursor.moveToNext()) {
                String childId = cursor.getString(idCol);
                String name = cursor.getString(nameCol);
                String mime = cursor.getString(mimeCol);

                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    collectFolderSongs(treeUri, childId, depth + 1);
                    continue;
                }

                if (name == null || !isAudioFile(name, mime)) continue;

                Uri fileUri = DocumentsContract.buildDocumentUriUsingTree(
                        treeUri, childId);
                String title = name.replaceFirst("(?i)\\.(mp3|m4a|aac|wav|ogg|flac|opus|wma)$", "");
                if (title.trim().isEmpty()) title = "Unknown title";

                MediaMetadata metadata = new MediaMetadata.Builder()
                        .setTitle(title)
                        .setArtist("Selected folder")
                        .build();

                MediaItem item = new MediaItem.Builder()
                        .setMediaId(fileUri.toString())
                        .setUri(fileUri)
                        .setMediaMetadata(metadata)
                        .build();

                songInfo.add(new SongInfo(
                        item, title, "Selected folder", 0L, 0L));
            }
        } catch (Exception e) {
            // Skip folders that the selected provider does not allow us to read.
        }
    }

    private boolean isAudioFile(String name, String mime) {
        if (mime != null && mime.toLowerCase(Locale.ROOT).startsWith("audio/")) {
            return true;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".mp3") || lower.endsWith(".m4a")
                || lower.endsWith(".aac") || lower.endsWith(".wav")
                || lower.endsWith(".ogg") || lower.endsWith(".flac")
                || lower.endsWith(".opus") || lower.endsWith(".wma");
    }

    private boolean isScanning = false;
    private static final String SONG_CACHE_PREFS = "pk_music_cache";
    private static final String SONG_CACHE_KEY = "library_v1";

    private void saveSongCache(ArrayList<SongInfo> list) {
        StringBuilder data = new StringBuilder();
        for (SongInfo song : list) {
            String uri = song.item.localConfiguration == null
                    ? null : song.item.localConfiguration.uri.toString();
            if (uri == null) continue;

            data.append(android.util.Base64.encodeToString(
                    uri.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    android.util.Base64.NO_WRAP)).append("|");
            data.append(android.util.Base64.encodeToString(
                    song.title.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    android.util.Base64.NO_WRAP)).append("|");
            data.append(android.util.Base64.encodeToString(
                    song.artist.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    android.util.Base64.NO_WRAP)).append("|");
            data.append(song.duration).append("|")
                .append(song.dateAdded).append("\\n");
        }

        getSharedPreferences(SONG_CACHE_PREFS, MODE_PRIVATE)
                .edit().putString(SONG_CACHE_KEY, data.toString()).apply();
    }

    private void restoreSongCache() {
        String data = getSharedPreferences(SONG_CACHE_PREFS, MODE_PRIVATE)
                .getString(SONG_CACHE_KEY, "");
        if (data.isEmpty()) return;

        ArrayList<SongInfo> restored = new ArrayList<>();
        try {
            for (String line : data.split("\\n")) {
                String[] parts = line.split("\\|", -1);
                if (parts.length != 5) continue;

                String uriText = new String(android.util.Base64.decode(
                        parts[0], android.util.Base64.NO_WRAP),
                        java.nio.charset.StandardCharsets.UTF_8);
                String title = new String(android.util.Base64.decode(
                        parts[1], android.util.Base64.NO_WRAP),
                        java.nio.charset.StandardCharsets.UTF_8);
                String artist = new String(android.util.Base64.decode(
                        parts[2], android.util.Base64.NO_WRAP),
                        java.nio.charset.StandardCharsets.UTF_8);

                Uri uri = Uri.parse(uriText);
                MediaMetadata metadata = new MediaMetadata.Builder()
                        .setTitle(title).setArtist(artist).build();
                MediaItem item = new MediaItem.Builder()
                        .setMediaId(uriText).setUri(uri)
                        .setMediaMetadata(metadata).build();

                restored.add(new SongInfo(item, title, artist,
                        Long.parseLong(parts[3]), Long.parseLong(parts[4])));
            }
        } catch (Exception e) {
            return;
        }

        if (!restored.isEmpty()) {
            songInfo.clear();
            songInfo.addAll(restored);
            sortAndRenderSongs();
            statusText.setText("Library loaded. Checking for new songs...");
        }
    }

    private void loadSongs() {
        if (isScanning) return;

        if (songInfo.isEmpty()) restoreSongCache();

        isScanning = true;
        if (songInfo.isEmpty()) statusText.setText("Loading music library...");


        new Thread(() -> {
            ArrayList<SongInfo> scanned = new ArrayList<>();
            String[] scanError = new String[1];

            Uri musicUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            String[] projection = {
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.DATE_ADDED
            };

            String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";
            String order = MediaStore.Audio.Media.DATE_ADDED + " DESC";

            try (Cursor cursor = getContentResolver().query(
                    musicUri, projection, selection, null, order)) {

                if (cursor != null) {
                    int idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                    int titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
                    int artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
                    int durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
                    int dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED);

                    while (cursor.moveToNext()) {
                        long id = cursor.getLong(idCol);
                        String title = cursor.getString(titleCol);
                        String artist = cursor.getString(artistCol);
                        long duration = cursor.getLong(durationCol);
                        long dateAdded = cursor.getLong(dateCol);

                        if (title == null || title.trim().isEmpty()) {
                            title = "Unknown title";
                        }
                        if (artist == null || artist.trim().isEmpty()
                                || artist.equals("<unknown>")) {
                            artist = "Unknown artist";
                        }

                        Uri uri = ContentUris.withAppendedId(musicUri, id);
                        MediaMetadata metadata = new MediaMetadata.Builder()
                                .setTitle(title)
                                .setArtist(artist)
                                .build();

                        MediaItem item = new MediaItem.Builder()
                                .setMediaId(uri.toString())
                                .setUri(uri)
                                .setMediaMetadata(metadata)
                                .build();

                        scanned.add(new SongInfo(item, title, artist, duration, dateAdded));
                    }
                }
            } catch (Exception e) {
                scanError[0] = e.getMessage();
            }

            handler.post(() -> {
                isScanning = false;
                if (scanError[0] == null) {
                    songInfo.clear();
                    songInfo.addAll(scanned);
                    saveSongCache(scanned);
                }

                sortAndRenderSongs();

                if (scanError[0] != null) {
                    statusText.setText("Could not refresh music library: " + scanError[0]);
                } else if (!songInfo.isEmpty()) {
                    statusText.setText("Library ready.");
                }
            });
        }, "PK-Music-Scanner").start();
    }

    private void sortAndRenderSongs() {
        int selected = sortSpinner == null ? 0 : sortSpinner.getSelectedItemPosition();

        Comparator<SongInfo> comparator;
        switch (selected) {
            case 1:
                comparator = (a, b) -> Long.compare(a.dateAdded, b.dateAdded);
                break;
            case 2:
                comparator = Comparator.comparing(a -> a.titleLower);
                break;
            case 3:
                comparator = Comparator.comparing(a -> a.artistLower);
                break;
            default:
                comparator = (a, b) -> Long.compare(b.dateAdded, a.dateAdded);
                break;
        }

        Collections.sort(songInfo, comparator);
        songs.clear();
        for (SongInfo info : songInfo) {
            songs.add(info.item);
        }
        renderSongRows();
    }

    private void renderSongRows() {
        if (songList == null) return;

        songList.removeAllViews();
        String query = searchInput == null ? ""
                : searchInput.getText().toString().trim().toLowerCase(Locale.ROOT);
        int shown = 0;

        for (int i = 0; i < songInfo.size(); i++) {
            SongInfo info = songInfo.get(i);

            if (!query.isEmpty()
                    && !info.titleLower.contains(query)
                    && !info.artistLower.contains(query)) {
                continue;
            }

            addSongRow(i, info.title, info.artist, info.duration);
            shown++;
        }

        if (query.isEmpty()) {
            countText.setText(songInfo.size()
                    + (songInfo.size() == 1 ? " song found" : " songs found"));
        } else {
            countText.setText(shown + " matching / " + songInfo.size() + " total");
        }

        if (songInfo.isEmpty()) {
            statusText.setText("No music files found. Add audio files and refresh.");
        } else if (shown == 0) {
            statusText.setText("No matching songs. Try another search.");
        } else {
            statusText.setText("Tap any song to play it.");
        }
    }

    private void addSongRow(int index, String title, String artist, long duration) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));

        boolean isCurrent = false;
        if (controller != null && controller.getCurrentMediaItem() != null
                && index >= 0 && index < songInfo.size()) {
            String currentId = controller.getCurrentMediaItem().mediaId;
            String rowId = songInfo.get(index).item.mediaId;
            isCurrent = currentId != null && currentId.equals(rowId);
        }

        row.setBackgroundColor(isCurrent
                ? Color.rgb(58, 22, 31) : SURFACE);

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(isCurrent ? RED : WHITE);
        titleView.setTextSize(15);
        titleView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        titleView.setMaxLines(1);

        TextView artistView = new TextView(this);
        artistView.setText(artist + "  •  " + formatDuration(duration));
        artistView.setTextColor(SECONDARY);
        artistView.setTextSize(12);
        artistView.setMaxLines(1);

        TextView icon = new TextView(this);
        icon.setText(isCurrent ? "♫" : "♪");
        icon.setTextColor(isCurrent ? RED : SECONDARY);
        icon.setTextSize(26);
        icon.setPadding(dp(12), 0, dp(8), 0);

        details.addView(titleView);
        details.addView(artistView);

        row.addView(details, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        row.addView(icon);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(7);
        songList.addView(row, params);

        row.setOnClickListener(v -> playSong(index));
    }

    private void playSong(int index) {
        if (controller == null) {
            toast("Audio player is still connecting. Try again.");
            return;
        }
        if (index < 0 || index >= songs.size()) return;

        controller.setMediaItems(songs, index, 0L);
        controller.prepare();
        controller.play();
        updateNowPlaying();
        showPlayerScreen();
    }

    private void updateNowPlaying() {
        if (controller == null || controller.getCurrentMediaItem() == null) {
            nowTitle.setText("No song selected");
            nowArtist.setText("Choose a song from your library");
            updatePlayButton();
            updateProgress();
            return;
        }

        MediaMetadata metadata = controller.getCurrentMediaItem().mediaMetadata;
        nowTitle.setText(metadata.title != null ? metadata.title : "Unknown title");
        nowArtist.setText(metadata.artist != null ? metadata.artist : "Unknown artist");
        updateAlbumArtwork(metadata);
        updatePlayButton();
        updateProgress();
        updateVinylAnimation(controller.isPlaying());
    }

    private void updateAlbumArtwork(MediaMetadata metadata) {
        if (albumArtwork == null) return;
        byte[] artwork = metadata.artworkData;
        if (artwork != null && artwork.length > 0) {
            android.graphics.Bitmap bitmap = BitmapFactory.decodeByteArray(
                    artwork, 0, artwork.length);
            if (bitmap != null) {
                albumArtwork.setImageBitmap(bitmap);
                if (artworkFallback != null) artworkFallback.setVisibility(View.GONE);
                return;
            }
        }
        albumArtwork.setImageDrawable(makeFallbackArtwork());
        if (artworkFallback != null) artworkFallback.setVisibility(View.VISIBLE);
    }

    private GradientDrawable makeCircleDrawable(int fill, int stroke, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(fill);
        if (strokeWidth > 0) drawable.setStroke(strokeWidth, stroke);
        return drawable;
    }

    private android.graphics.drawable.Drawable makeFallbackArtwork() {
        android.graphics.drawable.GradientDrawable fallback =
                makeCircleDrawable(Color.rgb(32, 18, 27), RED, dp(3));
        return fallback;
    }

    private void chooseQuoteForCurrentSong() {
        if (controller == null || controller.getCurrentMediaItem() == null
                || quoteText == null) return;
        String id = controller.getCurrentMediaItem().mediaId;
        if (id == null) id = String.valueOf(controller.getCurrentMediaItemIndex());
        if (id.equals(quoteSongId)) return;
        quoteSongId = id;
        int index = quoteRandom.nextInt(MUSIC_QUOTES.length);
        if (MUSIC_QUOTES.length > 1 && index == lastQuoteIndex) {
            index = (index + 1 + quoteRandom.nextInt(MUSIC_QUOTES.length - 1))
                    % MUSIC_QUOTES.length;
        }
        lastQuoteIndex = index;
        quoteText.setText("\u201c" + MUSIC_QUOTES[index] + "\u201d");
    }

    private void updateVinylAnimation(boolean isPlaying) {
        if (vinylDisc == null) return;
        if (isPlaying) {
            if (vinylRotation == null) {
                vinylRotation = new android.view.animation.RotateAnimation(
                        0f, 360f,
                        android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f,
                        android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f);
                vinylRotation.setDuration(1800);
                vinylRotation.setRepeatCount(android.view.animation.Animation.INFINITE);
                vinylRotation.setInterpolator(new android.view.animation.LinearInterpolator());
                vinylRotation.setFillAfter(true);
            }
            if (vinylDisc.getAnimation() == null) vinylDisc.startAnimation(vinylRotation);
        } else {
            vinylDisc.clearAnimation();
        }
    }

    private void updatePlayButton() {
        if (playButton != null) {
            playButton.setText(controller != null && controller.isPlaying() ? "⏸" : "▶");
        }
        updateMiniPlayButton();
    }

    private void updateProgress() {
        if (controller == null) {
            timeText.setText("0:00 / 0:00");
            return;
        }

        long duration = controller.getDuration();
        long position = controller.getCurrentPosition();

        if (duration < 0) duration = 0;
        if (position < 0) position = 0;

        if (!userSeeking) {
            seekBar.setProgress(duration > 0
                    ? (int) Math.min(1000, position * 1000.0 / duration) : 0);
        }

        timeText.setText(formatDuration(position) + " / " + formatDuration(duration));
    }

    private void updateModeButtons() {
        if (shuffleButton == null || repeatButton == null) return;

        shuffleButton.setText(shuffleEnabled ? "🔀 SHUFFLE ON" : "SHUFFLE OFF");
        shuffleButton.setTextColor(shuffleEnabled ? SKY_BLUE : SECONDARY);

        String repeatText;
        if (repeatMode == Player.REPEAT_MODE_ALL) {
            repeatText = "REPEAT ALL";
        } else if (repeatMode == Player.REPEAT_MODE_ONE) {
            repeatText = "REPEAT ONE";
        } else {
            repeatText = "REPEAT OFF";
        }

        repeatButton.setText(repeatText);
        repeatButton.setTextColor(
                repeatMode == Player.REPEAT_MODE_OFF ? SECONDARY : SKY_BLUE);
    }

    private String formatDuration(long milliseconds) {
        long seconds = Math.max(0, milliseconds / 1000);
        return String.format(Locale.getDefault(), "%d:%02d",
                seconds / 60, seconds % 60);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(progressUpdater);

        if (controller != null) {
            controller.removeListener(playerListener);
        }

        if (controllerFuture != null) {
            MediaController.releaseFuture(controllerFuture);
        }

        super.onDestroy();
    }
}
