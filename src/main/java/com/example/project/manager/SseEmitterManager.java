package com.example.project.manager;

import com.example.project.dto.Event;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class SseEmitterManager {
    // 조직별 연결 목록: orgId -> List<SseEmitter>
    private final Map<String, CopyOnWriteArrayList<SseEmitter>> emittersByOrg = new ConcurrentHashMap<>();

    /**
     * 이벤트 버퍼(최근 MAX_BUFFER_SIZE건 보관)
     */
    // 조직별 버퍼: orgId -> Deque<Event>
    private final Map<String, Deque<Event>> bufferByOrg = new ConcurrentHashMap<>();
    private final AtomicLong idSeq = new AtomicLong(0);
    private static final int MAX_BUFFER_SIZE = 100;


    public SseEmitter createEmitter(String orgId) {
        // 1) SseEmitter 객체 생성 (브라우저 별 개별 생성)
        //    SseEmitter(서버‑센트 이벤트(Server‑Sent Events, 서버→클라이언트 단방향 푸시) 연결 관리 객체)
        //    파라미터 0L = 무제한 타임아웃(타임아웃 없음)
        final SseEmitter emitter = new SseEmitter(0L);

        // 2) 동시성 안전 리스트(CopyOnWriteArrayList)에 등록
        if (!emittersByOrg.containsKey(orgId)) {
            emittersByOrg.put(orgId, new CopyOnWriteArrayList<SseEmitter>());
        }
        emittersByOrg.get(orgId).add(emitter); // 이후 publishEvent() 호출 시 해당 리스트를 순회하며 클라이언트에 푸시

        // 3) onCompletion(연결 정상 종료 시) 콜백 등록
        //    클라이언트가 스트림을 닫거나 에러 없이 완료되면 호출되어 리스트에서 제거
        emitter.onCompletion(new Runnable() {
            @Override
            public void run() {
                removeEmitter(orgId, emitter);
            }
        });

        // 4) onTimeout(설정된 시간 내 클라이언트 응답 없을 때) 콜백 등록
        //    타임아웃 발생 시에도 동일하게 리스트에서 제거
        emitter.onTimeout(new Runnable() {
            @Override
            public void run() {
                removeEmitter(orgId, emitter);
            }
        });

        // 5) 생성된 SseEmitter 반환
        //    이 객체가 컨트롤러에서 클라이언트에게 리턴되어
        //    HTTP 응답 스트림이 “롱‑라이브(long‑lived, 장시간 유지) HTTP 연결”로 유지됨
        return emitter;
    }

    private void removeEmitter(String orgId, SseEmitter emitter) {
        List<SseEmitter> list = emittersByOrg.get(orgId);
        if (list != null) {
            list.remove(emitter);
        }
    }

    /**
     * 데이터 발행: 조직 코드(orgId)와 payload를 받아
     * 조직별 버퍼에 저장 후 해당 조직 구독자에게만 전송
     * 1) 이벤트 ID 생성 및 orgId 버퍼에 추가
     * 2) 버퍼 크기 초과 시 오래된 이벤트 삭제
     * 3) 해당 orgId의 모든 Emitter에 전송 시도
     * 4) 전송 중 IOException 발생 시 dead 리스트에 추가
     * 5) dead 리스트에 모인 emitter 제거
     */
    public void publishEvent(String orgId, String data) {
        // 1) ID 생성 및 Event 생성
        String id = String.valueOf(idSeq.incrementAndGet());
        Event ev = new Event(id, orgId, data);

        // 2) 버퍼에 추가
        bufferByOrg.computeIfAbsent(orgId, k -> new ArrayDeque<Event>());
        Deque<Event> buffer = bufferByOrg.get(orgId);
        synchronized (buffer) {
            buffer.addLast(ev);
            if (buffer.size() > MAX_BUFFER_SIZE) {
                buffer.removeFirst(); // 오래된 이벤트 제거
            }
        }

        // 3) 해당 orgId의 emitter 리스트 조회
        List<SseEmitter> list = emittersByOrg.get(orgId);
        if (list == null) {
            return; // 구독자가 없는 경우 종료
        }

        List<SseEmitter> dead = new ArrayList<SseEmitter>(); // 4)
        for (SseEmitter emitter : list) {
            try {
                // 5) SSE 이벤트 전송
                emitter.send(
                        SseEmitter.event()
                                .id(ev.getId())         // 이벤트 ID 설정
                                .name("kafka-event") // 커스텀 이벤트 이름
                                .data(ev.getData())      // 데이터 페이로드
                );
            } catch (IOException ex) {
                dead.add(emitter); // 전송 오류 시 dead에 기록 (4)
            }
        }

        // 6) 실패한 emitter 제거
        for (SseEmitter e : dead) {
            removeEmitter(orgId, e);
        }
    }

    /**
     * 놓친 이벤트 replay용
     */
    public List<Event> findEventsAfter(String orgId, String lastEventId) {
        Deque<Event> buffer = bufferByOrg.get(orgId);
        List<Event> list = new ArrayList<Event>();
        if (buffer != null) {
            synchronized (buffer) {
                for (Event ev : buffer) {
                    if (lastEventId == null || ev.getId().compareTo(lastEventId) > 0) {
                        list.add(ev);
                    }
                }
            }
        }
        return list;
    }


}
