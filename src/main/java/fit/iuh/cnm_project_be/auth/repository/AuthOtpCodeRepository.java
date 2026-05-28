package fit.iuh.cnm_project_be.auth.repository;

import fit.iuh.cnm_project_be.auth.entity.AuthOtpCode;
import fit.iuh.cnm_project_be.auth.enums.OtpType;
import fit.iuh.cnm_project_be.common.repository.BaseRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuthOtpCodeRepository extends BaseRepository<AuthOtpCode, UUID> {

    Optional<AuthOtpCode> findFirstByIdentifierAndOtpTypeAndUsedAtIsNullOrderByCreatedAtDesc(String identifier, OtpType otpType);

    @Modifying
    @Query("""
        update AuthOtpCode o
        set o.usedAt = :usedAt
        where o.identifier = :identifier
          and o.otpType = :otpType
          and o.usedAt is null
    """)
    int markActiveCodesUsed(
            @Param("identifier") String identifier,
            @Param("otpType") OtpType otpType,
            @Param("usedAt") Instant usedAt
    );

    @Modifying
    @Query("""
        delete from AuthOtpCode o
        where o.expiresAt < :now
           or (o.usedAt is not null and o.usedAt < :cutoff)
    """)
    int purgeExpiredOrOldUsedCodes(@Param("now") Instant now, @Param("cutoff") Instant cutoff);

    List<AuthOtpCode> findByIdentifierAndOtpTypeOrderByCreatedAtDesc(String identifier, OtpType otpType);
}
