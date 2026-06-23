package com.groupware.service;

import com.groupware.dto.draw.DrawEventPayload.DrawElement;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 항목8(일정이후): 방별 그림판 캔버스 상태(완성된 요소 목록)를 서버 메모리에 보관.
 *
 * <p>그림판은 WebSocket 릴레이만 했기에 패널을 닫은 사용자는 구독이 끊겨 그동안의 드로잉을 놓쳤다.
 * 완성(DRAW_DONE)·취소(DRAW_UNDO)·전체삭제(DRAW_CLEAR) 이벤트를 여기에 반영해두면,
 * 패널을 (다시) 여는 사용자가 REST 스냅샷으로 그 이전에 그려진 내용을 받아볼 수 있다.
 *
 * <p>단일 인스턴스 인메모리(서버 재시작 시 초기화). 진행중(DRAW_MOVE)은 저장하지 않는다.
 */
@Component
public class DrawStateStore {

    /** 방당 보관 요소 상한(메모리 보호). 초과 시 신규 append 무시. */
    private static final int MAX_ELEMENTS_PER_ROOM = 5000;

    private final Map<Long, List<DrawElement>> roomElements = new ConcurrentHashMap<>();

    public void append(Long roomIdx, DrawElement element) {
        if (element == null || element.getId() == null) return;
        List<DrawElement> list = roomElements.computeIfAbsent(
                roomIdx, k -> Collections.synchronizedList(new ArrayList<>()));
        synchronized (list) {
            if (list.size() >= MAX_ELEMENTS_PER_ROOM) return;
            boolean dup = list.stream().anyMatch(e -> element.getId().equals(e.getId()));
            if (!dup) list.add(element);
        }
    }

    public void remove(Long roomIdx, String elementId) {
        if (elementId == null) return;
        List<DrawElement> list = roomElements.get(roomIdx);
        if (list == null) return;
        synchronized (list) {
            list.removeIf(e -> elementId.equals(e.getId()));
        }
    }

    public void clear(Long roomIdx) {
        roomElements.remove(roomIdx);
    }

    public List<DrawElement> snapshot(Long roomIdx) {
        List<DrawElement> list = roomElements.get(roomIdx);
        if (list == null) return List.of();
        synchronized (list) {
            return new ArrayList<>(list);
        }
    }
}
