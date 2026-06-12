package com.groupware.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class SignupRequest {

    @NotBlank(message = "이메일은 필수입니다.")
    @Email(message = "올바른 이메일 형식이 아닙니다.")
    private String email;

    @NotBlank(message = "비밀번호는 필수입니다.")
    @Size(max = 100, message = "비밀번호는 100자 이하여야 합니다.")
    private String password;

    @NotBlank(message = "닉네임은 필수입니다.")
    @Size(max = 20, message = "닉네임은 20자 이하여야 합니다.")
    private String nickname;

    @Size(max = 500, message = "소개는 500자 이하여야 합니다.")
    private String about;
}
