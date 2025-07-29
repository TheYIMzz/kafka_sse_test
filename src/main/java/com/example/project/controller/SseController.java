package com.example.project.controller;

import com.example.project.manager.SseEmitterManager;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;


@RestController
@RequestMapping("/api")
public class SseController {

    private final SseEmitterManager manager;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public SseController(SseEmitterManager manager,
                         KafkaTemplate<String, String> kafkaTemplate) {
        this.manager = manager;
        this.kafkaTemplate = kafkaTemplate;
    }

    // 1) 클라이언트가 호출하면 SSE 연결(“롱‑라이브(long‑lived, 장시간 유지) HTTP 스트림”) 생성
    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return manager.createEmitter();
    }

    // 2) 테스트용 메시지 발행. Kafka로 보내면 Listener가 받아 SSE로 푸시
    @PostMapping("/publish")
    public ResponseEntity<Void> publish(@RequestParam String msg) {
        kafkaTemplate.send("my-topic", msg);
        return ResponseEntity.accepted().build();
    }
}