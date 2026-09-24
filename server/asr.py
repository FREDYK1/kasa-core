
import os
import tempfile
import torch
from transformers import pipeline

# Model selection: Defaults to Akan Whisper model fine-tuned for Twi
# Can be overridden via AKAN_MODEL environment variable
MODEL_NAME = os.getenv("AKAN_MODEL", "GiftMark/akan-whisper-model")

# Device selection: Use GPU if available, otherwise CPU
device = 0 if torch.cuda.is_available() else "cpu"

print(f"[ASR] Loading Akan ASR model '{MODEL_NAME}' on {device}...")
_pipeline = pipeline(
    "automatic-speech-recognition",
    model=MODEL_NAME,
    device=device,
)
print("[ASR] Akan ASR model loaded and ready.")


def run_asr(audio_bytes: bytes) -> str:
    """
    Transcribes raw audio bytes into Twi text using the Akan Whisper model.
    
    Args:
        audio_bytes: Raw bytes of an audio recording (WAV, MP3, M4A, etc.)
        
    Returns:
        Clean transcribed text string in Twi.
    """
    if not audio_bytes:
        return ""

    temp_file = tempfile.NamedTemporaryFile(suffix=".wav", delete=False)
    temp_path = temp_file.name

    try:
        temp_file.write(audio_bytes)
        temp_file.flush()
        temp_file.close()

        try:
            result = _pipeline(temp_path)
            return result.get("text", "").strip()
        except Exception as e:
            print(f"[ASR] Audio decoding error: {e}")
            return ""
    finally:
        if os.path.exists(temp_path):
            try:
                os.remove(temp_path)
            except OSError:
                pass

