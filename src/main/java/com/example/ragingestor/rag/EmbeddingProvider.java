package com.example.ragingestor.rag;

import java.util.List;

public interface EmbeddingProvider {
    float[] embed(String text);
    List<float[]> embedBatch(List<String> texts);
    String modelId();
}
