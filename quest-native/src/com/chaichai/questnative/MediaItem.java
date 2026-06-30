package com.chaichai.questnative;

final class MediaItem {
    String id;
    String name;
    String type;
    String mediaType;
    String overview;
    String genres;
    int year;
    long runtimeTicks;
    boolean hasPrimaryImage;

    boolean isFolderLike() {
        return "CollectionFolder".equals(type) || "Folder".equals(type) || "Series".equals(type) || "Season".equals(type);
    }

    boolean isPlayable() {
        return "Video".equals(mediaType) || "Movie".equals(type) || "Episode".equals(type);
    }

    String subtitle() {
        StringBuilder text = new StringBuilder();
        if (year > 0) text.append(year);
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
}
