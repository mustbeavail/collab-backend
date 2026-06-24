package com.groupware.service;

import com.groupware.dto.chart.ChartSharePayload;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 항목4(일정이후 *추가4): 방별 마지막으로 공유된 차트를 서버 메모리에 보관.
 *
 * <p>차트는 WebSocket 릴레이만 했기에, 패널을 나중에 여는 사용자는 그 전에 다른 사용자가
 * 만든 차트를 볼 수 없었다. 그림판(DrawStateStore)과 동일하게 방별 최신 차트를 보관해두면,
 * 패널을 (다시) 여는 사용자가 REST 스냅샷으로 현재 공유 중인 차트를 받아 표시할 수 있다.
 *
 * <p>그림판은 요소를 누적하지만 차트는 "방의 현재 차트" 하나만 의미가 있으므로 최신 1개만 보관한다.
 * 단일 인스턴스 인메모리(서버 재시작 시 초기화).
 */
@Component
public class ChartStateStore {

    private final Map<Long, ChartSharePayload> roomCharts = new ConcurrentHashMap<>();

    public void put(Long roomIdx, ChartSharePayload payload) {
        if (roomIdx == null || payload == null) return;
        roomCharts.put(roomIdx, payload);
    }

    public ChartSharePayload get(Long roomIdx) {
        return roomCharts.get(roomIdx);
    }

    public void clear(Long roomIdx) {
        roomCharts.remove(roomIdx);
    }
}
