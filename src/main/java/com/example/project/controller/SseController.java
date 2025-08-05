package com.example.project.controller;

import com.example.project.dto.Event;
import com.example.project.manager.SseEmitterManager;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;


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

    /**
     * SSE 스트림 엔드포인트
     * - Last-Event-ID 헤더로 놓친 이벤트 replay
     */
    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream( @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) throws IOException, IOException {
        SseEmitter emitter = manager.createEmitter();
        // replay missed events
        List<Event> missed = manager.findEventsAfter(lastEventId);
        for (Event ev : missed) {
            emitter.send(
                    SseEmitter.event()
                            .id(ev.getId())
                            .name("kafka-event")
                            .data(ev.getData())
            );
        }
        return emitter;
    }

    // 2) 테스트용 메시지 발행. Kafka로 보내면 Listener가 받아 SSE로 푸시
    @PostMapping("/publish")
    public ResponseEntity<Void> publish(@RequestParam String msg) {
        kafkaTemplate.send("my-topic", msg);
        return ResponseEntity.accepted().build();
    }
}