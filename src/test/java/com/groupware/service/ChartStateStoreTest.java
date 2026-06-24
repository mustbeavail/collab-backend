package com.groupware.service;

import com.groupware.dto.chart.ChartAnalyzeResponse;
import com.groupware.dto.chart.ChartSharePayload;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChartStateStoreTest {

    private final ChartStateStore store = new ChartStateStore();

    private ChartSharePayload payload(String fromUserId) {
        ChartSharePayload p = new ChartSharePayload();
        p.setFromUserId(fromUserId);
        p.setChartConfig(new ChartAnalyzeResponse());
        return p;
    }

    @Test
    void put_후_get으로_조회() {
        ChartSharePayload p = payload("alice@test.com");
        store.put(1L, p);

        assertThat(store.get(1L)).isSameAs(p);
    }

    @Test
    void put_최신차트로_덮어씀() {
        store.put(1L, payload("alice@test.com"));
        store.put(1L, payload("bob@test.com"));

        assertThat(store.get(1L).getFromUserId()).isEqualTo("bob@test.com");
    }

    @Test
    void put_null이면_무시() {
        store.put(1L, null);
        store.put(null, payload("alice@test.com"));

        assertThat(store.get(1L)).isNull();
    }

    @Test
    void clear_후_null() {
        store.put(1L, payload("alice@test.com"));
        store.clear(1L);

        assertThat(store.get(1L)).isNull();
    }

    @Test
    void 방별로_독립_보관() {
        store.put(1L, payload("alice@test.com"));
        store.put(2L, payload("bob@test.com"));

        assertThat(store.get(1L).getFromUserId()).isEqualTo("alice@test.com");
        assertThat(store.get(2L).getFromUserId()).isEqualTo("bob@test.com");
    }

    @Test
    void 공유된적없는방은_null() {
        assertThat(store.get(999L)).isNull();
    }
}
