package com.groupware.event;

import com.groupware.service.DemoAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * WS 끊김으로 시연 세션이 끝났을 때(새로고침·탭종료·크래시) 부산물을 정리한다.
 * 이벤트로 분리해 WebSocketEventListener ↔ DemoAccountService 순환 의존을 피한다.
 * 끊김 유예 스케줄러 스레드를 막지 않도록 @Async로 실행한다.
 */
@Component
@RequiredArgsConstructor
public class DemoSessionCleanupListener {

    private final DemoAccountService demoAccountService;

    @Async
    @EventListener
    public void onDemoSessionEnded(DemoSessionEndedEvent event) {
        demoAccountService.cleanupDemoSession(event.userId());
    }
}
