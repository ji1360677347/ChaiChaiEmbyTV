package com.chaichai.questnative;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String PREFS = "quest_native";
    private static final String KEY_SERVER = "server";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_DANMAKU_API = "danmaku_api";
    private static final String KEY_DANMAKU_API_PREFIX = "danmaku_api_";
    private static final String KEY_AUTO_NEXT = "auto_next";
    private static final String KEY_RESUME_PLAYBACK = "resume_playback";
    private static final String KEY_PROXY_ENABLED = "proxy_enabled";
    private static final String KEY_PROXY_HOST = "proxy_host";
    private static final String KEY_PROXY_PORT = "proxy_port";

    private FrameLayout root;
    private EmbyClient client;
    private final ArrayList<NavEntry> backStack = new ArrayList<>();
    private MediaItem currentDetail;
    private VideoView currentVideo;
    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private Runnable progressRunnable;
    private boolean userSeeking;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        root = new FrameLayout(this);
        setContentView(root);

        SharedPreferences prefs = prefs();
        String server = prefs.getString(KEY_SERVER, "");
        String token = prefs.getString(KEY_TOKEN, "");
        String userId = prefs.getString(KEY_USER_ID, "");
        if (!server.isEmpty() && !token.isEmpty() && !userId.isEmpty()) {
            client = new EmbyClient(server, token, userId);
            showHome();
        } else {
            showLogin();
        }
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private void showLogin() {
        currentVideo = null;
        currentDetail = null;
        root.removeAllViews();
        LinearLayout page = vertical();
        page.setGravity(Gravity.CENTER);
        page.setPadding(dp(48), dp(24), dp(48), dp(24));
        page.setBackgroundColor(Color.rgb(245, 247, 250));
        root.addView(page, fill());

        TextView title = title("ChaiChai Quest Native");
        page.addView(title);
        page.addView(label("原生 Emby 客户端 · Quest 3 触摸适配"));

        EditText server = input("服务器地址，例如 192.168.1.10:8096");
        EditText username = input("用户名");
        EditText password = input("密码");
        EditText danmaku = input("弹幕接口");
        danmaku.setText(prefs().getString(KEY_DANMAKU_API, AppConfig.DEFAULT_DANMAKU_API));

        page.addView(server, wideInputParams());
        page.addView(username, wideInputParams());
        page.addView(password, wideInputParams());
        page.addView(danmaku, wideInputParams());

        Button login = button("登录");
        page.addView(login, wideButtonParams());
        login.setOnClickListener(v -> {
            String d = danmaku.getText().toString().trim();
            if (d.isEmpty()) d = AppConfig.DEFAULT_DANMAKU_API;
            prefs().edit().putString(KEY_DANMAKU_API, d).apply();
            doLogin(server.getText().toString(), username.getText().toString(), password.getText().toString());
        });
        server.requestFocus();
        server.postDelayed(() -> showKeyboard(server), 300);
    }

    private void doLogin(String server, String username, String password) {
        showLoading("登录中...");
        new AsyncTask<Void, Void, Object>() {
            @Override
            protected Object doInBackground(Void... voids) {
                try {
                    return EmbyClient.login(server, username, password);
                } catch (Exception e) {
                    return e;
                }
            }

            @Override
            protected void onPostExecute(Object result) {
                if (result instanceof Exception) {
                    toast(((Exception) result).getMessage());
                    showLogin();
                    return;
                }
                client = (EmbyClient) result;
                prefs().edit()
                        .putString(KEY_SERVER, client.server)
                        .putString(KEY_TOKEN, client.token)
                        .putString(KEY_USER_ID, client.userId)
                        .apply();
                showHome();
            }
        }.execute();
    }

    private void showHome() {
        backStack.clear();
        loadHome();
    }

    private void loadHome() {
        currentVideo = null;
        currentDetail = null;
        showLoading("加载首页...");
        new AsyncTask<Void, Void, HomeResult>() {
            @Override
            protected HomeResult doInBackground(Void... voids) {
                try {
                    HomeResult result = new HomeResult();
                    result.resume = client.getResumeItems();
                    result.latest = client.getLatestItems();
                    result.libraries = client.getLibraries();
                    return result;
                } catch (Exception e) {
                    return HomeResult.failure(e);
                }
            }

            @Override
            protected void onPostExecute(HomeResult result) {
                if (result.error != null) showError(result.error.getMessage());
                else showHomeDashboard(result);
            }
        }.execute();
    }

    private void showHomeDashboard(HomeResult data) {
        root.removeAllViews();
        LinearLayout shell = horizontal();
        shell.setBackgroundColor(Color.rgb(14, 18, 24));
        root.addView(shell, fill());

        LinearLayout nav = vertical();
        nav.setPadding(dp(16), dp(24), dp(16), dp(24));
        nav.setBackgroundColor(Color.rgb(20, 25, 33));
        shell.addView(nav, new LinearLayout.LayoutParams(dp(190), -1));

        TextView brand = title("ChaiChai");
        brand.setTextColor(Color.WHITE);
        brand.setTextSize(25);
        nav.addView(brand, new LinearLayout.LayoutParams(-1, dp(58)));
        Button home = navButton("首页");
        Button libraries = navButton("媒体库");
        Button search = navButton("搜索");
        Button settings = navButton("设置");
        Button logout = navButton("退出");
        nav.addView(home);
        nav.addView(libraries);
        nav.addView(search);
        nav.addView(settings);
        nav.addView(logout);

        home.setOnClickListener(v -> loadHome());
        libraries.setOnClickListener(v -> loadItems(null, "媒体库", true));
        search.setOnClickListener(v -> showSearch());
        settings.setOnClickListener(v -> showSettings());
        logout.setOnClickListener(v -> {
            prefs().edit().remove(KEY_TOKEN).remove(KEY_USER_ID).apply();
            client = null;
            showLogin();
        });

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = vertical();
        content.setPadding(dp(28), dp(24), dp(28), dp(32));
        scroll.addView(content);
        shell.addView(scroll, new LinearLayout.LayoutParams(0, -1, 1));

        TextView title = title("首页");
        title.setTextColor(Color.WHITE);
        title.setTextSize(32);
        content.addView(title);
        content.addView(labelWhite("快速继续、发现最新内容，或进入媒体库浏览。"));

        if (!data.resume.isEmpty()) content.addView(rowSection("继续观看", data.resume, true));
        if (!data.latest.isEmpty()) content.addView(rowSection("最新添加", data.latest, true));
        if (!data.libraries.isEmpty()) content.addView(rowSection("媒体库", data.libraries, false));
    }

    private void loadItems(String parentId, String title, boolean replaceCurrent) {
        currentVideo = null;
        currentDetail = null;
        showLoading("加载中...");
        new AsyncTask<Void, Void, LoadResult>() {
            @Override
            protected LoadResult doInBackground(Void... voids) {
                try {
                    return LoadResult.success(parentId == null ? client.getLibraries() : client.getItems(parentId));
                } catch (Exception e) {
                    return LoadResult.failure(e);
                }
            }

            @Override
            protected void onPostExecute(LoadResult result) {
                if (result.error != null) {
                    showError(result.error.getMessage());
                } else {
                    if (replaceCurrent) {
                        if (backStack.isEmpty()) backStack.add(new NavEntry(parentId, title));
                        else backStack.set(backStack.size() - 1, new NavEntry(parentId, title));
                    } else {
                        backStack.add(new NavEntry(parentId, title));
                    }
                    showGrid(title, result.items);
                }
            }
        }.execute();
    }

    private void showGrid(String titleText, List<MediaItem> items) {
        root.removeAllViews();
        LinearLayout page = vertical();
        page.setBackgroundColor(Color.rgb(14, 18, 24));
        root.addView(page, fill());

        LinearLayout toolbar = horizontal();
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(12), 0, dp(12), 0);
        toolbar.setBackgroundColor(Color.rgb(24, 30, 39));
        page.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(62)));

        Button back = button("返回");
        TextView title = title(titleText);
        title.setTextColor(Color.WHITE);
        Button search = button("搜索");
        Button settings = button("设置");
        Button logout = button("退出");
        toolbar.addView(back, new LinearLayout.LayoutParams(dp(86), dp(44)));
        toolbar.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        toolbar.addView(search, new LinearLayout.LayoutParams(dp(86), dp(44)));
        toolbar.addView(settings, new LinearLayout.LayoutParams(dp(86), dp(44)));
        toolbar.addView(logout, new LinearLayout.LayoutParams(dp(86), dp(44)));

        back.setOnClickListener(v -> goBack());
        search.setOnClickListener(v -> showSearch());
        settings.setOnClickListener(v -> showSettings());
        logout.setOnClickListener(v -> {
            prefs().edit().remove(KEY_TOKEN).remove(KEY_USER_ID).apply();
            client = null;
            showLogin();
        });

        ScrollView scroll = new ScrollView(this);
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(5);
        grid.setPadding(dp(18), dp(18), dp(18), dp(18));
        scroll.addView(grid);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        if (items.isEmpty()) {
            TextView empty = label("没有找到内容");
            empty.setTextColor(Color.WHITE);
            grid.addView(empty, new ViewGroup.LayoutParams(dp(520), dp(80)));
            return;
        }
        for (MediaItem item : items) {
            grid.addView(card(item), new ViewGroup.LayoutParams(dp(190), dp(308)));
        }
    }

    private View card(MediaItem item) {
        LinearLayout card = vertical();
        card.setPadding(dp(8), dp(8), dp(8), dp(8));
        card.setClickable(true);
        card.setFocusable(true);
        card.setBackgroundColor(Color.rgb(32, 39, 49));

        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(Color.rgb(45, 54, 68));
        card.addView(image, new LinearLayout.LayoutParams(-1, dp(220)));
        if (item.hasPrimaryImage) loadImage(item.id, image);

        TextView name = label(item.displayName());
        name.setTextColor(Color.WHITE);
        name.setMaxLines(2);
        card.addView(name, new LinearLayout.LayoutParams(-1, -2));

        TextView meta = label(item.isPlayable() ? item.subtitle() : item.type);
        meta.setTextColor(Color.rgb(174, 184, 198));
        meta.setMaxLines(1);
        card.addView(meta, new LinearLayout.LayoutParams(-1, -2));

        card.setOnClickListener(v -> {
            if (item.isPlayable()) {
                showDetail(item);
            } else {
                loadItems(item.id, item.name, false);
            }
        });
        return card;
    }

    private View rowSection(String titleText, List<MediaItem> items, boolean playableRow) {
        LinearLayout section = vertical();
        section.setPadding(0, dp(20), 0, dp(4));
        TextView heading = sectionTitle(titleText);
        section.addView(heading);

        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = horizontal();
        row.setPadding(0, 0, dp(12), 0);
        scroll.addView(row);
        section.addView(scroll, new LinearLayout.LayoutParams(-1, dp(328)));

        for (MediaItem item : items) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(190), dp(308));
            params.setMargins(0, 0, dp(14), 0);
            row.addView(card(item), params);
        }
        return section;
    }

    private void showDetail(MediaItem item) {
        if (item.isPlayable() && !item.detailsLoaded) {
            currentVideo = null;
            currentDetail = item;
            showLoading("加载详情...");
            new AsyncTask<Void, Void, Object>() {
                @Override
                protected Object doInBackground(Void... voids) {
                    try {
                        return client.getItem(item.id);
                    } catch (Exception e) {
                        return e;
                    }
                }

                @Override
                protected void onPostExecute(Object result) {
                    if (result instanceof Exception) {
                        toast(((Exception) result).getMessage());
                        renderDetail(item);
                    } else {
                        MediaItem detail = (MediaItem) result;
                        detail.selectedMediaSourceId = item.selectedMediaSourceId;
                        detail.selectedSubtitleIndex = item.selectedSubtitleIndex;
                        renderDetail(detail);
                    }
                }
            }.execute();
            return;
        }
        renderDetail(item);
    }

    private void renderDetail(MediaItem item) {
        currentVideo = null;
        currentDetail = item;
        root.removeAllViews();
        LinearLayout page = vertical();
        page.setBackgroundColor(Color.rgb(14, 18, 24));
        root.addView(page, fill());

        LinearLayout toolbar = horizontal();
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(12), 0, dp(12), 0);
        toolbar.setBackgroundColor(Color.rgb(24, 30, 39));
        page.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(62)));

        Button back = button("返回");
        TextView title = title(item.displayName());
        title.setTextColor(Color.WHITE);
        toolbar.addView(back, new LinearLayout.LayoutParams(dp(86), dp(44)));
        toolbar.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        back.setOnClickListener(v -> goBack());

        LinearLayout content = horizontal();
        content.setPadding(dp(36), dp(30), dp(36), dp(30));
        page.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));

        ImageView poster = new ImageView(this);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        poster.setBackgroundColor(Color.rgb(45, 54, 68));
        content.addView(poster, new LinearLayout.LayoutParams(dp(280), dp(420)));
        if (item.hasPrimaryImage) loadImage(item.id, poster);

        LinearLayout info = vertical();
        info.setPadding(dp(28), 0, 0, 0);
        content.addView(info, new LinearLayout.LayoutParams(0, -1, 1));

        TextView name = title(item.displayName());
        name.setTextColor(Color.WHITE);
        name.setTextSize(30);
        info.addView(name);

        TextView meta = label(item.subtitle());
        meta.setTextColor(Color.rgb(174, 184, 198));
        info.addView(meta);

        String progress = item.progressText();
        if (!progress.isEmpty()) {
            TextView progressView = label(progress);
            progressView.setTextColor(Color.rgb(126, 211, 166));
            info.addView(progressView);
        }

        TextView overview = label(item.overview == null || item.overview.isEmpty() ? "暂无简介" : item.overview);
        overview.setTextColor(Color.rgb(221, 228, 238));
        overview.setTextSize(17);
        overview.setPadding(0, dp(18), dp(18), dp(18));
        info.addView(overview, new LinearLayout.LayoutParams(-1, 0, 1));

        TextView danmaku = label("弹幕接口：" + prefs().getString(KEY_DANMAKU_API, AppConfig.DEFAULT_DANMAKU_API));
        danmaku.setTextColor(Color.rgb(174, 184, 198));
        info.addView(danmaku);

        TextView source = label("资源版本：" + item.selectedMediaSourceLabel());
        source.setTextColor(Color.rgb(174, 184, 198));
        info.addView(source);
        TextView subtitle = label("字幕：" + item.selectedSubtitleLabel());
        subtitle.setTextColor(Color.rgb(174, 184, 198));
        info.addView(subtitle);

        LinearLayout selectors = horizontal();
        Button sourceButton = button("版本");
        Button subtitleButton = button("字幕");
        selectors.addView(sourceButton, new LinearLayout.LayoutParams(dp(150), dp(54)));
        selectors.addView(subtitleButton, new LinearLayout.LayoutParams(dp(150), dp(54)));
        info.addView(selectors);
        sourceButton.setOnClickListener(v -> showSourceChooser(item, false, null, null));
        subtitleButton.setOnClickListener(v -> showSubtitleChooser(item, null));

        Button play = button(item.playbackPositionTicks > 0 && !item.played ? "继续播放" : "播放");
        info.addView(play, new LinearLayout.LayoutParams(dp(180), dp(56)));
        play.setOnClickListener(v -> play(item));
    }

    private void showSearch() {
        root.removeAllViews();
        LinearLayout page = vertical();
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.setPadding(dp(48), dp(42), dp(48), dp(24));
        page.setBackgroundColor(Color.rgb(245, 247, 250));
        root.addView(page, fill());

        TextView title = title("搜索媒体");
        page.addView(title);
        EditText query = input("输入片名、剧集或视频名称");
        page.addView(query, wideInputParams());

        LinearLayout actions = horizontal();
        actions.setGravity(Gravity.CENTER);
        Button search = button("搜索");
        Button back = button("返回");
        actions.addView(search, new LinearLayout.LayoutParams(dp(160), dp(54)));
        actions.addView(back, new LinearLayout.LayoutParams(dp(160), dp(54)));
        page.addView(actions);

        search.setOnClickListener(v -> doSearch(query.getText().toString().trim()));
        back.setOnClickListener(v -> goBack());
        query.requestFocus();
        query.postDelayed(() -> showKeyboard(query), 250);
    }

    private void doSearch(String query) {
        if (query.isEmpty()) {
            toast("请输入搜索内容");
            return;
        }
        showLoading("搜索中...");
        new AsyncTask<Void, Void, LoadResult>() {
            @Override
            protected LoadResult doInBackground(Void... voids) {
                try {
                    return LoadResult.success(client.search(query));
                } catch (Exception e) {
                    return LoadResult.failure(e);
                }
            }

            @Override
            protected void onPostExecute(LoadResult result) {
                if (result.error != null) showError(result.error.getMessage());
                else showGrid("搜索：" + query, result.items);
            }
        }.execute();
    }

    private void showSettings() {
        root.removeAllViews();
        LinearLayout page = vertical();
        page.setBackgroundColor(Color.rgb(14, 18, 24));
        root.addView(page, fill());

        LinearLayout toolbar = horizontal();
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(12), 0, dp(12), 0);
        toolbar.setBackgroundColor(Color.rgb(24, 30, 39));
        page.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(62)));

        Button backTop = button("返回");
        TextView title = title("设置");
        title.setTextColor(Color.WHITE);
        toolbar.addView(backTop, new LinearLayout.LayoutParams(dp(86), dp(44)));
        toolbar.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        backTop.setOnClickListener(v -> goBack());

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = vertical();
        content.setPadding(dp(36), dp(24), dp(36), dp(32));
        scroll.addView(content);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        SharedPreferences prefs = prefs();
        content.addView(sectionTitle("服务器"));
        TextView server = settingText("当前服务器", prefs.getString(KEY_SERVER, "未登录"));
        content.addView(server);

        content.addView(sectionTitle("弹幕"));
        EditText[] danmakuInputs = new EditText[5];
        for (int i = 0; i < danmakuInputs.length; i++) {
            EditText input = input("弹幕 API " + (i + 1));
            String key = i == 0 ? KEY_DANMAKU_API : KEY_DANMAKU_API_PREFIX + (i + 1);
            input.setText(prefs.getString(key, i == 0 ? AppConfig.DEFAULT_DANMAKU_API : ""));
            input.setSingleLine(true);
            content.addView(input, settingsInputParams());
            danmakuInputs[i] = input;
        }

        content.addView(sectionTitle("播放"));
        CheckBox resume = checkBox("启动播放时自动从上次进度继续");
        resume.setChecked(prefs.getBoolean(KEY_RESUME_PLAYBACK, true));
        content.addView(resume);
        CheckBox autoNext = checkBox("剧集播放完成后自动播放下一集");
        autoNext.setChecked(prefs.getBoolean(KEY_AUTO_NEXT, false));
        content.addView(autoNext);

        content.addView(sectionTitle("代理"));
        CheckBox proxyEnabled = checkBox("启用 HTTP/SOCKS 代理");
        proxyEnabled.setChecked(prefs.getBoolean(KEY_PROXY_ENABLED, false));
        content.addView(proxyEnabled);
        EditText proxyHost = input("代理地址，例如 192.168.1.2");
        proxyHost.setText(prefs.getString(KEY_PROXY_HOST, ""));
        content.addView(proxyHost, settingsInputParams());
        EditText proxyPort = input("代理端口，例如 7890");
        proxyPort.setText(prefs.getString(KEY_PROXY_PORT, ""));
        content.addView(proxyPort, settingsInputParams());

        content.addView(sectionTitle("关于"));
        content.addView(settingText("版本", "ChaiChai Quest Native 0.7.0"));
        content.addView(settingText("默认弹幕接口", AppConfig.DEFAULT_DANMAKU_API));

        LinearLayout actions = horizontal();
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(0, dp(18), 0, 0);
        Button save = button("保存");
        Button back = button("返回");
        actions.addView(save, new LinearLayout.LayoutParams(dp(180), dp(56)));
        actions.addView(back, new LinearLayout.LayoutParams(dp(180), dp(56)));
        content.addView(actions);

        save.setOnClickListener(v -> {
            SharedPreferences.Editor editor = prefs().edit();
            for (int i = 0; i < danmakuInputs.length; i++) {
                String value = danmakuInputs[i].getText().toString().trim();
                if (i == 0 && value.isEmpty()) value = AppConfig.DEFAULT_DANMAKU_API;
                String key = i == 0 ? KEY_DANMAKU_API : KEY_DANMAKU_API_PREFIX + (i + 1);
                editor.putString(key, value);
            }
            editor.putBoolean(KEY_RESUME_PLAYBACK, resume.isChecked());
            editor.putBoolean(KEY_AUTO_NEXT, autoNext.isChecked());
            editor.putBoolean(KEY_PROXY_ENABLED, proxyEnabled.isChecked());
            editor.putString(KEY_PROXY_HOST, proxyHost.getText().toString().trim());
            editor.putString(KEY_PROXY_PORT, proxyPort.getText().toString().trim());
            editor.apply();
            toast("已保存");
            goBack();
        });
        back.setOnClickListener(v -> goBack());
        danmakuInputs[0].requestFocus();
    }

    private void play(MediaItem item) {
        stopProgressReporter();
        currentDetail = item;
        root.removeAllViews();
        FrameLayout player = new FrameLayout(this);
        player.setBackgroundColor(Color.BLACK);
        root.addView(player, fill());

        VideoView video = new VideoView(this);
        currentVideo = video;
        player.addView(video, fill());

        LinearLayout controls = vertical();
        controls.setPadding(dp(18), dp(14), dp(18), dp(18));
        controls.setBackgroundColor(Color.argb(150, 0, 0, 0));
        player.addView(controls, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        TextView title = title(item.displayName());
        title.setTextColor(Color.WHITE);
        controls.addView(title);

        TextView progress = label("00:00 / " + item.runtimeText());
        progress.setTextColor(Color.rgb(221, 228, 238));
        controls.addView(progress);

        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(1000);
        seekBar.setFocusable(true);
        controls.addView(seekBar, new LinearLayout.LayoutParams(-1, dp(42)));

        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        Button back = button("返回");
        Button rewind = button("-10秒");
        Button pause = button("暂停");
        Button forward = button("+30秒");
        Button sourceButton = button("版本");
        Button subtitleButton = button("字幕");
        row.addView(back, new LinearLayout.LayoutParams(dp(140), dp(54)));
        row.addView(rewind, new LinearLayout.LayoutParams(dp(140), dp(54)));
        row.addView(pause, new LinearLayout.LayoutParams(dp(140), dp(54)));
        row.addView(forward, new LinearLayout.LayoutParams(dp(140), dp(54)));
        row.addView(sourceButton, new LinearLayout.LayoutParams(dp(140), dp(54)));
        row.addView(subtitleButton, new LinearLayout.LayoutParams(dp(140), dp(54)));
        controls.addView(row);

        back.setOnClickListener(v -> {
            stopPlaybackAndReport();
            showDetail(item);
        });
        rewind.setOnClickListener(v -> seekBy(-10000, item, progress, seekBar));
        forward.setOnClickListener(v -> seekBy(30000, item, progress, seekBar));
        sourceButton.setOnClickListener(v -> showSourceChooser(item, true, progress, seekBar));
        subtitleButton.setOnClickListener(v -> showSubtitleChooser(item, video));
        pause.setOnClickListener(v -> {
            if (video.isPlaying()) {
                video.pause();
                pause.setText("播放");
                reportPlaybackProgress(item, true);
            } else {
                video.start();
                pause.setText("暂停");
                reportPlaybackProgress(item, false);
            }
        });
        video.setOnClickListener(v -> pause.performClick());
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                if (!fromUser || video.getDuration() <= 0) return;
                long positionMs = (long) video.getDuration() * value / 1000L;
                progress.setText(MediaItem.positionText(positionMs * 10000L) + " / " + MediaItem.positionText(video.getDuration() * 10000L));
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
                userSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                if (video.getDuration() > 0) {
                    int positionMs = (int) ((long) video.getDuration() * bar.getProgress() / 1000L);
                    video.seekTo(positionMs);
                    item.playbackPositionTicks = positionMs * 10000L;
                    reportPlaybackProgress(item, !video.isPlaying());
                }
                userSeeking = false;
            }
        });
        MediaSourceInfo source = item.selectedMediaSource();
        video.setVideoURI(Uri.parse(client.streamUrl(item.id, source == null ? null : source.id)));
        video.setOnPreparedListener(mp -> {
            mp.setLooping(false);
            if (prefs().getBoolean(KEY_RESUME_PLAYBACK, true) && item.playbackPositionTicks > 0 && !item.played) {
                int positionMs = (int) Math.min(Integer.MAX_VALUE, item.playbackPositionTicks / 10000L);
                video.seekTo(positionMs);
            }
            if (item.selectedSubtitleIndex >= 0) loadSubtitle(video, item);
            video.start();
            reportPlaybackStart(item);
            startProgressReporter(item, progress, seekBar);
        });
        video.setOnCompletionListener(mp -> {
            item.played = true;
            item.playbackPositionTicks = 0;
            stopProgressReporter();
            reportPlayback(item, "stop", item.runtimeTicks, true);
            pause.setText("播放");
            progress.setText("已播放完成");
            seekBar.setProgress(1000);
            if (prefs().getBoolean(KEY_AUTO_NEXT, false)) toast("自动下一集将在后续版本接入");
        });
        video.setOnErrorListener((mp, what, extra) -> {
            toast("播放失败: " + what);
            return true;
        });
    }

    private void showSourceChooser(MediaItem item, boolean restartPlayback, TextView progress, SeekBar seekBar) {
        if (item.mediaSources.isEmpty()) {
            toast("没有可切换的资源版本");
            return;
        }
        String[] labels = new String[item.mediaSources.size()];
        int checked = 0;
        for (int i = 0; i < item.mediaSources.size(); i++) {
            MediaSourceInfo source = item.mediaSources.get(i);
            labels[i] = source.label() + "\n" + source.subLabel();
            if (source.id != null && source.id.equals(item.selectedMediaSourceId)) checked = i;
        }
        new AlertDialog.Builder(this)
                .setTitle("资源版本")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    MediaSourceInfo source = item.mediaSources.get(which);
                    item.selectedMediaSourceId = source.id;
                    dialog.dismiss();
                    if (restartPlayback && currentVideo != null) {
                        item.playbackPositionTicks = currentVideo.getCurrentPosition() * 10000L;
                        stopPlaybackAndReport();
                        play(item);
                    } else {
                        showDetail(item);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showSubtitleChooser(MediaItem item, VideoView video) {
        ArrayList<String> labels = new ArrayList<>();
        labels.add("禁用字幕");
        for (SubtitleTrack track : item.subtitles) labels.add(track.label());
        new AlertDialog.Builder(this)
                .setTitle("字幕")
                .setSingleChoiceItems(labels.toArray(new String[0]), selectedSubtitleChoice(item), (dialog, which) -> {
                    if (which == 0) {
                        item.selectedSubtitleIndex = -1;
                    } else {
                        item.selectedSubtitleIndex = item.subtitles.get(which - 1).index;
                        if (video != null) loadSubtitle(video, item);
                    }
                    dialog.dismiss();
                    if (video == null) showDetail(item);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private int selectedSubtitleChoice(MediaItem item) {
        if (item.selectedSubtitleIndex < 0) return 0;
        for (int i = 0; i < item.subtitles.size(); i++) {
            if (item.subtitles.get(i).index == item.selectedSubtitleIndex) return i + 1;
        }
        return 0;
    }

    private void loadSubtitle(VideoView video, MediaItem item) {
        MediaSourceInfo source = item.selectedMediaSource();
        new AsyncTask<Void, Void, Object>() {
            @Override
            protected Object doInBackground(Void... voids) {
                try {
                    return client.openSubtitle(item.id, source == null ? null : source.id, item.selectedSubtitleIndex);
                } catch (Exception e) {
                    return e;
                }
            }

            @Override
            protected void onPostExecute(Object result) {
                if (result instanceof Exception) {
                    toast("字幕加载失败");
                    return;
                }
                try {
                    MediaFormat format = MediaFormat.createSubtitleFormat("application/x-subrip", Locale.getDefault().getLanguage());
                    video.addSubtitleSource((InputStream) result, format);
                    toast("已加载字幕");
                } catch (Exception e) {
                    toast("字幕格式不支持");
                }
            }
        }.execute();
    }

    private void loadImage(String id, ImageView target) {
        new AsyncTask<Void, Void, Bitmap>() {
            @Override
            protected Bitmap doInBackground(Void... voids) {
                try {
                    return client.loadPrimaryImage(id, 360);
                } catch (Exception ignored) {
                    return null;
                }
            }

            @Override
            protected void onPostExecute(Bitmap bitmap) {
                if (bitmap != null) target.setImageBitmap(bitmap);
            }
        }.execute();
    }

    private void goBack() {
        if (currentVideo != null) {
            stopPlaybackAndReport();
            if (currentDetail != null) showDetail(currentDetail);
            else showHome();
            return;
        }
        if (currentDetail != null) {
            currentDetail = null;
            reloadCurrent();
            return;
        }
        if (backStack.size() <= 1) {
            showHome();
            return;
        }
        backStack.remove(backStack.size() - 1);
        reloadCurrent();
    }

    private void reloadCurrent() {
        if (backStack.isEmpty()) {
            showHome();
            return;
        }
        NavEntry entry = backStack.get(backStack.size() - 1);
        loadItems(entry.parentId, entry.title, true);
    }

    private void showLoading(String text) {
        root.removeAllViews();
        LinearLayout page = vertical();
        page.setGravity(Gravity.CENTER);
        page.setBackgroundColor(Color.rgb(14, 18, 24));
        ProgressBar bar = new ProgressBar(this);
        TextView label = label(text);
        label.setTextColor(Color.WHITE);
        page.addView(bar);
        page.addView(label);
        root.addView(page, fill());
    }

    private void showError(String message) {
        toast(message);
        if (client == null) showLogin();
        else reloadCurrent();
    }

    private LinearLayout vertical() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout horizontal() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        return layout;
    }

    private TextView title(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(24);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setTextColor(Color.rgb(25, 31, 40));
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setSingleLine(false);
        return view;
    }

    private TextView label(String text) {
        TextView view = new TextView(this);
        view.setText(text == null ? "" : text);
        view.setTextSize(15);
        view.setTextColor(Color.rgb(86, 96, 110));
        view.setPadding(dp(6), dp(4), dp(6), dp(4));
        return view;
    }

    private TextView labelWhite(String text) {
        TextView view = label(text);
        view.setTextColor(Color.rgb(174, 184, 198));
        return view;
    }

    private TextView sectionTitle(String text) {
        TextView view = title(text);
        view.setTextColor(Color.WHITE);
        view.setTextSize(20);
        view.setPadding(0, dp(18), 0, dp(8));
        return view;
    }

    private TextView settingText(String name, String value) {
        TextView view = label(name + "\n" + value);
        view.setTextColor(Color.rgb(221, 228, 238));
        view.setTextSize(16);
        view.setBackgroundColor(Color.rgb(32, 39, 49));
        view.setPadding(dp(16), dp(12), dp(16), dp(12));
        return view;
    }

    private CheckBox checkBox(String text) {
        CheckBox box = new CheckBox(this);
        box.setText(text);
        box.setTextSize(16);
        box.setTextColor(Color.rgb(221, 228, 238));
        box.setButtonTintList(android.content.res.ColorStateList.valueOf(Color.rgb(126, 211, 166)));
        box.setPadding(dp(12), dp(8), dp(12), dp(8));
        box.setFocusable(true);
        return box;
    }

    private EditText input(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setSingleLine(true);
        input.setTextSize(16);
        return input;
    }

    private Button button(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setFocusable(true);
        button.setMinHeight(dp(44));
        return button;
    }

    private Button navButton(String text) {
        Button button = button(text);
        button.setGravity(Gravity.CENTER_VERTICAL);
        button.setTextSize(17);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(52));
        params.setMargins(0, dp(5), 0, dp(5));
        button.setLayoutParams(params);
        return button;
    }

    private LinearLayout.LayoutParams wideInputParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(Math.min(dp(620), getResources().getDisplayMetrics().widthPixels - dp(96)), dp(58));
        params.setMargins(0, dp(10), 0, 0);
        return params;
    }

    private LinearLayout.LayoutParams wideButtonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(Math.min(dp(620), getResources().getDisplayMetrics().widthPixels - dp(96)), dp(54));
        params.setMargins(0, dp(18), 0, 0);
        return params;
    }

    private LinearLayout.LayoutParams settingsInputParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(58));
        params.setMargins(0, dp(6), 0, dp(6));
        return params;
    }

    private FrameLayout.LayoutParams fill() {
        return new FrameLayout.LayoutParams(-1, -1);
    }

    private void showKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String message) {
        Toast.makeText(this, message == null ? "发生错误" : message, Toast.LENGTH_LONG).show();
    }

    private void startProgressReporter(MediaItem item, TextView progress, SeekBar seekBar) {
        stopProgressReporter();
        progressRunnable = new Runnable() {
            @Override
            public void run() {
                if (currentVideo == null) return;
                long positionTicks = currentVideo.getCurrentPosition() * 10000L;
                long durationTicks = currentVideo.getDuration() > 0 ? currentVideo.getDuration() * 10000L : item.runtimeTicks;
                progress.setText(MediaItem.positionText(positionTicks) + " / " + MediaItem.positionText(durationTicks));
                if (!userSeeking && currentVideo.getDuration() > 0) {
                    seekBar.setProgress((int) ((long) currentVideo.getCurrentPosition() * 1000L / currentVideo.getDuration()));
                }
                item.playbackPositionTicks = positionTicks;
                reportPlaybackProgress(item, !currentVideo.isPlaying());
                progressHandler.postDelayed(this, 1000);
            }
        };
        progressHandler.postDelayed(progressRunnable, 1000);
    }

    private void seekBy(int deltaMs, MediaItem item, TextView progress, SeekBar seekBar) {
        if (currentVideo == null) return;
        int duration = currentVideo.getDuration();
        int target = currentVideo.getCurrentPosition() + deltaMs;
        if (target < 0) target = 0;
        if (duration > 0 && target > duration) target = duration;
        currentVideo.seekTo(target);
        long positionTicks = target * 10000L;
        long durationTicks = duration > 0 ? duration * 10000L : item.runtimeTicks;
        item.playbackPositionTicks = positionTicks;
        progress.setText(MediaItem.positionText(positionTicks) + " / " + MediaItem.positionText(durationTicks));
        if (duration > 0) seekBar.setProgress((int) ((long) target * 1000L / duration));
        reportPlaybackProgress(item, !currentVideo.isPlaying());
    }

    private void stopProgressReporter() {
        if (progressRunnable != null) {
            progressHandler.removeCallbacks(progressRunnable);
            progressRunnable = null;
        }
    }

    private void reportPlaybackStart(MediaItem item) {
        reportPlayback(item, "start", item.playbackPositionTicks, false);
    }

    private void reportPlaybackProgress(MediaItem item, boolean paused) {
        if (currentVideo == null) return;
        reportPlayback(item, "progress", currentVideo.getCurrentPosition() * 10000L, paused);
    }

    private void stopPlaybackAndReport() {
        if (currentVideo == null) return;
        long positionTicks = currentVideo.getCurrentPosition() * 10000L;
        MediaItem item = currentDetail;
        stopProgressReporter();
        currentVideo.stopPlayback();
        currentVideo = null;
        if (item != null) {
            item.playbackPositionTicks = positionTicks;
            reportPlayback(item, "stop", positionTicks, true);
        }
    }

    private void reportPlayback(MediaItem item, String event, long positionTicks, boolean paused) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                try {
                    if ("start".equals(event)) client.reportPlaybackStart(item.id, positionTicks);
                    else if ("stop".equals(event)) client.reportPlaybackStopped(item.id, positionTicks);
                    else client.reportPlaybackProgress(item.id, positionTicks, paused);
                } catch (Exception ignored) {
                    // 进度同步失败不应该打断本地播放。
                }
                return null;
            }
        }.execute();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (currentVideo != null && currentVideo.isPlaying()) currentVideo.pause();
        if (currentDetail != null && currentVideo != null) reportPlaybackProgress(currentDetail, true);
    }

    @Override
    protected void onDestroy() {
        stopPlaybackAndReport();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        goBack();
    }

    private static final class NavEntry {
        final String parentId;
        final String title;

        NavEntry(String parentId, String title) {
            this.parentId = parentId;
            this.title = title;
        }
    }

    private static final class LoadResult {
        final List<MediaItem> items;
        final Exception error;

        private LoadResult(List<MediaItem> items, Exception error) {
            this.items = items;
            this.error = error;
        }

        static LoadResult success(List<MediaItem> items) {
            return new LoadResult(items, null);
        }

        static LoadResult failure(Exception error) {
            return new LoadResult(null, error);
        }
    }

    private static final class HomeResult {
        List<MediaItem> resume = new ArrayList<>();
        List<MediaItem> latest = new ArrayList<>();
        List<MediaItem> libraries = new ArrayList<>();
        Exception error;

        static HomeResult failure(Exception error) {
            HomeResult result = new HomeResult();
            result.error = error;
            return result;
        }
    }
}
