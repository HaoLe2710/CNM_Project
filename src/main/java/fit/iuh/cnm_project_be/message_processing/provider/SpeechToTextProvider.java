package fit.iuh.cnm_project_be.message_processing.provider;

public interface SpeechToTextProvider {

    String providerName();

    SpeechToTextResult transcribe(SpeechToTextRequest request);
}
