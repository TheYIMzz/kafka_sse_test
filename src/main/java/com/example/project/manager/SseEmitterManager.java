package com.example.project.manager;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class SseEmitterManager {
    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();

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

    // 카프카 리스너에서 호출되는 메서드
    public void publishEvent(final String data) {
        // 1) 끊어진 연결 혹은 예외 발생으로 전송 실패한 Emitter를 모아둘 리스트 생성
        //    dead 리스트(전송 실패한 SseEmitter 모음)
        List<SseEmitter> dead = new ArrayList<SseEmitter>();

        // 2) 등록된 모든 구독자(emitters: CopyOnWriteArrayList〈SseEmitter〉, 동시성 안전 리스트) 순회
        for (final SseEmitter emitter : emitters) {
            try {
                // 3) SSE(Server‑Sent Events, 서버→클라이언트 단방향 푸시) 이벤트 전송
                emitter.send(SseEmitter.event()  // 이벤트 빌더 생성
                        .name("kafka-event")  // 커스텀 이벤트 이름 (클라이언트가 addEventListener('kafka-event', …) 로 잡는 이름을 정의)
                        .data(data));            // payload(전송할 메시지 데이터)
            } catch (Exception ex) {
                // 4) 전송 중 예외 발생 시 해당 emitter를 dead 리스트에 추가
                dead.add(emitter);
            }
        }
        // 5) dead에 모인, 더 이상 유효하지 않은 연결 등 emitter 제거
        //    (메모리 누수 방지 및 다음 전송에서 제외)
        emitters.removeAll(dead);
    }
}
