
import os
import tempfile
import whisper
from whisper.tokenizer import LANGUAGES

# Load model once at startup (default to 'base' for fast local testing; can switch to 'small')
MODEL_NAME = os.getenv("WHISPER_MODEL", "base")
_model = whisper.load_model(MODEL_NAME)

# Domain prompt to guide decoding toward Twi mobile-money vocabulary
TWI_COMMAND_PROMPT = "sika, balance, fa kɔma, soma, cedi, baako, mmienu, aduonum, data, airtime"

# Preferred language code ('ak' or 'tw' if custom tokenizer supports it; otherwise None for auto)
LANGUAGE_CODE = "ak" if "ak" in LANGUAGES else ("tw" if "tw" in LANGUAGES else None)


def run_asr(audio_bytes: bytes) -> str:
    """
    Transcribes raw audio bytes into Twi text.
    
    Args:
        audio_bytes: Raw bytes of an audio recording (WAV, MP3, M4A, etc.)
        
    Returns:
        Clean transcribed text string.
    """
    if not audio_bytes:
        return ""

    temp_file = tempfile.NamedTemporaryFile(suffix=".wav", delete=False)
    temp_path = temp_file.name

    try:
        temp_file.write(audio_bytes)
        temp_file.flush()
        temp_file.close()

        # Transcribe with domain vocabulary prompt
        transcribe_args = {
            "initial_prompt": TWI_COMMAND_PROMPT,
            "fp16": False,  # CPU friendly
        }
        if LANGUAGE_CODE:
            transcribe_args["language"] = LANGUAGE_CODE

        result = _model.transcribe(temp_path, **transcribe_args)
        return result.get("text", "").strip()

    finally:
        if os.path.exists(temp_path):
            try:
                os.remove(temp_path)
            except OSError:
                pass
