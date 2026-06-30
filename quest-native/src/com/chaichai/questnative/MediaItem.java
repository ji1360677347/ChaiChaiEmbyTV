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
