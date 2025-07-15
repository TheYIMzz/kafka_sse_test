package com.example.project.service;

import com.example.project.controller.SseController;
import com.example.project.mapper.CampaignMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class ConsumerService {
    private final SseController sse;
    private final CampaignMapper mapper;


    public ConsumerService(SseController sse, CampaignMapper mapper) {
        this.sse = sse;
        this.mapper = mapper;
    }

    @KafkaListener(topics = "campaign-complete")
    public void onComplete(String msg) throws IOException {
        String[] parts = msg.split(":");
        String campaignId = parts[0];
        String userId     = parts[1];
        sse.sendEvent(userId, "completed:" + campaignId);

    }

    // 신규 발행(N) 캠페인 리스너
    @KafkaListener(topics = "campaign-new")
    public void onNew(String msg) throws IOException {
        // msg = "id:userId"
        String[] parts = msg.split(":");
        String id     = parts[0];
        String userId = parts[1];
        // 클라이언트로는 "new:id" 형태로 전송
        sse.sendEvent(userId, "new:" + id);
    }

}