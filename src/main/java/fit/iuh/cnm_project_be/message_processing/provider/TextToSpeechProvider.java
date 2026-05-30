package fit.iuh.cnm_project_be.message_processing.provider;

public interface TextToSpeechProvider {

    String providerName();

    TextToSpeechResult synthesize(TextToSpeechRequest request);
}
