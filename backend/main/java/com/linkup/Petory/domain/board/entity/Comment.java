package com.linkup.Petory.domain.board.entity;

import com.linkup.Petory.domain.common.ContentStatus;
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

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	@Builder.Default
	private ContentStatus status = ContentStatus.ACTIVE;

	// soft delete support

	private LocalDateTime createdAt;

	@Column(name = "is_deleted")
	@Builder.Default
	private Boolean isDeleted = false;

	@Column(name = "deleted_at")
	private LocalDateTime deletedAt;

    @PrePersist
	protected void onCreate() {
		this.createdAt = LocalDateTime.now();
		if (this.status == null) {
			this.status = ContentStatus.ACTIVE;
		}
	}
}
