package fit.iuh.cnm_project_be.message_processing.provider.impl;

import fit.iuh.cnm_project_be.message_processing.provider.TextToSpeechProvider;
import fit.iuh.cnm_project_be.message_processing.provider.TextToSpeechRequest;
import fit.iuh.cnm_project_be.message_processing.provider.TextToSpeechResult;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class MockTextToSpeechProvider implements TextToSpeechProvider {

    private static final int SAMPLE_RATE = 16_000;
    private static final int BITS_PER_SAMPLE = 16;
    private static final int CHANNELS = 1;

    @Override
    public String providerName() {
        return "mock";
    }

    @Override
    public TextToSpeechResult synthesize(TextToSpeechRequest request) {
        String text = request != null && request.getText() != null ? request.getText() : "";
        int durationSeconds = estimateDurationSeconds(text);
        byte[] wavBytes = buildWaveBytes(text, durationSeconds);

        return TextToSpeechResult.builder()
                .audioBytes(wavBytes)
                .mimeType("audio/wav")
                .durationMs(durationSeconds * 1000L)
                .build();
    }

    private int estimateDurationSeconds(String text) {
        int textLength = Math.max(text.trim().length(), 1);
        int seconds = (int) Math.ceil(textLength / 18.0);
        return Math.max(1, Math.min(seconds, 20));
    }

    private byte[] buildWaveBytes(String text, int durationSeconds) {
        int totalSamples = SAMPLE_RATE * durationSeconds;
        int byteRate = SAMPLE_RATE * CHANNELS * (BITS_PER_SAMPLE / 8);
        int blockAlign = CHANNELS * (BITS_PER_SAMPLE / 8);
        int dataSize = totalSamples * blockAlign;

        double baseFrequency = 220.0 + Math.abs(text.hashCode() % 180);
        double amplitude = 7_200.0;

        try (ByteArrayOutputStream output = new ByteArrayOutputStream(44 + dataSize)) {
            writeAscii(output, "RIFF");
            writeLittleEndianInt(output, 36 + dataSize);
            writeAscii(output, "WAVE");
            writeAscii(output, "fmt ");
            writeLittleEndianInt(output, 16);
            writeLittleEndianShort(output, (short) 1);
            writeLittleEndianShort(output, (short) CHANNELS);
            writeLittleEndianInt(output, SAMPLE_RATE);
            writeLittleEndianInt(output, byteRate);
            writeLittleEndianShort(output, (short) blockAlign);
            writeLittleEndianShort(output, (short) BITS_PER_SAMPLE);
            writeAscii(output, "data");
            writeLittleEndianInt(output, dataSize);

            for (int index = 0; index < totalSamples; index++) {
                double phase = (2 * Math.PI * baseFrequency * index) / SAMPLE_RATE;
                short sample = (short) Math.round(Math.sin(phase) * amplitude);
                writeLittleEndianShort(output, sample);
            }

            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to generate mock TTS audio", ex);
        }
    }

    private void writeAscii(ByteArrayOutputStream output, String value) throws IOException {
        output.write(value.getBytes(StandardCharsets.US_ASCII));
    }

    private void writeLittleEndianInt(ByteArrayOutputStream output, int value) throws IOException {
        output.write(value & 0xFF);
        output.write((value >> 8) & 0xFF);
        output.write((value >> 16) & 0xFF);
        output.write((value >> 24) & 0xFF);
    }

    private void writeLittleEndianShort(ByteArrayOutputStream output, short value) throws IOException {
        output.write(value & 0xFF);
        output.write((value >> 8) & 0xFF);
    }
}
