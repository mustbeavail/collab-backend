package com.groupware.dto.draw;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class DrawEventPayload {

    private String eventType;  // DRAW_MOVE | DRAW_DONE | DRAW_CLEAR | DRAW_UNDO
    private String userId;
    private String nickname;
    private DrawElement element;
    private String elementId;  // DRAW_UNDO 시 제거할 요소 ID

    @Getter
    @Setter
    @NoArgsConstructor
    public static class DrawElement {
        private String id;
        private String type;          // pencil | eraser | line | rect | ellipse
        private List<Double> points;  // pencil / eraser / line
        private Double x, y, w, h;   // rect
        private Double cx, cy, rx, ry; // ellipse
        private String color;
        private Integer size;
    }
}
