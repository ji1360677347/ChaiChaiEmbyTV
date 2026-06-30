package com.chaichai.questnative;

final class MediaSourceInfo {
    String id;
    String name;
    String container;
    String videoCodec;
    String audioCodec;
    String resolution;
    String path;
    long bitrate;
    long size;

    String label() {
        StringBuilder text = new StringBuilder();
        if (name != null && !name.isEmpty()) text.append(name);
        else text.append("资源版本");
        if (resolution != null && !resolution.isEmpty()) text.append(" · ").append(resolution);
        if (videoCodec != null && !videoCodec.isEmpty()) text.append(" · ").append(videoCodec.toUpperCase());
        if (audioCodec != null && !audioCodec.isEmpty()) text.append(" · ").append(audioCodec.toUpperCase());
        if (bitrate > 0) text.append(" · ").append(bitrate / 1000).append("kbps");
        return text.toString();
    }

    String subLabel() {
        StringBuilder text = new StringBuilder();
        if (container != null && !container.isEmpty()) text.append(container.toUpperCase());
        if (size > 0) {
            if (text.length() > 0) text.append(" · ");
            text.append(size / 1024 / 1024).append("MB");
        }
        return text.toString();
    }
}
