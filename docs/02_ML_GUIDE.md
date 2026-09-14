# 02 · ML GUIDE — Kelvin

**Your mission:** make the app hear Twi and speak Twi. Speech in, speech out. Everything else depends on these two working well enough to trust.

**You own:** the ASR (Twi audio → text) and TTS (text → Twi audio) pipelines, behind two clean functions Selorm imports.
**You do NOT own:** the parsing logic, the server endpoints, or the app.

**The key idea that makes your job feasible:** we are **not** solving open-domain Twi speech recognition. The user only says a handful of things — a few actions, numbers, and contact names. That is a *constrained command grammar*. You are optimising for high accuracy on a small vocabulary, which is a completely different, much easier problem than general Twi ASR. Lean into it.

**The contract you must honour:**
```python
def run_asr(audio_bytes: bytes) -> str        # Twi audio -> Twi text
def synthesize_twi(text: str) -> bytes        # Twi text  -> audio (wav)
```
Agree these signatures with Selorm on Day 1 and never change them. Behind them you can swap models freely.

---

## Layer by layer

### Step 1 — Get the models (Day 1, do this first)

Email **Evans** (evanskwasi17@gmail.com) and **dcshcilab@ug.edu.gh** today for:
- `dcshcilab_Whisper` (Akan ASR checkpoint) and `dcshcilab_Vits` (Akan TTS)
- the open Akan datasets

While you wait, set up fallbacks so you are never blocked:
- **ASR fallback:** `openai-whisper` (`whisper-small`) or the GhanaNLP Khaya ASR API.
- **TTS fallback:** GhanaNLP Khaya TTS, or pre-recorded prompts (Step 4).

```bash
cd server
pip install openai-whisper soundfile numpy
# GPU on a laptop makes whisper-small comfortably real-time; CPU works but is slower
```

### Step 2 — Baseline ASR behind the contract (Days 1–2)

Get *something* returning text on Day 1, even if imperfect. Wrap it:

```python
# asr.py
import whisper
_model = whisper.load_model("small")   # later: load the lab's Akan checkpoint here

def run_asr(audio_bytes: bytes) -> str:
    # save bytes to a temp wav, then:
    result = _model.transcribe("temp.wav", language="ak")  # 'ak' = Akan if supported
    return result["text"].strip()
```

Give Selorm `run_asr`. His `/understand` now works end-to-end with real (if rough) transcription. **This unblocks the whole pipeline.**

### Step 3 — Make it accurate on OUR commands (Days 2–6)

1. **Collect domain audio.** ~25–30 minutes is enough to fine-tune (Evans confirmed: fine-tune, don't pre-train). Record the team + the accessibility lead + a few volunteers saying the command set many ways: "check my balance", "send 5 cedis to Kofi", "buy 1 cedi data", with different amounts and names. Save as audio + exact Twi transcript pairs.
2. **Bias decoding toward the command vocabulary.** Even without fine-tuning, constraining/boosting the lexicon (numbers, action words, contact names) sharply cuts errors on a small vocabulary. Do this first — it's cheap.
3. **Fine-tune** whisper-small (or the lab checkpoint) on your ~30 min set if accuracy still isn't there. Kaggle/Colab give free GPU.
4. **Handle code-switching** (Twi + English mixed, e.g. "send credit") — it's normal Ghanaian speech and it's in your test set from the start, not an afterthought.

**Done when:** on real users, the command set transcribes reliably enough that Selorm's parser gets the right action + amount most of the time. Measure it — see Step 6.

### Step 4 — TTS: pre-record the fixed prompts, synthesize the rest (Days 3–5)

There are only a handful of *fixed* prompts. Pre-record them with a clear Twi speaker — highest quality, zero latency, works offline:
- "What would you like to do?"
- "Send how much, and to whom?"
- "Please enter your PIN."
- "Done." / "It was successful." / "Something went wrong."

Only the *variable* parts need synthesis: amounts, names, balance figures. Provide:

```python
def synthesize_twi(text: str) -> bytes: ...          # VITS / Khaya for dynamic bits
def say_amount_twi(amount: float) -> bytes: ...       # number -> Twi words -> audio
```

**Number-to-Twi is a real task** — "9,207.93 cedis" must come out as natural Twi. Build a small number-to-Twi-words converter with the accessibility lead (he'll tell you what sounds right), then synthesize or stitch pre-recorded number words. Get this right; it's what a blind user actually hears.

### Step 5 — On-device is the *stated* path, not the build path (note only)

For the prototype, models run on the laptop server. Say clearly in the presentation that production targets on-device (whisper.cpp / ONNX / TFLite, quantised) for offline use. **Do not spend build days on this** unless the core works early and you have time to spare.

### Step 6 — Evaluate honestly, by subgroup (throughout)

The CV mentor was emphatic: never report one number. Track:
- **Command accuracy** = did we get the right action + amount + recipient — this matters more than raw word-error-rate.
- **By subgroup:** by speaker, by amount type (digits vs Twi words), by device mic, by clean vs noisy audio, by code-switched vs pure Twi.
- **Real-user test** is the golden metric — a model that scores well but confuses a real speaker has failed.

This subgroup table is also evidence for the mentors. Keep it.

---

## How you integrate

- **With Selorm:** you hand him `run_asr` and `synthesize_twi`. He stubs them Day 1, imports the real ones when ready. If a signature must change, tell him first.
- **With Richmond:** he sends audio to the server and plays back the audio you return. He needs to know the audio format you accept/return (agree: 16kHz mono wav). Tell him.
- **With the accessibility lead:** he is your source of truth for how Twi commands and numbers actually sound. Record him, test on him.

## Your day-by-day

- **Day 1:** emails sent; whisper-small baseline; `run_asr` handed to Selorm.
- **Day 2–3:** lexicon biasing; start collecting command audio; fixed TTS prompts recorded.
- **Day 4–6:** fine-tune if needed; number-to-Twi readback working; code-switching in the test set.
- **Day 7–8:** swap in the lab checkpoints if they arrive and beat the fallback; integrate.
- **Day 9+:** subgroup evaluation; fix the worst-performing subgroup (it lifts the average); real-user testing.
