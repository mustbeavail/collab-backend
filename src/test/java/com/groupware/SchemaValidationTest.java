package com.groupware;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 엔티티 ↔ DB 스키마 일치 검증 하네스.
 *
 * 로컬 collab_test DB에 대해 전체 Spring 컨텍스트를 부팅한다(= 서버를 한 번 켜는 것).
 * spring.jpa.hibernate.ddl-auto=validate 이므로, 모든 @Entity 가 실제 테이블/컬럼과
 * 대조된다. 엔티티엔 있는데 DB엔 없는 컬럼(또는 그 반대)이 있으면 컨텍스트 생성이 실패해
 * 이 테스트가 깨진다. "엔티티만 고치고 DB·schema.sql 갱신을 빠뜨려 부팅 실패"하는
 * 재발 버그를 mvn test 단계에서 자동으로 잡는다.
 *
 * 전제: 로컬 MariaDB(collab_test)가 떠 있고 schema.sql 로 최신화돼 있어야 한다.
 *       DB 미기동이면 이 테스트만 실패한다(나머지 단위 테스트는 DB 불필요).
 */
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        // 메인 application.yml 이 테스트 application.yml 에 가려져 사라지는 값.
        // JavaMailSender 빈 생성을 위해 mail.host 만 보강(실제 발송 없음).
        "spring.mail.host=smtp.gmail.com"
})
@ActiveProfiles("local")
class SchemaValidationTest {

    @Test
    void 엔티티와_DB_스키마가_일치하면_컨텍스트가_부팅된다() {
        // 컨텍스트 부팅 성공 = 검증 통과. 별도 단언 불필요.
    }
}
