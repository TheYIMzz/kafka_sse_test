package com.example.project.manager;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class SseEmitterManager {
    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter createEmitter() {
        final SseEmitter emitter = new SseEmitter(0L);  // 무제한 타임아웃
        emitters.add(emitter);

        emitter.onCompletion(new Runnable() {
            @Override public void run() { emitters.remove(emitter); }
        });
        emitter.onTimeout(   new Runnable() {
            @Override public void run() { emitters.remove(emitter); }
        });

        return emitter;
    }

    public void publishEvent(final String data) {
        List<SseEmitter> dead = new ArrayList<SseEmitter>();
        for (final SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("kafka-event")
                        .data(data));
            } catch (Exception ex) {
                dead.add(emitter);
            }
        }
        emitters.removeAll(dead);
    }
}
