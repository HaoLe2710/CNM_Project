package fit.iuh.cnm_project_be.message.persistence;

import fit.iuh.cnm_project_be.message.enums.MessageDeliveryStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class MessageDeliveryStatusConverter implements AttributeConverter<MessageDeliveryStatus, String> {

    @Override
    public String convertToDatabaseColumn(MessageDeliveryStatus attribute) {
        return attribute == null ? null : attribute.name().toLowerCase();
    }

    @Override
    public MessageDeliveryStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : MessageDeliveryStatus.valueOf(dbData.toUpperCase());
    }
}
