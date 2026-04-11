package fit.iuh.cnm_project_be.user.persistence;

import fit.iuh.cnm_project_be.user.enums.FriendRequestStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class FriendRequestStatusConverter implements AttributeConverter<FriendRequestStatus, String> {
    @Override
    public String convertToDatabaseColumn(FriendRequestStatus attribute) {
        return attribute == null ? null : attribute.name().toLowerCase();
    }

    @Override
    public FriendRequestStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : FriendRequestStatus.valueOf(dbData.toUpperCase());
    }

}
