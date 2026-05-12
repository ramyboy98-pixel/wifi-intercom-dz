package com.idaradz.wifiintercom;

import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

public class JitterBuffer {
    private final LinkedBlockingDeque<byte[]> queue = new LinkedBlockingDeque<>();
    private final int prebufferFrames;
    private final int maxFrames;

    public JitterBuffer(int prebufferFrames, int maxFrames) {
        this.prebufferFrames = prebufferFrames;
        this.maxFrames = maxFrames;
    }

    public void clear() {
        queue.clear();
    }

    public void push(byte[] pcm) {
        if (pcm == null || pcm.length == 0) return;
        while (queue.size() >= maxFrames) {
            queue.pollFirst();
        }
        queue.offerLast(pcm);
    }

    public byte[] takeForPlayback() throws InterruptedException {
        while (queue.size() < prebufferFrames) {
            byte[] first = queue.poll(80, TimeUnit.MILLISECONDS);
            if (first != null) {
                queue.offerFirst(first);
            }
        }
        byte[] data = queue.poll(120, TimeUnit.MILLISECONDS);
        if (data == null) {
            return new byte[AudioConfig.FRAME_BYTES];
        }
        return data;
    }
}
