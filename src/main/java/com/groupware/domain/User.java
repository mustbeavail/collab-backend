package com.groupware.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter @Setter @NoArgsConstructor
public class User {

    @Id
    @Column(name = "user_id", length = 254, nullable = false)
    private String userId;

    @Column(name = "pw", length = 100)
    private String pw;

    @Column(name = "nick", length = 40)
    private String nick;

    @Column(name = "about", length = 6000)
    private String about;

    @Column(name = "join_at")
    private LocalDateTime joinAt;

    @Column(name = "withdrwal_at")
    private LocalDateTime withdrwalAt;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;
}
