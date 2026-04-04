package fit.iuh.cnm_project_be.room.persistence;

import fit.iuh.cnm_project_be.room.enums.MemberRole;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class MemberRoleConverter implements AttributeConverter<MemberRole, String> {

    @Override
    public String convertToDatabaseColumn(MemberRole attribute) {
        return attribute == null ? null : attribute.name().toLowerCase();
    }

    @Override
    public MemberRole convertToEntityAttribute(String dbData) {
        return dbData == null ? null : MemberRole.valueOf(dbData.toUpperCase());
    }
}
