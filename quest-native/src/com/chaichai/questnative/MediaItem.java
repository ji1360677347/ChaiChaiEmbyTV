package com.chaichai.questnative;

final class MediaItem {
    String id;
    String name;
    String type;
    String mediaType;
    String overview;
    String genres;
    String seriesName;
    int year;
    int seasonNumber;
    int episodeNumber;
    long runtimeTicks;
    long playbackPositionTicks;
    boolean hasPrimaryImage;
    boolean played;
    boolean detailsLoaded;
    String selectedMediaSourceId;
    int selectedSubtitleIndex = -1;
    final java.util.ArrayList<MediaSourceInfo> mediaSources = new java.util.ArrayList<>();
    final java.util.ArrayList<SubtitleTrack> subtitles = new java.util.ArrayList<>();

    boolean isFolderLike() {
        return "CollectionFolder".equals(type) || "Folder".equals(type) || "Series".equals(type) || "Season".equals(type);
    }

    boolean isPlayable() {
        return "Video".equals(mediaType) || "Movie".equals(type) || "Episode".equals(type);
    }

    String displayName() {
        if ("Episode".equals(type) && episodeNumber > 0) {
            String prefix = seasonNumber > 0 ? "S" + twoDigits(seasonNumber) + "E" + twoDigits(episodeNumber) : "E" + twoDigits(episodeNumber);
            return prefix + " " + name;
        }
        return name;
    }

    String subtitle() {
        StringBuilder text = new StringBuilder();
        if (played) text.append("已看");
        else if (playbackPositionTicks > 0) text.append("看到 " + positionText(playbackPositionTicks));
        if (year > 0) {
            if (text.length() > 0) text.append(" · ");
            text.append(year);
        }
        String runtime = runtimeText();
        if (!runtime.isEmpty()) {
            if (text.length() > 0) text.append(" · ");
            text.append(runtime);
        }
        if (genres != null && !genres.isEmpty()) {
            if (text.length() > 0) text.append(" · ");
            text.append(genres);
        }
        return text.toString();
    }

    String runtimeText() {
        if (runtimeTicks <= 0) return "";
        long minutes = runtimeTicks / 600000000L;
        if (minutes <= 0) return "";
        long hours = minutes / 60;
        long rest = minutes % 60;
        if (hours > 0) return hours + "小时" + rest + "分钟";
        return rest + "分钟";
    }

    String progressText() {
        if (played) return "已看完";
        if (playbackPositionTicks <= 0) return "";
        return "上次看到 " + positionText(playbackPositionTicks);
    }

    MediaSourceInfo selectedMediaSource() {
        if (mediaSources.isEmpty()) return null;
        if (selectedMediaSourceId != null) {
            for (MediaSourceInfo source : mediaSources) {
                if (selectedMediaSourceId.equals(source.id)) return source;
            }
        }
        return mediaSources.get(0);
    }

    String selectedMediaSourceLabel() {
        MediaSourceInfo source = selectedMediaSource();
        return source == null ? "默认资源" : source.label();
    }

    String selectedSubtitleLabel() {
        if (selectedSubtitleIndex < 0) return "禁用字幕";
        for (SubtitleTrack track : subtitles) {
            if (track.index == selectedSubtitleIndex) return track.label();
        }
        return "禁用字幕";
    }

    static String positionText(long ticks) {
        if (ticks <= 0) return "00:00";
        long seconds = ticks / 10000000L;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long rest = seconds % 60;
        if (hours > 0) return hours + ":" + twoDigits(minutes) + ":" + twoDigits(rest);
        return twoDigits(minutes) + ":" + twoDigits(rest);
    }

    private static String twoDigits(long value) {
        return value < 10 ? "0" + value : String.valueOf(value);
    }
}
