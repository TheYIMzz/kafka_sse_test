package com.example.project.service;

import com.example.project.domain.Campaign;
import com.example.project.mapper.CampaignMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProducerService {
    private final CampaignMapper mapper;
    private final KafkaTemplate<String,String> kafka;

    public ProducerService(CampaignMapper mapper,
                           KafkaTemplate<String,String> kafka) {
        this.mapper = mapper;
        this.kafka = kafka;
    }

    @Scheduled(fixedDelay = 60000)
    public void publishNew() {
        List<Campaign> list = mapper.selectByStatus("N");
        System.out.println("publishNew 실행: 조회된 N 상태 캠페인 수 = " + list.size());

        for (Campaign c : list) {
            String msg = c.getId() + ":" + c.getUserId();
            kafka.send("campaign-new", msg);
        }
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 10_000)
    @Transactional
    public void publishComplete() {
        List<Campaign> list = mapper.selectByStatusForUpdate("Y");
        for (Campaign c : list) {
            String payload = c.getId() + "," + c.getStatus();
            kafka.send("campaign-complete", payload);
        }
    }


}