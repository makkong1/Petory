package com.linkup.Petory.domain.admin.entity;

import com.linkup.Petory.domain.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "system_config",
    indexes = @Index(name = "idx_system_config_key", columnList = "config_key", unique = true))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
/** key-value 형태의 시스템 설정 엔티티. MASTER 권한으로 런타임에 값을 변경할 수 있다. */
public class SystemConfig extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;

    @Column(name = "config_key", nullable = false, unique = true, length = 100)
    private String configKey;

    @Column(name = "config_value", nullable = false, length = 500)
    private String configValue;

    @Column(name = "description", length = 200)
    private String description;
}
