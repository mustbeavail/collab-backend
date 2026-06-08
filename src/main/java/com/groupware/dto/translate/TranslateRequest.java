package com.groupware.dto.translate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class TranslateRequest {

    @NotBlank
    @Size(max = 5000)
    private String text;

    @NotBlank
    private String targetLang;
}
