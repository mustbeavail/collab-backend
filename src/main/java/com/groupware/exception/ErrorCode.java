package com.groupware.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    NICKNAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
    EMAIL_NOT_REGISTERED(HttpStatus.UNAUTHORIZED, "등록되지 않은 이메일입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "비밀번호가 올바르지 않습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "만료된 토큰입니다."),
    REFRESH_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "리프레시 토큰을 찾을 수 없습니다."),

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    WITHDRAWN_USER(HttpStatus.FORBIDDEN, "탈퇴한 사용자입니다."),
    WRONG_CURRENT_PASSWORD(HttpStatus.BAD_REQUEST, "현재 비밀번호가 올바르지 않습니다."),
    SAME_AS_CURRENT_PASSWORD(HttpStatus.BAD_REQUEST, "새 비밀번호가 현재 비밀번호와 동일합니다."),
    INVALID_FILE_TYPE(HttpStatus.BAD_REQUEST, "이미지 파일만 업로드 가능합니다."),
    FILE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "파일 업로드에 실패했습니다."),
    FILE_NOT_FOUND(HttpStatus.NOT_FOUND, "파일을 찾을 수 없습니다."),
    FILE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "파일에 대한 권한이 없습니다."),
    FILE_SIZE_EXCEEDED(HttpStatus.BAD_REQUEST, "파일 크기는 50MB를 초과할 수 없습니다."),

    CANNOT_ADD_SELF(HttpStatus.BAD_REQUEST, "자기 자신에게 친구 요청을 보낼 수 없습니다."),
    FRIEND_REQUEST_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 친구이거나 요청이 존재합니다."),
    FRIEND_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "친구 요청을 찾을 수 없습니다."),
    FRIEND_NOT_FOUND(HttpStatus.NOT_FOUND, "친구 관계를 찾을 수 없습니다."),

    TEAM_NOT_FOUND(HttpStatus.NOT_FOUND, "팀을 찾을 수 없습니다."),
    TEAM_ALREADY_DELETED(HttpStatus.GONE, "이미 삭제된 팀입니다."),
    TEAM_ACCESS_DENIED(HttpStatus.FORBIDDEN, "팀에 대한 권한이 없습니다."),
    NOT_TEAM_MEMBER(HttpStatus.FORBIDDEN, "팀 멤버가 아닙니다."),
    TEAM_ALREADY_MEMBER(HttpStatus.CONFLICT, "이미 팀 멤버이거나 초대 대기 중입니다."),
    TEAM_INVITATION_NOT_FOUND(HttpStatus.NOT_FOUND, "팀 초대를 찾을 수 없습니다."),
    CANNOT_INVITE_SELF(HttpStatus.BAD_REQUEST, "자기 자신을 초대할 수 없습니다."),
    CANNOT_KICK_HIGHER_ROLE(HttpStatus.FORBIDDEN, "자신보다 높거나 같은 역할의 멤버를 추방할 수 없습니다."),
    LEADER_CANNOT_LEAVE(HttpStatus.FORBIDDEN, "팀장은 팀을 나갈 수 없습니다. 팀을 삭제하거나 역할을 위임하세요."),
    LEADER_SELF_DEMOTION(HttpStatus.BAD_REQUEST, "팀장은 자신의 역할을 직접 변경할 수 없습니다. 위임 후 강등하세요."),

    CHAT_ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "채팅방을 찾을 수 없습니다."),
    NOT_ROOM_MEMBER(HttpStatus.FORBIDDEN, "채팅방 멤버가 아닙니다."),

    EMAIL_SEND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "이메일 발송에 실패했습니다."),
    EMAIL_SEND_COOLDOWN(HttpStatus.TOO_MANY_REQUESTS, "인증 메일은 1분에 한 번만 발송할 수 있습니다."),
    EMAIL_CODE_EXPIRED(HttpStatus.BAD_REQUEST, "인증코드가 만료되었습니다."),
    EMAIL_CODE_INVALID(HttpStatus.BAD_REQUEST, "인증코드가 올바르지 않습니다."),
    EMAIL_NOT_VERIFIED(HttpStatus.BAD_REQUEST, "이메일 인증이 완료되지 않았습니다."),

    SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND, "일정을 찾을 수 없습니다."),
    MEETING_NOTE_NOT_FOUND(HttpStatus.NOT_FOUND, "회의록을 찾을 수 없습니다."),
    MEETING_NOTE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "회의록에 대한 권한이 없습니다."),
    MEETING_NOTE_DELETE_DENIED(HttpStatus.FORBIDDEN, "회의록 삭제 권한이 없습니다. (작성자 또는 방장/팀 리더·매니저만 가능)"),
    NO_MESSAGES_IN_RANGE(HttpStatus.BAD_REQUEST, "선택한 시간 범위에 메시지가 없습니다."),
    NO_AUDIO_FILES(HttpStatus.BAD_REQUEST, "오디오 파일이 없습니다."),
    AUDIO_TOO_LARGE(HttpStatus.BAD_REQUEST, "오디오 파일 크기는 15MB를 초과할 수 없습니다."),
    AI_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "AI 회의록 생성에 실패했습니다."),
    TRANSLATE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "번역에 실패했습니다."),

    DEMO_ACCOUNTS_BUSY(HttpStatus.CONFLICT, "모든 테스트계정이 사용중입니다. 잠시 후 다시 시도해주세요."),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
