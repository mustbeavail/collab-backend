package com.groupware.service;

import com.groupware.dto.draw.DrawEventPayload.DrawElement;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DrawStateStoreTest {

    private final DrawStateStore store = new DrawStateStore();

    private DrawElement el(String id) {
        DrawElement e = new DrawElement();
        e.setId(id);
        e.setType("pencil");
        return e;
    }

    @Test
    void append_후_snapshot에_반영() {
        store.append(1L, el("a"));
        store.append(1L, el("b"));

        assertThat(store.snapshot(1L)).extracting(DrawElement::getId).containsExactly("a", "b");
    }

    @Test
    void append_같은id_중복무시() {
        store.append(1L, el("a"));
        store.append(1L, el("a"));

        assertThat(store.snapshot(1L)).hasSize(1);
    }

    @Test
    void append_null이거나_id없으면_무시() {
        store.append(1L, null);
        store.append(1L, el(null));

        assertThat(store.snapshot(1L)).isEmpty();
    }

    @Test
    void remove_해당id_제거() {
        store.append(1L, el("a"));
        store.append(1L, el("b"));

        store.remove(1L, "a");

        assertThat(store.snapshot(1L)).extracting(DrawElement::getId).containsExactly("b");
    }

    @Test
    void clear_방전체_초기화() {
        store.append(1L, el("a"));
        store.clear(1L);

        assertThat(store.snapshot(1L)).isEmpty();
    }

    @Test
    void 방별로_독립_보관() {
        store.append(1L, el("a"));
        store.append(2L, el("b"));

        assertThat(store.snapshot(1L)).extracting(DrawElement::getId).containsExactly("a");
        assertThat(store.snapshot(2L)).extracting(DrawElement::getId).containsExactly("b");
    }

    @Test
    void 없는방_snapshot은_빈리스트() {
        assertThat(store.snapshot(999L)).isEmpty();
    }
}
