package com.groupware.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * ffmpeg 실제 변환 테스트. ffmpeg가 있는 환경에서만 실행(없으면 skip).
 * 로컬은 collab-backend/tools/ffmpeg/ffmpeg.exe, CI/Docker는 PATH의 ffmpeg를 탐색한다.
 */
class AudioTranscoderTest {

    // 후보 경로 순서대로 실행 가능한 ffmpeg를 찾는다.
    private static String resolveFfmpeg() {
        String[] candidates = {
                System.getProperty("ffmpeg.path"),
                "tools/ffmpeg/ffmpeg.exe",   // 로컬 Windows 정적 바이너리(작업 디렉토리 = collab-backend)
                "ffmpeg"                     // PATH
        };
        for (String c : candidates) {
            if (c != null && new AudioTranscoder(c).isAvailable()) {
                return c;
            }
        }
        return null;
    }

    // ffmpeg로 1초짜리 opus webm 오디오를 만들어 바이트로 반환(입력 픽스처 생성).
    private byte[] makeWebmFixture(String ffmpeg) throws Exception {
        Path out = Files.createTempFile("fixture-", ".webm");
        try {
            Process p = new ProcessBuilder(
                    ffmpeg, "-hide_banner", "-loglevel", "error", "-y",
                    "-f", "lavfi", "-i", "sine=frequency=440:duration=1",
                    "-c:a", "libopus", out.toString())
                    .redirectErrorStream(true).start();
            p.getInputStream().readAllBytes();
            p.waitFor(30, TimeUnit.SECONDS);
            return Files.readAllBytes(out);
        } finally {
            Files.deleteIfExists(out);
        }
    }

    @Test
    void webm을_ogg로_변환한다() throws Exception {
        String ffmpeg = resolveFfmpeg();
        assumeTrue(ffmpeg != null, "ffmpeg 미설치 환경 — 변환 테스트 skip");

        AudioTranscoder transcoder = new AudioTranscoder(ffmpeg);
        byte[] webm = makeWebmFixture(ffmpeg);
        assertThat(webm).isNotEmpty();

        byte[] ogg = transcoder.toOggOpus(webm);

        assertThat(ogg).isNotEmpty();
        // Ogg 컨테이너 매직 시그니처 "OggS"
        assertThat(new String(ogg, 0, 4)).isEqualTo("OggS");
    }
}
