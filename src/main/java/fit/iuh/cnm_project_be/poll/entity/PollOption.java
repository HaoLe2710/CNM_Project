package fit.iuh.cnm_project_be.poll.entity;

import fit.iuh.cnm_project_be.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "poll_options")
@Getter
@Setter
public class PollOption extends BaseEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "poll_id", nullable = false)
    private UUID pollId;

    @Column(nullable = false)
    private String text;

    @Column(name = "position", nullable = false)
    private int position;
}
