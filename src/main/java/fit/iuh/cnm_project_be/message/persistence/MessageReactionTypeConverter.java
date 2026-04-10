package fit.iuh.cnm_project_be.message.persistence;

import fit.iuh.cnm_project_be.message.enums.MessageReactionType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class MessageReactionTypeConverter implements AttributeConverter<MessageReactionType, String> {

    @Override
    public String convertToDatabaseColumn(MessageReactionType attribute) {
        return attribute == null ? null : attribute.name().toLowerCase();
    }

    @Override
    public MessageReactionType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : MessageReactionType.valueOf(dbData.toUpperCase());
    }
}
