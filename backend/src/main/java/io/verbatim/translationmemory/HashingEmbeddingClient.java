package io.verbatim.translationmemory;

import java.text.Normalizer;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class HashingEmbeddingClient implements EmbeddingClient {

    public static final int DIMENSIONS = 384;

    @Override
    public float[] embed(String text) {
        String normalized = Normalizer.normalize(
            text == null ? "" : text,
            Normalizer.Form.NFKC
        ).toLowerCase(Locale.ROOT);
        float[] vector = new float[DIMENSIONS];
        String[] words = normalized.split("[^\\p{L}\\p{N}]+");
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            add(vector, word, 1.5f);
            String padded = "^" + word + "$";
            for (int index = 0; index <= padded.length() - 3; index++) {
                add(vector, padded.substring(index, index + 3), 0.35f);
            }
        }
        double norm = 0;
        for (float value : vector) {
            norm += value * value;
        }
        if (norm == 0) {
            return vector;
        }
        float divisor = (float) Math.sqrt(norm);
        for (int index = 0; index < vector.length; index++) {
            vector[index] /= divisor;
        }
        return vector;
    }

    private void add(float[] vector, String feature, float weight) {
        int hash = feature.hashCode();
        int index = Math.floorMod(hash, vector.length);
        vector[index] += (hash & 1) == 0 ? weight : -weight;
    }
}
