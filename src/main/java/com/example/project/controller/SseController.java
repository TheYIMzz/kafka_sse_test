package com.example.project.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
@RestController
public class SseController {

    private ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>> emitters =
            new ConcurrentHashMap<>();

    @GetMapping("/connect/{userId}")
    public SseEmitter connect(@PathVariable String userId) {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>())
                .add(emitter);
        emitter.onCompletion(() -> emitters.get(userId).remove(emitter));
        return emitter;
    }

    public void sendEvent(String userId, String data) throws IOException {
        List<SseEmitter> list = emitters.get(userId);
        if (list != null) {
            for (SseEmitter e : list) {
                e.send(data);

            }
        }
    }
}
