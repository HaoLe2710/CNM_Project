package fit.iuh.cnm_project_be.call.persistence;

import fit.iuh.cnm_project_be.call.enums.CallStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class CallStatusConverter implements AttributeConverter<CallStatus, String> {
    @Override
    public String convertToDatabaseColumn(CallStatus attribute) {
        return attribute == null ? null : attribute.name().toLowerCase();
    }

    @Override
    public CallStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : CallStatus.valueOf(dbData.toUpperCase());
    }
}
