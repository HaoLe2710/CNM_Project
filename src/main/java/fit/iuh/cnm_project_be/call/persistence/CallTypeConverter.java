package fit.iuh.cnm_project_be.call.persistence;

import fit.iuh.cnm_project_be.call.enums.CallType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class CallTypeConverter implements AttributeConverter<CallType, String> {
    @Override
    public String convertToDatabaseColumn(CallType attribute) {
        return attribute == null ? null : attribute.name().toLowerCase();
    }

    @Override
    public CallType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : CallType.valueOf(dbData.toUpperCase());
    }
}
