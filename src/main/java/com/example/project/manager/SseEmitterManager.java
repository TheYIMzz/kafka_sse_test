package com.example.project.manager;

import com.example.project.dto.Event;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class SseEmitterManager {
    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    /**
     * 이벤트 버퍼(최근 MAX_BUFFER_SIZE건 보관)
     */
    private final Deque<Event> buffer = new ArrayDeque<>();
    private final AtomicLong idSeq = new AtomicLong(0);
    private static final int MAX_BUFFER_SIZE = 100;


    public SseEmitter createEmitter() {
        // 1) SseEmitter 객체 생성 (브라우저 별 개별 생성)
        //    SseEmitter(서버‑센트 이벤트(Server‑Sent Events, 서버→클라이언트 단방향 푸시) 연결 관리 객체)
        //    파라미터 0L = 무제한 타임아웃(타임아웃 없음)
        final SseEmitter emitter = new SseEmitter(0L);

        // 2) 동시성 안전 리스트(CopyOnWriteArrayList)에 등록
        //    이후 publishEvent() 호출 시 이 리스트를 순회하며 모든 클라이언트에 푸시
        emitters.add(emitter);


        // 3) onCompletion(연결 정상 종료 시) 콜백 등록
        //    클라이언트가 스트림을 닫거나 에러 없이 완료되면 호출되어 리스트에서 제거
        emitter.onCompletion(new Runnable() {
            @Override public void run() { emitters.remove(emitter); }
        });

        // 4) onTimeout(설정된 시간 내 클라이언트 응답 없을 때) 콜백 등록
        //    타임아웃 발생 시에도 동일하게 리스트에서 제거
        emitter.onTimeout(   new Runnable() {
            @Override public void run() { emitters.remove(emitter); }
        });

        // 5) 생성된 SseEmitter 반환
        //    이 객체가 컨트롤러에서 클라이언트에게 리턴되어
        //    HTTP 응답 스트림이 “롱‑라이브(long‑lived, 장시간 유지) HTTP 연결”로 유지됨
        return emitter;
    }

    /**
     * KafkaListener 에서 호출: 데이터 + ID 생성, 버퍼 저장 후 전체 클라이언트에 푸시
     */
    public void publishEvent(final String data) {
        // 1) 이벤트 객체 생성 및 버퍼에 추가
        String id = String.valueOf(idSeq.incrementAndGet());
        Event ev = new Event(id, data);
        synchronized (buffer) {
            buffer.addLast(ev);
            if (buffer.size() > MAX_BUFFER_SIZE) {
                buffer.removeFirst();
            }
        }

        // 2) 등록된 모든 구독자(emitters: CopyOnWriteArrayList〈SseEmitter〉, 동시성 안전 리스트) 순회
        List<SseEmitter> dead = new ArrayList<>();  // 끊어진 연결 혹은 예외 발생으로 전송 실패한 Emitter를 모아둘 리스트 생성
        for (final SseEmitter emitter : emitters) {
            try {
                // 3) SSE(Server‑Sent Events, 서버→클라이언트 단방향 푸시) 이벤트 전송
                emitter.send(SseEmitter.event()
                                       .id(ev.getId())               // 이벤트 ID 설정
                                       .name("kafka-event")         // 커스텀 이벤트 이름
                                       .data(ev.getData())            // 페이로드
                );
            } catch (IOException ex) {
                // 4) 전송 중 예외 발생 시 해당 emitter를 dead 리스트에 추가
                dead.add(emitter);
            }
        }
        // 5) dead에 모인, 더 이상 유효하지 않은 연결 등 emitter 제거
        //    (메모리 누수 방지 및 다음 전송에서 제외)
        emitters.removeAll(dead);  // 실패한 emitter 모두 제거
    }

    /**
     * Last-Event-ID 이후 버퍼에 남은 이벤트 목록 조회
     */
    public List<Event> findEventsAfter(String lastEventId) {
        List<Event> list = new ArrayList<>();
        synchronized (buffer) {
            for (Event ev : buffer) {
                if (lastEventId == null || ev.getId().compareTo(lastEventId) > 0) {
                    list.add(ev);
                }
            }
        }
        return list;
    }




}
