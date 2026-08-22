package com.localai.chat.data.model

/**
 * Central catalog of supported models.
 *
 * IMPORTANT: The Gemma models require accepting the license on Kaggle before downloading.
 * Kaggle download links are not direct; the user must obtain a direct URL from:
 *   https://www.kaggle.com/models/google/gemma
 * or from Hugging Face:
 *   https://huggingface.co/google/gemma-2b-it-gpu-int4
 *
 * The placeholder URLs below point to public Hugging Face LFS files.
 * Replace DOWNLOAD_URL with a verified direct link to the .task file.
 *
 * For testing without a real model, keep USE_MOCK_ENGINE = true in ServiceLocator.
 */
object ModelCatalog {

    /**
     * Gemma 2B-IT GPU INT4 — best balance for 12 GB RAM devices.
     * File format: MediaPipe .task bundle.
     * Approx size: 1.5 GB.
     *
     * Direct download available at (requires Kaggle account + license acceptance):
     *   https://www.kaggle.com/models/google/gemma/tfLite/gemma-2b-it-gpu-int4/1
     *
     * Alternative (HuggingFace, may require HF token):
     *   https://huggingface.co/google/gemma-2b-it-gpu-int4/resolve/main/gemma-2b-it-gpu-int4.bin
     */
    // Official Kaggle API download endpoint (requires Kaggle account + Gemma license accepted).
    // Get credentials at: kaggle.com/settings → API → Create New Token (kaggle.json)
    // Accept the license at: kaggle.com/models/google/gemma
    const val GEMMA_2B_DOWNLOAD_URL =
        "https://www.kaggle.com/api/v1/models/google/gemma/tfLite/gemma-2b-it-gpu-int4/1/download"

    /**
     * Phi-2 2.7B — alternate small model.
     * Not officially in MediaPipe format; listed as placeholder.
     */
    const val PHI2_DOWNLOAD_URL = ""

    val entries: List<ModelInfo> = listOf(
        ModelInfo(
            id = "gemma_2b_it_int4",
            displayName = "Gemma 2B-IT (GPU INT4)",
            description = "Google Gemma 2B instruction-tuned, 4-bit quantized. Recommended for 12 GB RAM.",
            sizeBytes = 1_500_000_000L, // ~1.5 GB
            downloadUrl = GEMMA_2B_DOWNLOAD_URL,
            fileName = "gemma-2b-it-gpu-int4.bin"
        ),
        ModelInfo(
            id = "gemma_2b_it_int4_cpu",
            displayName = "Gemma 2B-IT (CPU INT4)",
            description = "Google Gemma 2B instruction-tuned, 4-bit quantized for CPU. Slower but broader compatibility.",
            sizeBytes = 1_500_000_000L,
            downloadUrl = GEMMA_2B_DOWNLOAD_URL, // replace with CPU-specific URL
            fileName = "gemma-2b-it-cpu-int4.bin"
        )
    )

    fun findById(id: String): ModelInfo? = entries.find { it.id == id }
}
