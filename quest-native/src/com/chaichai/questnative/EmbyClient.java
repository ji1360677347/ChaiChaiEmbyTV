package com.chaichai.questnative;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class EmbyClient {
    String server;
    String token;
    String userId;

    EmbyClient(String server, String token, String userId) {
        this.server = stripTrailingSlash(server);
        this.token = token;
        this.userId = userId;
    }

    static String normalizeServer(String raw) {
        String value = raw.trim();
        if (value.isEmpty()) return "";
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            value = "http://" + value;
        }
        return stripTrailingSlash(value);
    }

    static String stripTrailingSlash(String value) {
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }

    static EmbyClient login(String server, String username, String password) throws Exception {
        String base = normalizeServer(server);
        JSONObject body = new JSONObject();
        body.put("Username", username);
        body.put("Pw", password);
        JSONObject result = requestJson("POST", base + "/Users/AuthenticateByName", body.toString(), null);
        String token = result.getString("AccessToken");
        String userId = result.getJSONObject("User").getString("Id");
        return new EmbyClient(base, token, userId);
    }

    List<MediaItem> getLibraries() throws Exception {
        JSONObject json = getJson("/Users/" + userId + "/Views");
        return parseItems(json.getJSONArray("Items"));
    }

    List<MediaItem> getItems(String parentId) throws Exception {
        String path = "/Users/" + userId + "/Items?ParentId=" + Uri.encode(parentId)
                + commonFields()
                + "&SortBy=SortName&SortOrder=Ascending";
        JSONObject json = getJson(path);
        return parseItems(json.getJSONArray("Items"));
    }

    MediaItem getItem(String id) throws Exception {
        JSONObject json = getJson("/Users/" + userId + "/Items/" + Uri.encode(id) + "?" + commonFields().substring(1));
        return parseItem(json);
    }

    List<MediaItem> search(String query) throws Exception {
        String path = "/Users/" + userId + "/Items?Recursive=true"
                + "&SearchTerm=" + Uri.encode(query)
                + "&IncludeItemTypes=Movie,Series,Episode,Video,Folder"
                + commonFields()
                + "&Limit=80";
        JSONObject json = getJson(path);
        return parseItems(json.getJSONArray("Items"));
    }

    String streamUrl(String id) {
        return server + "/Videos/" + Uri.encode(id) + "/stream?Static=true&api_key=" + Uri.encode(token);
    }

    void reportPlaybackStart(String itemId, long positionTicks) throws Exception {
        JSONObject body = playbackBody(itemId, positionTicks, false);
        postJson("/Sessions/Playing", body);
    }

    void reportPlaybackProgress(String itemId, long positionTicks, boolean paused) throws Exception {
        JSONObject body = playbackBody(itemId, positionTicks, paused);
        postJson("/Sessions/Playing/Progress", body);
    }

    void reportPlaybackStopped(String itemId, long positionTicks) throws Exception {
        JSONObject body = playbackBody(itemId, positionTicks, true);
        postJson("/Sessions/Playing/Stopped", body);
    }

    Bitmap loadPrimaryImage(String id, int width) throws Exception {
        String url = server + "/Items/" + Uri.encode(id) + "/Images/Primary?maxWidth=" + width
                + "&quality=85&api_key=" + Uri.encode(token);
        byte[] data = requestBytes(url);
        return BitmapFactory.decodeByteArray(data, 0, data.length);
    }

    private String commonFields() {
        return "&Fields=Overview,Genres,PrimaryImageAspectRatio,ProductionYear,RunTimeTicks,UserData,SeriesName,ParentIndexNumber,IndexNumber";
    }

    private JSONObject getJson(String path) throws Exception {
        String separator = path.contains("?") ? "&" : "?";
        return requestJson("GET", server + path + separator + "api_key=" + Uri.encode(token), null, token);
    }

    private void postJson(String path, JSONObject body) throws Exception {
        String separator = path.contains("?") ? "&" : "?";
        request("POST", server + path + separator + "api_key=" + Uri.encode(token), body.toString(), token);
    }

    private JSONObject playbackBody(String itemId, long positionTicks, boolean paused) throws Exception {
        JSONObject body = new JSONObject();
        body.put("ItemId", itemId);
        body.put("PositionTicks", Math.max(0, positionTicks));
        body.put("IsPaused", paused);
        body.put("PlayMethod", "DirectPlay");
        body.put("CanSeek", true);
        return body;
    }

    private static JSONObject requestJson(String method, String url, String body, String token) throws Exception {
        String text = request(method, url, body, token);
        if (text.isEmpty()) return new JSONObject();
        return new JSONObject(text);
    }

    private static String request(String method, String url, String body, String token) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("X-Emby-Authorization",
                "MediaBrowser Client=\"ChaiChai Quest Native\", Device=\"Quest 3\", DeviceId=\"quest-native\", Version=\"1.0\"");
        if (token != null && !token.isEmpty()) conn.setRequestProperty("X-Emby-Token", token);
        if (body != null) {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setFixedLengthStreamingMode(bytes.length);
            OutputStream out = conn.getOutputStream();
            out.write(bytes);
            out.close();
        }
        int code = conn.getResponseCode();
        InputStream input = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
        String text = readText(input);
        if (code < 200 || code >= 300) {
            throw new RuntimeException("HTTP " + code + ": " + text);
        }
        return text;
    }

    private static byte[] requestBytes(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) throw new RuntimeException("HTTP " + code);
        InputStream input = conn.getInputStream();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = input.read(buf)) != -1) out.write(buf, 0, n);
        input.close();
        return out.toByteArray();
    }

    private static String readText(InputStream input) throws Exception {
        if (input == null) return "";
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        reader.close();
        return sb.toString();
    }

    private static List<MediaItem> parseItems(JSONArray array) throws Exception {
        ArrayList<MediaItem> items = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            items.add(parseItem(array.getJSONObject(i)));
        }
        return items;
    }

    private static MediaItem parseItem(JSONObject obj) throws Exception {
        MediaItem item = new MediaItem();
        item.id = obj.optString("Id");
        item.name = obj.optString("Name");
        item.type = obj.optString("Type");
        item.mediaType = obj.optString("MediaType");
        item.overview = obj.optString("Overview");
        item.seriesName = obj.optString("SeriesName");
        item.year = obj.optInt("ProductionYear", 0);
        item.seasonNumber = obj.optInt("ParentIndexNumber", 0);
        item.episodeNumber = obj.optInt("IndexNumber", 0);
        item.runtimeTicks = obj.optLong("RunTimeTicks", 0);
        item.hasPrimaryImage = obj.has("ImageTags") && obj.getJSONObject("ImageTags").has("Primary");
        JSONObject userData = obj.optJSONObject("UserData");
        if (userData != null) {
            item.played = userData.optBoolean("Played", false);
            item.playbackPositionTicks = userData.optLong("PlaybackPositionTicks", 0);
        }
        JSONArray genres = obj.optJSONArray("Genres");
        if (genres != null) {
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < genres.length(); i++) {
                if (i > 0) text.append(" / ");
                text.append(genres.optString(i));
            }
            item.genres = text.toString();
        }
        return item;
    }
}
