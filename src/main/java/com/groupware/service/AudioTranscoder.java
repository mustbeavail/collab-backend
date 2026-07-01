package com.groupware.service;

import com.groupware.exception.CustomException;
import com.groupware.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * 음성 회의록용 오디오 트랜스코더.
 * 브라우저 MediaRecorder 녹음물은 webm(opus) 컨테이너인데 Gemini generateContent 인라인 오디오는
 * webm을 지원하지 않는다(지원: wav/mp3/aac/ogg/flac 등). 그래서 ffmpeg로 ogg(opus)로 재인코딩해 전달한다.
 */
@Slf4j
@Component
public class AudioTranscoder {

    private static final long FFMPEG_TIMEOUT_SEC = 120;

    private final String ffmpegPath;

    public AudioTranscoder(@Value("${ffmpeg.path:ffmpeg}") String ffmpegPath) {
        this.ffmpegPath = ffmpegPath;
    }

    /**
     * webm 등 입력 오디오를 ogg(opus, mono 32k)로 변환한다.
     * @param input 원본 오디오 바이트(webm 등)
     * @return ogg 컨테이너 바이트
     */
    public byte[] toOggOpus(byte[] input) {
        Path in = null;
        Path out = null;
        try {
            in = Files.createTempFile("audio-in-", ".webm");
            out = Files.createTempFile("audio-out-", ".ogg");
            Files.write(in, input);
            // ffmpeg가 -y로 덮어쓸 수 있게 미리 만든 빈 out 파일은 그대로 둔다.

            Process process = new ProcessBuilder(
                    ffmpegPath, "-hide_banner", "-loglevel", "error", "-y",
                    "-i", in.toString(),
                    "-vn",                       // 영상 트랙 제거(오디오 전용 녹음이지만 안전차원)
                    "-c:a", "libopus",           // opus 재인코딩
                    "-b:a", "32k", "-ac", "1",   // 음성용 저비트레이트 모노 → 파일 작게(Gemini 15MB 인라인 한도)
                    out.toString())
                    .redirectErrorStream(true)
                    .start();

            String ffmpegOutput = new String(process.getInputStream().readAllBytes());
            boolean finished = process.waitFor(FFMPEG_TIMEOUT_SEC, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.error("ffmpeg 변환 타임아웃({}초 초과)", FFMPEG_TIMEOUT_SEC);
                throw new CustomException(ErrorCode.AUDIO_TRANSCODE_FAILED);
            }
            if (process.exitValue() != 0) {
                log.error("ffmpeg 변환 실패(exit={}): {}", process.exitValue(), ffmpegOutput.trim());
                throw new CustomException(ErrorCode.AUDIO_TRANSCODE_FAILED);
            }

            byte[] result = Files.readAllBytes(out);
            if (result.length == 0) {
                log.error("ffmpeg 변환 결과가 비어있음");
                throw new CustomException(ErrorCode.AUDIO_TRANSCODE_FAILED);
            }
            return result;

        } catch (IOException e) {
            log.error("ffmpeg 실행/파일 처리 실패: {}", e.getMessage());
            throw new CustomException(ErrorCode.AUDIO_TRANSCODE_FAILED);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("ffmpeg 변환 중단됨: {}", e.getMessage());
            throw new CustomException(ErrorCode.AUDIO_TRANSCODE_FAILED);
        } finally {
            deleteQuietly(in);
            deleteQuietly(out);
        }
    }

    /** ffmpeg 실행 가능 여부(테스트 가드용). */
    public boolean isAvailable() {
        try {
            Process process = new ProcessBuilder(ffmpegPath, "-version")
                    .redirectErrorStream(true).start();
            process.getInputStream().readAllBytes();
            return process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void deleteQuietly(Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("임시 오디오 파일 삭제 실패: {}", path);
        }
    }
}
