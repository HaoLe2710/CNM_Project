package fit.iuh.cnm_project_be.call.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CallActionRequest(
    @JsonProperty("action") String action,
    @JsonProperty("value") String value
) {}
