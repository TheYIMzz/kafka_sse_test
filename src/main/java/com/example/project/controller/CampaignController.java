package com.example.project.controller;

import com.example.project.domain.Campaign;
import com.example.project.mapper.CampaignMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/campaign")
public class CampaignController {
    private final CampaignMapper mapper;
    private final KafkaTemplate<String,String> kafka;

    public CampaignController(CampaignMapper mapper,
                              KafkaTemplate<String,String> kafka) {
        this.mapper = mapper;
        this.kafka = kafka;
    }

    @PostMapping("/complete/{id}")
    public ResponseEntity<Void> complete(@PathVariable Long id) {
        int updated = mapper.updateStatus(id, "Y");
        if (updated == 0) return ResponseEntity.notFound().build();

        // 사용자 ID 조회
        Campaign c = mapper.selectByStatus("Y")
                .stream()
                .filter(x -> x.getId().equals(id))
                .findFirst()
                .orElse(null);
        if (c != null) {
            kafka.send("campaign-complete", id + ":" + c.getUserId());
        }
        return ResponseEntity.ok().build();
    }
}