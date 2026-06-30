package com.chaichai.questnative;

final class SubtitleTrack {
    int index;
    String title;
    String language;
    String codec;
    boolean external;

    String label() {
        StringBuilder text = new StringBuilder();
        if (title != null && !title.isEmpty()) text.append(title);
        else if (language != null && !language.isEmpty()) text.append(language);
        else text.append("字幕 ").append(index);
        if (codec != null && !codec.isEmpty()) text.append(" · ").append(codec.toUpperCase());
        if (external) text.append(" · 同目录");
        return text.toString();
    }
}
