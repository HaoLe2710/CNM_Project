package fit.iuh.cnm_project_be.room.persistence;

import fit.iuh.cnm_project_be.room.enums.ConversationType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class ConversationTypeConverter implements AttributeConverter<ConversationType, String> {

    @Override
    public String convertToDatabaseColumn(ConversationType attribute) {
        return attribute == null ? null : attribute.name().toLowerCase();
    }

    @Override
    public ConversationType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : ConversationType.valueOf(dbData.toUpperCase());
    }
}
