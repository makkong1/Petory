package com.linkup.Petory.domain.board.entity;

import com.linkup.Petory.domain.user.entity.Users;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "comment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;

    @ManyToOne
    @JoinColumn(name = "board_idx", nullable = false)
    private Board board;

    @ManyToOne
    @JoinColumn(name = "user_idx", nullable = false)
    private Users user;

    @Lob
    private String content;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
