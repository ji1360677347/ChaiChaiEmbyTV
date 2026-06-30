package com.chaichai.questnative;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
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
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final String PREFS = "quest_native";
    private static final String KEY_SERVER = "server";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_DANMAKU_API = "danmaku_api";

    private FrameLayout root;
    private EmbyClient client;
    private final ArrayList<NavEntry> backStack = new ArrayList<>();
    private MediaItem currentDetail;
    private VideoView currentVideo;
    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private Runnable progressRunnable;

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
        loadItems(null, "媒体库", true);
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

    private void showDetail(MediaItem item) {
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
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.setPadding(dp(48), dp(42), dp(48), dp(24));
        page.setBackgroundColor(Color.rgb(245, 247, 250));
        root.addView(page, fill());

        TextView title = title("设置");
        page.addView(title);
        EditText danmaku = input("弹幕接口");
        danmaku.setText(prefs().getString(KEY_DANMAKU_API, AppConfig.DEFAULT_DANMAKU_API));
        page.addView(danmaku, wideInputParams());

        LinearLayout actions = horizontal();
        actions.setGravity(Gravity.CENTER);
        Button save = button("保存");
        Button back = button("返回");
        actions.addView(save, new LinearLayout.LayoutParams(dp(160), dp(54)));
        actions.addView(back, new LinearLayout.LayoutParams(dp(160), dp(54)));
        page.addView(actions);

        save.setOnClickListener(v -> {
            String value = danmaku.getText().toString().trim();
            if (value.isEmpty()) value = AppConfig.DEFAULT_DANMAKU_API;
            prefs().edit().putString(KEY_DANMAKU_API, value).apply();
            toast("已保存");
            goBack();
        });
        back.setOnClickListener(v -> goBack());
        danmaku.requestFocus();
        danmaku.postDelayed(() -> showKeyboard(danmaku), 250);
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

        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        Button back = button("返回");
        Button pause = button("暂停");
        row.addView(back, new LinearLayout.LayoutParams(dp(140), dp(54)));
        row.addView(pause, new LinearLayout.LayoutParams(dp(140), dp(54)));
        controls.addView(row);

        back.setOnClickListener(v -> {
            stopPlaybackAndReport();
            showDetail(item);
        });
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
        video.setVideoURI(Uri.parse(client.streamUrl(item.id)));
        video.setOnPreparedListener(mp -> {
            mp.setLooping(false);
            if (item.playbackPositionTicks > 0 && !item.played) {
                int positionMs = (int) Math.min(Integer.MAX_VALUE, item.playbackPositionTicks / 10000L);
                video.seekTo(positionMs);
            }
            video.start();
            reportPlaybackStart(item);
            startProgressReporter(item, progress);
        });
        video.setOnErrorListener((mp, what, extra) -> {
            toast("播放失败: " + what);
            return true;
        });
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

    private void startProgressReporter(MediaItem item, TextView progress) {
        stopProgressReporter();
        progressRunnable = new Runnable() {
            @Override
            public void run() {
                if (currentVideo == null) return;
                long positionTicks = currentVideo.getCurrentPosition() * 10000L;
                long durationTicks = currentVideo.getDuration() > 0 ? currentVideo.getDuration() * 10000L : item.runtimeTicks;
                progress.setText(MediaItem.positionText(positionTicks) + " / " + MediaItem.positionText(durationTicks));
                item.playbackPositionTicks = positionTicks;
                reportPlaybackProgress(item, !currentVideo.isPlaying());
                progressHandler.postDelayed(this, 10000);
            }
        };
        progressHandler.postDelayed(progressRunnable, 1000);
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
}
