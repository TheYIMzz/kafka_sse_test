package com.example.project.service;

import com.example.project.manager.SseEmitterManager;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class KafkaSseListener {
    /*
        KafkaSseListener 동작(메시지 구독 & 자동 호출)

        @KafkaListener(topics="my-topic")가 붙은 KafkaSseListener 클래스는
        Spring Kafka가 내부적으로 생성한 컨슈머(consumer, 메시지 구독자) 스레드에서
        토픽(topic, 메시지 스트림 단위)에 새 레코드(record)가 올라오면 자동으로 onMessage(String message) 메서드를 호출한다
        별도의 서비스 호출 없이도 브로커에 메시지가 적재되자마자 이 메서드가 실행되고
        manager.publishEvent(message)를 통해 연결된 모든 SSE 클라이언트(SseEmitter)에 즉시 푸시된다.
    */
    private final SseEmitterManager manager;

    // Kafka "my-topic" 에 도착하는 메시지를 비동기로 수신
    public KafkaSseListener(SseEmitterManager manager) {
        this.manager = manager;
    }

    // 수신된 문자열 메시지를 SSE 매니저에 전달 → 실시간 푸시
    @KafkaListener(topics = "my-topic", containerFactory = "kafkaListenerContainerFactory")
    // topic 메시지는 "orgId:data" 형태로 전달
    public void onMessage(String message) {
        String[] parts = message.split(":", 2);
        String orgId = parts[0];
        String data = parts.length > 1 ? parts[1] : "";
        manager.publishEvent(orgId, data); // orgId별로 SSE 푸시
    }
}
