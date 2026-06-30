package com.groupware.event;

/**
 * 시연 계정의 WS 연결이 끊겨(새로고침·탭종료·크래시) 오프라인이 확정됐을 때 발행되는 이벤트.
 * 리스너가 시연 부산물을 정리한다(중단/완료 버튼 외 경로의 백스톱).
 */
public record DemoSessionEndedEvent(String userId) {
}
