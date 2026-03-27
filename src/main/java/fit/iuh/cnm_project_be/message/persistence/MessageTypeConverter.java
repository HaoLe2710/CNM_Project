package fit.iuh.cnm_project_be.message.persistence;

import fit.iuh.cnm_project_be.message.enums.MessageType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class MessageTypeConverter implements AttributeConverter<MessageType, String> {

    @Override
    public String convertToDatabaseColumn(MessageType attribute) {
        return attribute == null ? null : attribute.name().toLowerCase();
    }

    @Override
    public MessageType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : MessageType.valueOf(dbData.toUpperCase());
    }
}
