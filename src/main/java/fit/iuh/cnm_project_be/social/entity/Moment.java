package fit.iuh.cnm_project_be.social.entity;

import fit.iuh.cnm_project_be.common.entity.BaseEntity;
import fit.iuh.cnm_project_be.social.enums.MomentAudioMode;
import fit.iuh.cnm_project_be.social.enums.MediaType;
import fit.iuh.cnm_project_be.social.enums.MomentVisibilityMode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;

import java.util.UUID;

@Entity
@Table(name = "moments")
@SQLDelete(sql = "UPDATE moments SET deleted_at = now() WHERE id = ?")
@Getter
@Setter
public class Moment extends BaseEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    private String caption;
    @Column(name = "media_url", nullable = false)
    private String mediaUrl;

    @Column(name = "cover_url")
    private String coverUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false)
    private MediaType mediaType;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility_mode", nullable = false)
    private MomentVisibilityMode visibilityMode = MomentVisibilityMode.FRIENDS;

    @Column(name = "duration_seconds", nullable = false)
    private Integer durationSeconds = 0;

    @Column(name = "share_count", nullable = false)
    private Long shareCount = 0L;

    @Enumerated(EnumType.STRING)
    @Column(name = "audio_mode", nullable = false)
    private MomentAudioMode audioMode = MomentAudioMode.NONE;

    @Column(name = "music_track_id")
    private String musicTrackId;

    @Column(name = "music_title")
    private String musicTitle;

    @Column(name = "music_artist")
    private String musicArtist;

    @Column(name = "music_url")
    private String musicUrl;

    @Column(name = "music_start_seconds", nullable = false)
    private Integer musicStartSeconds = 0;
}
