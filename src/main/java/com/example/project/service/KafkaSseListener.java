package com.example.project.service;

import com.example.project.manager.SseEmitterManager;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class KafkaSseListener {
    private final SseEmitterManager manager;

    // Kafka "my-topic" 에 도착하는 메시지를 비동기로 수신
    public KafkaSseListener(SseEmitterManager manager) {
        this.manager = manager;
    }

    // 수신된 문자열 메시지를 SSE 매니저에 전달 → 실시간 푸시
    @KafkaListener(topics = "my-topic", containerFactory = "kafkaListenerContainerFactory")
    public void onMessage(String message) {
        manager.publishEvent(message); // Kafka 메시지를 받으면 즉시 SSE로 푸시
    }
}
