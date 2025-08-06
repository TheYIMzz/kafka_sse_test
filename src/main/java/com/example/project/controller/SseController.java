package com.example.project.controller;

import com.example.project.dto.Event;
import com.example.project.manager.SseEmitterManager;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.util.Properties;
import java.util.UUID;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.Duration;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.clients.consumer.OffsetAndTimestamp;
import org.apache.kafka.common.serialization.StringDeserializer;





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
//    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
//    public SseEmitter stream(
//            @RequestParam String orgId,
//            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId
//    ) throws IOException {
//        // 1) 새로운 emitter 생성 및 등록
//        SseEmitter emitter = manager.createEmitter(orgId);
//
//        // 2) 놓친 이벤트 replay
//        List<Event> missed = manager.findEventsAfter(orgId, lastEventId);
//        for (Event ev : missed) {
//            emitter.send(
//                    SseEmitter.event()
//                            .id(ev.getId())
//                            .name("kafka-event")
//                            .data(ev.getData())
//            );
//        }
//
//        // 3) 실시간 publishEvent도 자동 전송
//        return emitter;
//    }

    // 메시지 카프카 적재 버전
    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @RequestParam String orgId,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId
    ) throws IOException {
        // 1) SseEmitter 생성 및 등록
        SseEmitter emitter = manager.createEmitter(orgId);  // emitter -> SSE 세션 객체

        // === CHANGED: In-memory 버퍼 대신 Kafka 토픽에서 오늘치 메시지 replay ===
        // (1) KafkaConsumer 설정
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:29092");
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "sse-replay-" + UUID.randomUUID());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            // (2) 토픽 파티션 할당
            TopicPartition tp = new TopicPartition("my-topic", 0);
            consumer.assign(Collections.singletonList(tp));

            // (3) 오늘 자정(epoch ms) 기준 오프셋 계산
            Instant midnight = LocalDate.now(ZoneId.of("Asia/Seoul"))
                    .atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant();
            Map<TopicPartition, Long> times = Collections.singletonMap(tp, midnight.toEpochMilli());
            OffsetAndTimestamp ot = consumer.offsetsForTimes(times).get(tp);
            if (ot != null) {
                consumer.seek(tp, ot.offset());
            } else {
                consumer.seekToBeginning(Collections.singletonList(tp));
            }

            // (4) 백로그 Poll & replay
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
            for (ConsumerRecord<String, String> rec : records) {
                // 메시지 값을 "orgId:data" 형태로 보내고 있다면
                String[] parts = rec.value().split(":", 2);
                String recOrgId = parts[0];
                String recData  = parts.length > 1 ? parts[1] : "";

                // 요청 파라미터 orgId와 일치하는 메시지만 전송
                if (orgId.equals(recOrgId)) {
                    emitter.send(SseEmitter.event()
                            .id(String.valueOf(rec.offset()))
                            .name("kafka-event")
                            .data(recData));
                }
            }
        }
        // === CHANGED END ===
        // 2) 이후 publishEvent() 호출로 실시간 이벤트 푸시 계속
        return emitter;
    }



    // 2) 테스트용 메시지 발행. Kafka로 보내면 Listener가 받아 SSE로 푸시
    @PostMapping("/publish")
    public ResponseEntity<Void> publish(@RequestParam String msg) {
        kafkaTemplate.send("my-topic", msg); // Kafka에 메시지 발행
        return ResponseEntity.accepted().build();
    }

    /**
     * orgId별 연결 수 확인 엔드포인트
     */
    @GetMapping("/connections")
    public ResponseEntity<Map<String, Integer>> connections(
            @RequestParam(required = false) String orgId) {
        if (orgId != null) {
            int count = manager.getConnectionCount(orgId);
            return ResponseEntity.ok(Collections.singletonMap(orgId, count));
        } else {
            Map<String, Integer> result = new HashMap<String, Integer>();
            for (String id : manager.getOrgIds()) {
                result.put(id, manager.getConnectionCount(id));
            }
            result.put("total", manager.getTotalConnectionCount());
            return ResponseEntity.ok(result);
        }
    }
}