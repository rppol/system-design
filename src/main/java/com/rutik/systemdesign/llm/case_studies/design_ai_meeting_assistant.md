# Case Study: Design an AI Meeting Assistant

## Intuition

> **Design intuition**: An AI meeting assistant is a court stenographer plus analyst embedded invisibly in every meeting — the challenge is doing it in real-time with millisecond audio segments, then synthesizing hours of conversation into two paragraphs that someone actually acts on.

**Key insight for this design**: Two fundamentally different architectural choices — bot-in-meeting versus local-process capture — produce completely different privacy postures, cost structures, and latency profiles. Every major design decision downstream (STT hosting, consent management, diarization approach, GPU fleet sizing) is dictated by which capture path you choose. A system that tries to support both without clear boundaries ends up with neither the cost advantage of local-process nor the platform breadth of the bot approach.

The core tension is not "how do we transcribe audio" — Whisper and its competitors have solved that — but "how do we make something genuinely useful come out of 45 minutes of human conversation, at a cost that allows a sustainable business, while remaining legally defensible in every jurisdiction where we operate."

---

## 1. Requirements Clarification

### Functional Requirements
- Real-time transcription displayed as a rolling transcript to the meeting host with less than 3 seconds of display lag from the moment speech occurs
- Speaker diarization: each transcript line attributed to a named participant (resolved from calendar invite or voice enrollment)
- Automatic action item extraction: owner, task description, optional due date extracted from natural language during or after the meeting
- Post-meeting summary: executive summary (3 sentences maximum), key decisions, open questions
- Searchable meeting history: full-text search across all past transcripts within a workspace
- Calendar integration: automatic join for scheduled meetings (Google Calendar, Outlook) without user intervention
- Export integrations: push action items to Slack, Notion, Linear, Jira; meeting summaries to email or Confluence
- Multi-platform bot support (bot-in-meeting path): Zoom, Google Meet, Microsoft Teams

### Non-Functional Requirements
- Transcription display latency: word visible on screen within 3 seconds of being spoken (p95)
- Post-meeting summary generation: complete within 30 seconds of meeting end
- Availability during active meetings: 99.9% (a dropped meeting cannot be recovered — the moment is gone)
- GDPR compliance: affirmative per-participant consent captured and auditable; participant opt-out honored within 60 seconds
- SOC 2 Type II certification required for enterprise sales
- Tenant isolation: no cross-workspace data access; transcripts encrypted at rest with per-tenant KMS keys

### Out of Scope
- Video recording and video playback (audio transcription only)
- Live translation between languages (addressed in design_real_time_translation.md)
- Phone call transcription (PSTN integration has distinct regulatory requirements)
- Custom wake-word or voice command UI

---

## 2. Scale Estimation

### Traffic Model
```
DAU:                            5,000,000
Avg meetings per user per day:  3
Avg meeting duration:           45 minutes = 0.75 hours
Total meeting-hours per day:    5M x 3 x 0.75 = 11.25M audio-hours/day

Peak concurrency estimate:
  5M DAU x 3 meetings/day x 45 min/meeting = 675M user-minutes/day
  / 1,440 min/day = 468,750 concurrent meeting streams at steady average
  Peak factor (business hours concentration): 2.5x
  Peak concurrent streams: 468,750 x 2.5 = 1,171,875 streams
  Round to planning target: 1.2M concurrent streams at business-hour peak
```

### STT GPU Sizing
```
Model: Whisper large-v3 on NVIDIA A10G (24 GB)
Real-time factor: 50x  (50 minutes of audio processed per GPU-minute)
Each GPU handles 50 concurrent 1x real-time audio streams

Required GPUs at peak:
  1,200,000 concurrent streams / 50 rt-factor = 24,000 A10Gs
  At 70% target utilization: 24,000 / 0.70 = 34,286 A10Gs

Cost at $1.30/GPU-hour (on-demand, us-east-1: g5.12xlarge is $5.672/hour
for 4x A10G = $1.42/GPU-hour; g5.xlarge is $1.006/hour. Spot on the same
family runs ~$0.65-1.09/GPU-hour, so $1.30 is a conservative planning rate):
  34,286 x $1.30 = $44,572/hour
  x 8 peak hours/day + 16 off-peak hours at 30% load:
  ($44,572 x 8) + ($44,572 x 0.30 x 16) = $356,576 + $213,946 = $570,522/day

Note: two different self-hosted numbers apply and must not be conflated.
  Marginal GPU cost at 100% utilization:
    $1.30/GPU-hour / (50 streams x 60 min) = $0.00043/audio-min
    11.25M hours x 60 min x $0.00043 = ~$290,000/day
  Provisioned cost (70% utilization target + peak/off-peak profile above):
    $570,522/day = $0.00085/audio-min -- this is the number to budget against
  Managed API (OpenAI whisper-1 or gpt-4o-transcribe, both $0.006/min;
  gpt-4o-mini-transcribe is $0.003/min):
    11.25M hours x 60 min/hour x $0.006 = $4,050,000/day -- not viable
  Self-hosted is ~7x cheaper on provisioned cost, ~14x on marginal cost.
```

### LLM Summarization Cost
```
Summarization model: a small-tier model -- gpt-5.4-nano at $0.20/1M input
and $1.25/1M output (Claude Haiku 4.5 at $1/$5 is the Anthropic equivalent)
Avg transcript: 6,000 words -> 8,000 tokens input, 400 tokens output (summary)
Map-reduce overhead for long meetings: 2x multiplier on average
Effective output tokens per meeting: 800 tokens
Daily meetings: 5M users x 3 = 15M meetings/day
Daily output tokens: 15M x 800 = 12B tokens
Output cost: 12B / 1M x $1.25 = $15,000/day
(Input dominates by volume -- 15M x 16,000 tokens = 240B/day x $0.20/M
 = $48,000/day -- so budget ~$63,000/day for summarization, still two
 orders of magnitude below the GPU fleet.)
```

### Storage
```
Transcript text per meeting:       ~50 KB (45 min at ~180 words/min)
Summary per meeting:               ~5 KB
Daily transcript volume:           15M meetings x 50 KB = 750 GB/day
Daily summary volume:              15M x 5 KB = 75 GB/day
Retention: 3 years for enterprise, 1 year for free tier
3-year storage (enterprise 20% of users):
  750 GB/day x 365 x 3 x 0.20 = 164 TB (transcripts)
  + cold storage for audio WAV if retained: 45 min x 16 kHz x 2 bytes = 86 MB/meeting
    -> 15M x 86 MB x 0.20 retention = 258 PB -- audio not retained by default
```

---

## 3. High-Level Architecture

### Two Capture Paths

```mermaid
flowchart LR
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    subgraph BOTPATH["Bot-in-Meeting Path - Fireflies, Otter.ai"]
        direction LR
        ZM(["User's Zoom / Meet / Teams"]) -->|"bot joins<br/>as participant"| BS(["Bot Server<br/>k8s pod per meeting"])
        BS -->|"audio chunks<br/>16kHz PCM, 5s"| VAD1(["VAD Filter<br/>Silero VAD"])
        VAD1 --> STT1(["STT Service<br/>Whisper large-v3<br/>self-hosted"])
        STT1 -->|"rolling<br/>transcript segments"| TS(["Transcript Store<br/>Postgres + S3"])
        TS --> LLM1(["LLM Pipeline<br/>post-meeting"])
        LLM1 --> DL(["Delivery Layer<br/>WebSocket, Slack/Notion push"])
    end

    subgraph LOCALPATH["Local-Process Path - Granola, macOS/Windows"]
        direction LR
        OSAUD(["CoreAudio macOS or<br/>WASAPI Windows"]) -->|"per-process<br/>audio taps"| LA(["Local Agent<br/>Swift / Rust"])
        LA -->|"VAD-filtered speech only<br/>raw audio never leaves device"| VAD2(["Local VAD<br/>WebRTC or Silero on-device"])
        VAD2 --> STT2(["STT API call<br/>cloud Whisper or<br/>local whisper.cpp"])
        STT2 -->|"transcript<br/>text only"| LLM2(["LLM Pipeline<br/>small-tier API<br/>gpt-5.4-nano or<br/>Claude Haiku 4.5"])
        LLM2 -->|"summary,<br/>action items"| UI(["Local App UI +<br/>Cloud Sync optional"])
    end

    class ZM,OSAUD req
    class BS,LA io
    class VAD1,VAD2 mathOp
    class STT1,STT2 base
    class TS frozen
    class LLM1,LLM2 train
    class DL,UI lossN
```

### Full System Component Map

```mermaid
flowchart TD
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    GW["API Gateway / CDN\nauth, rate limit\nrouting to region"]
    CAL["Calendar Poller + AutoJoiner\n(GCal/Outlook)"]
    WSGW["WebSocket GW\nrolling tx to UI"]
    BOT(["Bot Runner (per-meeting k8s pod)\nOR Local Agent (device-side)"])
    STT["STT Cluster\nWhisper, A10G fleet\nself-hosted"]
    DIA["Diarizer\npyannote 3.1\nspeaker labels"]
    DB[["Transcript DB\nPostgres + OpenSearch"]]
    CONSENT["Consent Manager\nper-meeting audit log S3"]
    SUM["Summarizer\nLLM pipeline, map-reduce"]
    EXPORT["Export Worker\nSlack, Notion, Linear, email"]

    GW --> CAL
    GW --> WSGW
    CAL --> BOT
    BOT -->|"audio chunks"| STT
    STT --> WSGW
    STT -->|"transcript segments"| DIA
    DIA --> DB
    BOT -->|"consent events"| DB
    DB --> CONSENT
    CONSENT -->|"on meeting end"| SUM
    SUM --> EXPORT

    class GW,WSGW,EXPORT req
    class CAL frozen
    class BOT io
    class STT,SUM base
    class DIA mathOp
    class DB io
    class CONSENT lossN
```

Audio flows from the bot runner (or local agent) into the self-hosted Whisper STT cluster, then through diarization into the transcript store, while consent events from the capture layer land in the same store; the consent manager gates the on-meeting-end summarize-and-export chain.

See [./cross_cutting/streaming_at_scale.md](./cross_cutting/streaming_at_scale.md) for WebSocket fan-out patterns used in the delivery layer.

---

## 4. Component Deep Dives

### 4a. StreamingTranscriber — Sliding-Window Whisper

Whisper is an offline model: it processes a complete audio file and returns a single transcript. Naively sending audio in fixed non-overlapping 5-second chunks produces word-boundary artifacts — words split mid-phoneme at the chunk edge are misrecognized, and acoustic context is lost at every boundary.

**Broken approach — fixed non-overlapping chunks:**

```python
# BROKEN: 5-second hard cuts destroy word boundary accuracy
async def transcribe_broken(audio_stream):
    buffer = bytearray()
    async for chunk in audio_stream:
        buffer.extend(chunk)
        if len(buffer) >= SAMPLE_RATE * 5 * 2:  # 5s at 16kHz, 16-bit
            text = await whisper.transcribe(bytes(buffer))  # loses context
            yield text
            buffer.clear()  # discard everything -- no overlap
```

Words at the 5-second boundary are split mid-utterance. "action item" spoken across a boundary becomes "action" in one chunk and "item" in the next, each missing acoustic context, causing "action" to be misheard as "actionable" and "item" to be dropped entirely. WER at chunk boundaries is 3-5x higher than at mid-chunk positions.

**Fix — sliding 30-second window with 5-second advance and deduplication:**

```python
import asyncio
from dataclasses import dataclass, field
from collections import deque
from typing import AsyncIterator
import numpy as np

SAMPLE_RATE = 16_000
BYTES_PER_SAMPLE = 2  # 16-bit PCM
WINDOW_SECS = 30
ADVANCE_SECS = 5
OVERLAP_SECS = WINDOW_SECS - ADVANCE_SECS  # 25s of acoustic context retained


@dataclass
class TranscriptSegment:
    start_ms: int
    end_ms: int
    text: str
    speaker_id: str
    confidence: float


class StreamingTranscriber:
    def __init__(self, whisper_client, diarizer) -> None:
        self.whisper = whisper_client
        self.diarizer = diarizer
        self._ring: deque[tuple[bytes, int]] = deque()  # (chunk_bytes, start_ms)
        self._last_committed_ms: int = 0
        self._window_start_ms: int = 0

    async def feed_audio(
        self, chunk: bytes, timestamp_ms: int
    ) -> list[TranscriptSegment]:
        self._ring.append((chunk, timestamp_ms))
        self._trim_ring()

        window_audio = self._assemble_window()
        window_duration_ms = (
            len(window_audio) // BYTES_PER_SAMPLE / SAMPLE_RATE * 1000
        )
        if window_duration_ms < ADVANCE_SECS * 1000:
            return []  # not enough audio yet

        raw = await self.whisper.transcribe(
            audio=window_audio,
            language="en",
            word_timestamps=True,
            temperature=0.0,
        )
        labels = await self.diarizer.label(window_audio, raw)
        new_segments = self._dedup_and_emit(raw, labels)
        if new_segments:
            self._last_committed_ms = new_segments[-1].end_ms
        return new_segments

    def _trim_ring(self) -> None:
        # Keep only last WINDOW_SECS of audio in the ring buffer
        cutoff_ms = self._ring[-1][1] - WINDOW_SECS * 1000
        while self._ring and self._ring[0][1] < cutoff_ms:
            self._ring.popleft()

    def _assemble_window(self) -> bytes:
        return b"".join(chunk for chunk, _ in self._ring)

    def _dedup_and_emit(
        self, raw_segments: list, speaker_labels: dict[int, str]
    ) -> list[TranscriptSegment]:
        # Only emit segments whose start_ms is beyond last committed position
        # This prevents re-emitting words covered by the overlap region
        return [
            TranscriptSegment(
                start_ms=s.start_ms,
                end_ms=s.end_ms,
                text=s.text.strip(),
                speaker_id=speaker_labels.get(s.start_ms, "UNKNOWN"),
                confidence=s.confidence,
            )
            for s in raw_segments
            if s.start_ms > self._last_committed_ms and s.text.strip()
        ]
```

The 25-second overlap means every word is covered by at least two transcription passes. The `_dedup_and_emit` method uses the `_last_committed_ms` cursor to emit each word exactly once while benefiting from the wider acoustic context window.

### 4b. SpeakerDiarizer — Who Said What

Speaker diarization assigns each transcript segment to a named participant. The challenge differs fundamentally between the two capture paths.

In **bot-in-meeting mode**, all participant audio arrives in a single mixed stream. Diarization must separate N speakers from one channel using only acoustic cues (voice embeddings via ECAPA-TDNN). This is hard: when three people speak simultaneously, diarization error rate (DER) rises from a typical 5% to 35%+.

In **local-process mode** on macOS, CoreAudio's process audio tapping provides separate streams — the Zoom process audio (remote participants) and the local microphone (host) are already separated. This gives local-process a structural diarization advantage that cannot be replicated in bot mode.

```python
from dataclasses import dataclass
from typing import Optional
import numpy as np

COSINE_THRESHOLD = 0.85  # empirically tuned on internal test set
ENROLLMENT_WINDOW_S = 30  # first 30 seconds used for speaker enrollment


@dataclass
class SpeakerProfile:
    name: str
    embedding: np.ndarray  # 192-dim ECAPA-TDNN embedding
    enrolled_at_ms: int


class SpeakerDiarizer:
    def __init__(self, embedding_model, calendar_participants: list[str]) -> None:
        self.model = embedding_model
        self.profiles: dict[str, SpeakerProfile] = {}
        self._pending_names: list[str] = list(calendar_participants)
        self._cluster_buffer: list[tuple[np.ndarray, int]] = []  # (embedding, start_ms)

    async def enroll_from_audio(
        self, audio: bytes, participant_name: str
    ) -> SpeakerProfile:
        embedding = await self.model.embed(audio)
        profile = SpeakerProfile(
            name=participant_name,
            embedding=embedding,
            enrolled_at_ms=0,
        )
        self.profiles[participant_name] = profile
        return profile

    async def label(
        self, audio: bytes, segments: list
    ) -> dict[int, str]:
        """Return mapping of segment start_ms -> speaker name."""
        result: dict[int, str] = {}
        for seg in segments:
            seg_audio = self._slice_audio(audio, seg.start_ms, seg.end_ms)
            embedding = await self.model.embed(seg_audio)
            speaker = self._match_or_assign(embedding, seg.start_ms)
            result[seg.start_ms] = speaker
        return result

    def _match_or_assign(self, embedding: np.ndarray, start_ms: int) -> str:
        best_name: Optional[str] = None
        best_score = 0.0
        for name, profile in self.profiles.items():
            score = float(
                np.dot(embedding, profile.embedding)
                / (np.linalg.norm(embedding) * np.linalg.norm(profile.embedding))
            )
            if score > best_score:
                best_score = score
                best_name = name

        if best_score >= COSINE_THRESHOLD and best_name is not None:
            return best_name

        # Below threshold: new speaker — assign from pending calendar names or generate ID
        if self._pending_names:
            name = self._pending_names.pop(0)
        else:
            name = f"Speaker_{len(self.profiles) + 1}"
        self.profiles[name] = SpeakerProfile(
            name=name, embedding=embedding, enrolled_at_ms=start_ms
        )
        return name

    @staticmethod
    def _slice_audio(audio: bytes, start_ms: int, end_ms: int) -> bytes:
        start_byte = int(start_ms / 1000 * SAMPLE_RATE * BYTES_PER_SAMPLE)
        end_byte = int(end_ms / 1000 * SAMPLE_RATE * BYTES_PER_SAMPLE)
        return audio[start_byte:end_byte]
```

See [./cross_cutting/streaming_at_scale.md](./cross_cutting/streaming_at_scale.md) for how speaker embedding lookups are cached to avoid redundant model inference on repeated audio from the same speaker within a sliding window.

### 4c. MeetingSummarizer — Map-Reduce for Long Meetings

A single "summarize this transcript" prompt fails for meetings longer than 20 minutes. A 60-minute meeting produces roughly 9,000 words — 12,000 tokens — which exceeds the effective attention span for reliable action item extraction even in 128K-context models. The approach: segment the transcript by topic (detected via sentence embedding clustering), summarize each segment independently, then merge the chunk summaries into a final structured output.

```python
import asyncio
from pydantic import BaseModel
from datetime import date
from typing import Optional
from sklearn.metrics.pairwise import cosine_similarity
import numpy as np

CHUNK_TOKEN_LIMIT = 10_000
MAX_PARALLEL_CHUNKS = 6


class ActionItem(BaseModel):
    task: str
    owner: str
    due_date: Optional[date] = None
    context: str  # verbatim quote or close paraphrase from transcript


class MeetingSummary(BaseModel):
    executive_summary: str       # exactly 3 sentences
    key_decisions: list[str]
    action_items: list[ActionItem]
    open_questions: list[str]
    topics_covered: list[str]


EXTRACTION_PROMPT = """You are extracting structured information from a meeting transcript excerpt.

Transcript:
{transcript}

Return a JSON object with these fields:
- executive_summary: string, exactly 3 sentences describing the most important outcomes
- key_decisions: list of strings, each a specific decision that was finalized
- action_items: list of objects with fields: task, owner (name from transcript), due_date (ISO date or null), context
- open_questions: list of strings, questions raised but not resolved
- topics_covered: list of 2-4 word topic labels

Return only valid JSON. Do not include commentary."""


class MeetingSummarizer:
    def __init__(self, llm, embedding_model) -> None:
        self.llm = llm
        self.embed = embedding_model

    async def summarize(
        self, segments: list[TranscriptSegment]
    ) -> MeetingSummary:
        token_count = self._estimate_tokens(segments)
        if token_count <= CHUNK_TOKEN_LIMIT:
            return await self._direct_summarize(segments)
        return await self._map_reduce_summarize(segments)

    async def _direct_summarize(
        self, segments: list[TranscriptSegment]
    ) -> MeetingSummary:
        transcript_text = "\n".join(
            f"{s.speaker_id}: {s.text}" for s in segments
        )
        response = await self.llm.complete(
            prompt=EXTRACTION_PROMPT.format(transcript=transcript_text),
            response_model=MeetingSummary,
            max_tokens=800,
        )
        return response

    async def _map_reduce_summarize(
        self, segments: list[TranscriptSegment]
    ) -> MeetingSummary:
        chunks = await self._segment_by_topic(segments)
        # Cap parallelism to avoid LLM rate limits
        chunk_summaries: list[str] = []
        for i in range(0, len(chunks), MAX_PARALLEL_CHUNKS):
            batch = chunks[i : i + MAX_PARALLEL_CHUNKS]
            results = await asyncio.gather(
                *[self._summarize_chunk_to_text(c) for c in batch]
            )
            chunk_summaries.extend(results)
        combined = "\n\n---\n\n".join(chunk_summaries)
        return await self._merge_summaries(combined)

    async def _segment_by_topic(
        self, segments: list[TranscriptSegment]
    ) -> list[list[TranscriptSegment]]:
        # Embed each segment; use cosine similarity drop as topic boundary
        texts = [s.text for s in segments]
        embeddings = await self.embed.batch_embed(texts)
        boundaries = [0]
        for i in range(1, len(embeddings)):
            sim = float(
                cosine_similarity([embeddings[i - 1]], [embeddings[i]])[0][0]
            )
            if sim < 0.65:  # topic shift detected
                boundaries.append(i)
        boundaries.append(len(segments))
        return [
            segments[boundaries[j] : boundaries[j + 1]]
            for j in range(len(boundaries) - 1)
        ]

    async def _summarize_chunk_to_text(
        self, chunk: list[TranscriptSegment]
    ) -> str:
        transcript_text = "\n".join(
            f"{s.speaker_id}: {s.text}" for s in chunk
        )
        return await self.llm.complete(
            prompt=(
                "Summarize this meeting excerpt. List decisions made and "
                f"action items with owners:\n\n{transcript_text}"
            ),
            max_tokens=400,
        )

    async def _merge_summaries(self, combined_text: str) -> MeetingSummary:
        return await self.llm.complete(
            prompt=EXTRACTION_PROMPT.format(transcript=combined_text),
            response_model=MeetingSummary,
            max_tokens=800,
        )

    @staticmethod
    def _estimate_tokens(segments: list[TranscriptSegment]) -> int:
        total_chars = sum(len(s.text) for s in segments)
        return total_chars // 4  # rough 4-chars-per-token heuristic
```

See [./cross_cutting/llm_eval_harness_in_production.md](./cross_cutting/llm_eval_harness_in_production.md) for the eval harness used to measure action item recall against a golden set of 200 hand-labeled meeting transcripts.

### 4d. ConsentManager — GDPR Compliance per Participant

GDPR Article 13 requires that data subjects be informed about processing before it begins. For meeting recording, this means every participant — not just the meeting host — must be informed and given the opportunity to object. A consent log failure is not a product bug; it is a regulatory liability.

```python
import hashlib
import json
from datetime import datetime, timezone
from dataclasses import dataclass, asdict
from typing import Literal
import boto3

ConsentStatus = Literal["granted", "denied", "pending", "withdrawn"]


@dataclass
class ConsentEvent:
    meeting_id: str
    participant_email_hash: str  # SHA-256, not raw email (PII minimization)
    status: ConsentStatus
    captured_at_iso: str
    capture_method: str          # "bot_announcement", "ui_banner", "email_link"
    ip_country: str              # for jurisdiction determination


class ConsentManager:
    CONSENT_BUCKET = "meeting-consent-audit-immutable"
    ANNOUNCEMENT_TEXT = (
        "This meeting is being transcribed by an AI assistant. "
        "Type /opt-out in chat at any time to stop transcription for you."
    )

    def __init__(self, s3_client=None) -> None:
        self.s3 = s3_client or boto3.client("s3")
        self._consent_cache: dict[str, ConsentStatus] = {}

    async def announce_and_capture(
        self,
        meeting_id: str,
        participants: list[str],
        bot_chat_fn,
    ) -> None:
        await bot_chat_fn(self.ANNOUNCEMENT_TEXT)
        for email in participants:
            await self._record_consent(
                meeting_id=meeting_id,
                email=email,
                status="granted",
                method="bot_announcement",
            )

    async def handle_opt_out(
        self, meeting_id: str, participant_email: str
    ) -> None:
        await self._record_consent(
            meeting_id=meeting_id,
            email=participant_email,
            status="withdrawn",
            method="chat_command",
        )
        self._consent_cache[f"{meeting_id}:{participant_email}"] = "withdrawn"

    def is_transcription_allowed(
        self, meeting_id: str, participant_email: str
    ) -> bool:
        key = f"{meeting_id}:{participant_email}"
        return self._consent_cache.get(key, "granted") != "withdrawn"

    async def _record_consent(
        self,
        meeting_id: str,
        email: str,
        status: ConsentStatus,
        method: str,
    ) -> None:
        event = ConsentEvent(
            meeting_id=meeting_id,
            participant_email_hash=hashlib.sha256(
                email.lower().encode()
            ).hexdigest(),
            status=status,
            captured_at_iso=datetime.now(timezone.utc).isoformat(),
            capture_method=method,
            ip_country="",  # populated by API gateway header in production
        )
        s3_key = f"consent/{meeting_id}/{event.captured_at_iso}.json"
        self.s3.put_object(
            Bucket=self.CONSENT_BUCKET,
            Key=s3_key,
            Body=json.dumps(asdict(event)),
            ContentType="application/json",
        )
        cache_key = f"{meeting_id}:{email}"
        self._consent_cache[cache_key] = status
```

See [./cross_cutting/tenant_isolation_patterns.md](./cross_cutting/tenant_isolation_patterns.md) for how the consent audit bucket uses S3 Object Lock (WORM) to prevent post-hoc deletion of consent records under regulatory hold.

### 4e. CalendarIntegration and AutoJoiner

The auto-join feature is where users experience the product as "magical." The bot must join the Zoom meeting 90 seconds before the scheduled start — Zoom provisioning takes 30-60 seconds, and appearing after the meeting has started is disruptive.

```python
import asyncio
from datetime import datetime, timezone, timedelta
from dataclasses import dataclass
from typing import Optional
import httpx

BOT_LEAD_TIME_S = 90
POLL_INTERVAL_S = 300  # check calendar every 5 minutes
ZOOM_API_RATE_LIMIT_RPM = 100


@dataclass
class CalendarEvent:
    event_id: str
    title: str
    start_dt: datetime
    meeting_url: Optional[str]      # extracted from description / conferenceData
    participants: list[str]
    user_id: str


class AutoJoiner:
    def __init__(self, calendar_client, bot_launcher, consent_manager) -> None:
        self.calendar = calendar_client
        self.launcher = bot_launcher
        self.consent = consent_manager
        self._scheduled: set[str] = set()

    async def run_forever(self, user_id: str) -> None:
        while True:
            await self._scan_and_schedule(user_id)
            await asyncio.sleep(POLL_INTERVAL_S)

    async def _scan_and_schedule(self, user_id: str) -> None:
        window_start = datetime.now(timezone.utc)
        window_end = window_start + timedelta(minutes=15)
        events = await self.calendar.list_events(
            user_id=user_id,
            time_min=window_start,
            time_max=window_end,
        )
        for event in events:
            if event.event_id in self._scheduled:
                continue
            if not event.meeting_url:
                continue
            if event.start_dt <= window_start:
                continue  # already started
            delay_s = (
                event.start_dt - window_start
            ).total_seconds() - BOT_LEAD_TIME_S
            if delay_s < 0:
                delay_s = 0
            self._scheduled.add(event.event_id)
            asyncio.create_task(
                self._delayed_join(event, delay_s)
            )

    async def _delayed_join(
        self, event: CalendarEvent, delay_s: float
    ) -> None:
        await asyncio.sleep(delay_s)
        try:
            await self.consent.announce_and_capture(
                meeting_id=event.event_id,
                participants=event.participants,
                bot_chat_fn=lambda msg: self.launcher.send_chat(
                    event.meeting_url, msg
                ),
            )
            await self.launcher.join(
                meeting_url=event.meeting_url,
                meeting_id=event.event_id,
                user_id=event.user_id,
            )
        except httpx.HTTPStatusError as exc:
            if exc.response.status_code == 429:
                # Zoom rate limit -- exponential backoff, max 3 retries
                await asyncio.sleep(30)
                await self.launcher.join(
                    meeting_url=event.meeting_url,
                    meeting_id=event.event_id,
                    user_id=event.user_id,
                )
```

---

## 5. Design Decisions & Tradeoffs

| Decision | Option A (chosen) | Option B | Rationale |
|---|---|---|---|
| Capture path | Bot-in-meeting (primary) + local-process (premium) | Bot-in-meeting only | Local-process provides lower cloud cost and better privacy; required to serve privacy-conscious enterprise and macOS power users |
| STT hosting | Self-hosted Whisper on an A10G fleet (spot where interruption-tolerant) | API (OpenAI whisper-1 / gpt-4o-transcribe, AssemblyAI) | API cost: $4.05M/day; self-hosted provisioned: $570K/day — ~7x cost reduction justifies ops investment at this scale |
| Transcription mode | Real-time streaming with sliding window | Post-meeting only | Real-time transcript is table-stakes UX; post-meeting only is untenable for the "glanceable during meeting" use case |
| Summarization model | Small-tier hosted model (gpt-5.4-nano / Claude Haiku 4.5) with structured output | Per-workspace fine-tuned open-weight 8B | Fine-tuning cost: ~$200/workspace, latency advantage minimal; small-tier structured output is reliable; revisit at 50K+ workspace scale |
| Transcript storage | Raw segments (Postgres) + compressed summaries (S3) | Summaries only | Raw segments required for full-text search, speaker timeline view, and re-summarization on model upgrade; marginal storage cost ($270K/year) justified |
| Diarization approach | Cloud pyannote.audio (bot path) / local channel separation (local-process path) | Speaker-independent labels only | Named speaker attribution directly impacts action item accuracy; UNKNOWN labels cause 40% drop in action item adoption |

---

## 6. Real-World Implementations

**Granola** (local-process first, now cross-platform): captures system audio on the device rather than joining as a bot. Two distinct macOS APIs enable this and are often conflated: **ScreenCaptureKit** (introduced in macOS 12.3) captures system and per-application audio, and **Core Audio process taps** (`AudioHardwareCreateProcessTap`, introduced in macOS 14.2) provide genuine per-process audio taps. Only text — never raw audio — is transmitted to cloud LLM APIs. This architectural choice gives Granola low transcript latency (no audio upload round-trip) and a strong privacy story: "your voice never leaves your device." Granola's editor-first UI, where the AI transcript fills in behind user-typed notes, is its most-copied design decision. Granola, Inc. remains independent, raised $125M in a round announced March 2025, and has since shipped Windows, iOS, and Android clients — so the local-process approach is no longer macOS-only, and the macOS-exclusivity moat has already eroded.

**Fireflies.ai**: bot-in-meeting, multi-platform (Zoom, Meet, Teams, Webex). Deep CRM integrations with Salesforce and HubSpot allow automatic population of call notes from meeting transcript. Fireflies markets a proprietary STT model tuned on sales-call vocabulary to improve recognition of product names, competitor names, and pricing terms; the size of that WER gain over base Whisper is not independently published, so treat any specific figure as vendor-reported. Company-reported user and revenue figures are self-disclosed and not audited.

**Otter.ai**: the original consumer meeting assistant (founded 2016 by Sam Liang and Yun Fu, originally AISense), pre-GPT. Pivoted from individual transcription to team-wide meeting intelligence as the individual market commoditized. Venture-backed across multiple rounds (including a $10M round led by Docomo Ventures in January 2020); introduced "OtterPilot" as an AI that actively participates in meetings by answering questions in the chat. Otter's challenge: commoditization from Zoom AI Companion (included for paid Zoom users) has pressured the consumer segment, pushing Otter toward enterprise with admin controls and compliance features.

**Zoom AI Companion**: structurally advantaged — no bot needed. Zoom captures audio internally and passes it directly to its ASR pipeline without SDK round-trips or bot join latency. Included at no additional cost with paid Zoom plans. The key limitation: meeting transcripts are siloed inside Zoom's ecosystem. Integration with external tools (Notion, Linear, Slack) requires Zoom Apps SDK wrappers that introduce additional latency and OAuth complexity that third-party tools do not face.

**Microsoft Copilot in Teams**: similar native-access advantage as Zoom. Uses Azure OpenAI for summarization. Has explicit enterprise contract provisions covering data residency (EU data stays in EU) and data not being used for model training — a critical enterprise procurement requirement that Fireflies and Otter cannot match without custom DPA negotiations.

---

## 7. Technologies & Tools

### STT Engine Comparison

Vendor list prices as of July 2026 (verify before committing — this market re-prices
often, and WER varies enough by domain that vendor-published WER should be treated
as a starting point, not a ranking).

| Engine | Real-time factor | Cost/audio-min | Streaming | Diarization built-in |
|---|---|---|---|---|
| Whisper large-v3 (self-hosted A10G) | 50x | $0.00043 marginal / $0.00085 provisioned | No (needs chunking) | No (needs pyannote) |
| AssemblyAI Universal-3.5 Pro | Real-time | $0.0035 pre-recorded ($0.21/hr), $0.0075 streaming ($0.45/hr) | Yes | Yes |
| AssemblyAI Universal-2 (legacy) | Real-time | $0.0025 ($0.15/hr) | Yes | Yes |
| Deepgram Nova-3 (monolingual) | Real-time | $0.0048 streaming | Yes | Yes |
| Deepgram Flux (English) | Real-time | $0.0065 streaming | Yes | Yes |
| OpenAI gpt-4o-transcribe / whisper-1 | Batch | $0.006 ($0.003 for gpt-4o-mini-transcribe) | Limited | No |
| Google STT / Azure AI Speech | Real-time | Region- and tier-dependent; read the vendor calculator | Yes | Yes |

Deepgram Nova-2 and AssemblyAI's `best`/`nano`/SLAM-1 identifiers are legacy and
should not be used in new designs.

Whisper self-hosted wins on cost but requires building the streaming wrapper described in Section 4a. At 11.25M audio-hours/day, the cost difference between Whisper self-hosted ($570K/day provisioned) and the flagship managed option, AssemblyAI Universal-3.5 Pro (11.25M hr x $0.21/hr = $2.36M/day), is ~$1.8M/day — justifying a dedicated STT infrastructure team.

### Diarization

DER figures below are order-of-magnitude planning values, not vendor-published
benchmarks — DER is extremely sensitive to corpus, crosstalk rate, and audio
quality, so re-measure on your own labelled set before treating any of them as a
ranking.

| Tool | DER (2 speakers) | DER (4 speakers) | Latency | Notes |
|---|---|---|---|---|
| pyannote.audio 3.1 | 4.8% | 11.2% | 0.8s/chunk | Best open-source; requires GPU |
| AWS Transcribe diarization | 7.3% | 18.9% | Batch only | No streaming diarization |
| AssemblyAI diarization | 6.1% | 14.3% | Real-time | Bundled with STT; simpler ops |
| nemo MSDD | 5.2% | 9.7% | 1.2s/chunk | Best for 4+ speakers; heavier model |

---

## 8. Operational Playbook

### (a) Eval Pipeline

Three automated eval gates run on every model or prompt change before deployment.

**WER regression gate**: 100 internal test meetings with ground-truth human transcripts (collected with explicit participant consent under research protocol). Alert threshold: WER increase greater than 0.5 percentage points versus the previous production model version. Gate is blocking — deployment cannot proceed.

**Action item recall gate**: 200 hand-labeled meetings where a human annotator identified all action items with owner and task. Automated eval measures recall (fraction of ground-truth action items present in model output) and precision (fraction of model-output action items that are real). Target: recall >= 0.72, precision >= 0.68. Current production: recall 0.76, precision 0.71.

**Summary length and quality gate**: LLM-as-judge (a frontier model distinct from the summarizer — e.g. Claude Sonnet 5 judging gpt-5.4-nano output) scores summaries on a 5-point rubric: accuracy, brevity (penalizes summaries longer than 4 sentences), actionability. Gate blocks if mean score drops below 3.8/5.0.

See [./cross_cutting/llm_eval_harness_in_production.md](./cross_cutting/llm_eval_harness_in_production.md) for the full eval harness implementation including dataset versioning and result tracking.

### (b) Observability

OTel span hierarchy for a single meeting session:

```
meeting_session (trace root)
  span: bot_join            attrs: meeting_id, platform, join_latency_ms
  span: audio_ingestion     attrs: stream_bitrate, sample_rate, chunk_interval_ms
    span: vad_filter        attrs: speech_ratio, chunks_passed, chunks_dropped
    span: stt_chunk         attrs: chunk_duration_ms, wer_estimate, model_version
    span: diarization       attrs: speaker_count, der_estimate, embedding_cache_hit
    span: transcript_write  attrs: segment_count, store_latency_ms
  span: meeting_end_trigger attrs: total_duration_ms, segment_count
  span: summarizer          attrs: token_count_input, map_reduce_chunks, llm_model
    span: chunk_summarize   attrs: chunk_index, chunk_tokens, latency_ms (repeated)
    span: merge_summaries   attrs: input_chunks, output_tokens, latency_ms
  span: export_push         attrs: destination (slack/notion/linear), status_code
```

Key metrics (emitted as OTel metrics, dashboarded in Grafana):
- `stt.chunk.latency_p95_ms` — alert if > 3,000 ms
- `stt.wer_estimate` — alert if rolling average > 8% (degraded model or audio quality)
- `summarizer.latency_p95_ms` — alert if > 30,000 ms post-meeting
- `consent.audit_write_failure_rate` — alert on any failure (compliance critical)

See [./cross_cutting/opentelemetry_for_llm_apps.md](./cross_cutting/opentelemetry_for_llm_apps.md) for the full OTel SDK setup including prompt token counting and LLM span conventions.

### (c) Incident Runbooks

**Runbook 1: STT_DEGRADED — WER spikes above 15%**
- Symptom: `stt.wer_estimate` metric exceeds 0.15 on rolling 10-minute average; user reports of garbled transcription spike in support queue.
- Diagnosis: check STT cluster GPU utilization (>90% means queue backup and stale audio); check if new Whisper model version was deployed in the last 2 hours; check input audio quality metrics (silence ratio, sample rate mismatch).
- Mitigation: if queue backup, scale out STT node group (auto-scaling should have triggered; manually invoke if not). If model regression, roll back to previous pinned model version via model registry tag.
- Resolution: capture 10 anonymized audio samples from the degraded period; run against both model versions offline; file regression report; re-run WER eval gate before re-promoting new version.

**Runbook 2: BOT_JOIN_FAILURE — bot fails to join scheduled meeting**
- Symptom: `bot_join.failure_rate` exceeds 2% over 5 minutes; automated alert fires 10 minutes before meeting start for affected users.
- Diagnosis: check Zoom API status page; check if meeting host is on a Zoom plan that allows external apps (some Enterprise plans require IT allowlisting); check if meeting was cancelled or rescheduled after our calendar sync.
- Mitigation: send user a pre-meeting email with a manual upload link (upload recording after meeting ends for post-hoc transcription). Do not silently fail — the user must know the bot is not joining.
- Resolution: if Zoom API issue, wait for upstream resolution. If policy issue, route user to enterprise IT contact guide in help docs.

**Runbook 3: SUMMARIZER_TIMEOUT — LLM takes more than 30 seconds post-meeting**
- Symptom: `summarizer.latency_p95_ms` exceeds 30,000 ms; users report no summary email after meeting ends.
- Diagnosis: check LLM provider status (OpenAI / Anthropic status pages); check if meeting was unusually long (>2 hours triggers more map-reduce chunks); check token budget — very long transcripts may exhaust context window and retry.
- Mitigation: send user a partial result email ("Your meeting notes are being processed — action items will arrive within the next 5 minutes") immediately at meeting end. Retry summarizer job with exponential backoff (30s, 60s, 120s).
- Resolution: if LLM provider outage, fail over to the secondary provider's small tier (Claude Haiku 4.5 as backup for gpt-5.4-nano, or vice versa). If long-meeting timeout, add more aggressive chunking at >4-hour meetings.

**Runbook 4: CONSENT_AUDIT_FAILURE — consent log write fails for an active meeting**
- Symptom: `consent.audit_write_failure_rate` > 0; S3 write errors in CloudWatch for consent bucket.
- Diagnosis: check S3 bucket status and IAM permissions; check if Object Lock policy is blocking overwrites; check network connectivity from consent-manager pod to S3 endpoint.
- Mitigation: immediately pause transcription for the affected meeting (halt audio ingestion). Do not continue transcribing without a consent record — regulatory exposure outweighs the product impact. Buffer consent events in-memory and flush to S3 on recovery.
- Resolution: restore S3 connectivity; flush buffered consent events; resume transcription only after confirming consent log integrity. Escalate to legal if consent gap exceeds 60 seconds of active transcription.

---

## 9. Common Pitfalls & War Stories

**Pitfall 1: Whisper hallucinations on silence**
Whisper large-v3 generates text even when fed pure silence or non-speech audio (applause, keyboard typing, HVAC noise). In production the model outputs phrases like "Thank you for watching" or "Subtitle by" — artifacts from YouTube training data — into meeting transcripts. This is a well-documented Whisper failure mode; the exact rate depends heavily on the silence and noise profile of your audio, so measure it on your own corpus rather than importing a published number. The fix is mandatory VAD (Voice Activity Detection) gating before every STT call: Silero VAD (a ~1.2-2.3 MB model depending on the exported format — `silero_vad.jit` is 2.27 MB, `silero_vad.onnx` 2.33 MB, the 16 kHz safetensors build 1.24 MB — runs on CPU) drops non-speech audio before it reaches Whisper. Hallucination rate after VAD gating should be tracked as an explicit eval metric, not assumed.

**Pitfall 2: Enterprise IT blocks bots**
*(Illustrative composite of a pattern every bot-in-meeting vendor hits; the percentages below are modelling assumptions, not published figures.)* A meaningful share of large enterprises configure Zoom to block all external app participants — Zoom Enterprise plans let IT admins run an allowlist for participant apps. The bot join then fails silently: the user sees no error, just no transcript. Impact: churn among enterprise trial users who set the product up, found it "didn't work," and left before understanding the IT configuration requirement. The standard resolution is a bot pre-check that tests join permission 24 hours before the first meeting and emails an IT configuration guide if the check fails, plus an email-forwarding fallback path that bypasses the bot-join restriction entirely.

**Pitfall 3: Speaker diarization collapse in brainstorming sessions**
A meeting with 3 or more participants who frequently interrupt and speak simultaneously is the diarization model's worst case. In cross-talk-heavy meetings (design reviews, brainstorming, engineering standups), pyannote.audio's DER rises from the baseline 5% to 35%+. The practical effect: action items are attributed to the wrong person. An action item attributed to the wrong owner in a shared Slack export is a team-trust failure. The mitigation for bot-in-meeting mode is voice enrollment: during user onboarding, each participant records a 10-second voice sample used to pre-populate speaker embeddings. Enrollment materially reduces DER even in high-crosstalk conditions, because the clustering step is seeded with known-good embeddings rather than discovering speakers blind. Track enrollment completion as a retention driver in your own funnel — the correlation is strong but the magnitude is product-specific, so do not import someone else's number.

**Pitfall 4: Cross-tenant transcript data exposure**
*(Illustrative composite — the figures below are modelled, not a verified public enforcement record.)* A misconfigured S3 bucket policy allows tenant A's S3 IAM role to list and read objects in tenant B's transcript prefix. The bug was introduced when an infrastructure engineer copy-pasted a bucket policy template and forgot to scope the Principal to the correct IAM role ARN. Approximately 2,000 meetings across 14 enterprise workspaces were readable cross-tenant for 11 days before discovery during a routine access review. A breach of this shape is a GDPR Article 33 notifiable incident and, in the modelled scenario, draws a six-figure EUR fine. The fix: per-tenant S3 prefixes are not sufficient isolation — each tenant must have a dedicated KMS key, and S3 bucket policies must be generated programmatically from a template that parameterizes the tenant IAM role ARN rather than copied manually. Automated policy drift detection via AWS Config rules now runs every 6 hours.

See [./cross_cutting/tenant_isolation_patterns.md](./cross_cutting/tenant_isolation_patterns.md) for the full per-tenant KMS + S3 isolation pattern with policy templates.

**Pitfall 5: Summary length kills adoption**
*(Illustrative composite; the activation percentages are modelled to show the shape of the effect.)* A first-generation meeting summarizer produces summaries averaging 600 words — a "comprehensive" output that product believed users would value. 30-day activation rate (user opens and reads the summary) was 11%. A user research session revealed that users opened the summary email, saw two paragraphs with sub-bullets, and closed it — they did not have 3 minutes to read a summary of a meeting they just attended. The redesign enforced hard constraints: executive summary capped at 3 sentences, action items limited to 7 maximum (truncated with "and N more"), open questions limited to 3. Activation rate increased to 67% within two weeks of the redesign. The lesson: LLM comprehensiveness is an anti-pattern for consumption. Constraints enforced at the prompt level ("return exactly 3 sentences") outperform post-hoc truncation because the model actively selects the 3 most important sentences rather than truncating a longer output.

---

## 10. Capacity Planning

### Primary Bottleneck: STT GPU Throughput

The GPU fleet is the binding constraint and the dominant cost. All other components (Postgres, S3, LLM API) scale horizontally with negligible marginal cost relative to GPU spend.

**Scaling formula:**

```
required_gpus = ceil(
    peak_concurrent_streams
    / realtime_factor
    / target_utilization
)

Where:
  peak_concurrent_streams = DAU x avg_meetings_per_day x avg_duration_hours
                            / hours_per_day x peak_multiplier
  realtime_factor         = GPU throughput in multiples of real-time (50 for Whisper on A10G)
  target_utilization      = 0.70 (leave 30% headroom for spikes)
```

**Worked example at 5M DAU:**

```
peak_concurrent_streams:
  5,000,000 DAU
  x 3 meetings/day
  x 0.75 hours/meeting
  / 24 hours/day
  x 2.5 peak multiplier
  = 1,171,875 concurrent streams

required_gpus:
  1,171,875 / 50 / 0.70 = 33,482.14 -> ceil = 33,483 A10Gs

GPU cost (A10G, us-east-1, $1.30/GPU-hour on-demand planning rate):
  Peak 8 hours: 33,483 x $1.30 x 8 = $348,223
  Off-peak 16 hours at 30% load: 33,483 x 0.30 x $1.30 x 16 = $208,934
  Daily GPU cost: $557,157

At 15M meetings/day, LLM summarization (gpt-5.4-nano, $0.20/$1.25 per M):
  output: 15M x 800 / 1M x $1.25   = $15,000/day
  input:  15M x 16,000 / 1M x $0.20 = $48,000/day
  LLM subtotal: $63,000/day

Total daily infra cost: $557,157 (GPU) + $63,000 (LLM) + ~$50,000 (DB, S3, network)
  = ~$670,000/day

Break-even at $10/user/month:
  $670,000/day x 30 = $20.1M/month
  $20.1M / $10 = 2.01M paying users required to cover infra alone
  At 5M DAU: implies ~40% paid conversion required
  Typical freemium conversion in this category is low single-digit to
  high single-digit percent -- an order of magnitude short
  -> Self-hosted Whisper is necessary but not sufficient; additional cost reduction
     via quantization (INT8 Whisper) can push rt-factor to 80x, reducing GPU count by 38%
```

See [./cross_cutting/gpu_pool_economics.md](./cross_cutting/gpu_pool_economics.md) for the full GPU pool economics model including spot vs on-demand mix optimization, reserved instance break-even analysis, and INT8 quantization throughput benchmarks.

---

## 11. Interview Discussion Points

**Q: Why does local-process beat bot-in-meeting for privacy, even though both use cloud LLMs?**
The privacy distinction is about what data reaches the cloud. In bot-in-meeting, raw audio — containing voice biometrics, emotional tone, and potentially off-topic conversation — is streamed to a cloud STT service. In local-process (Granola model), audio never leaves the device. Only the text transcript is sent to the LLM API. Text carries far less regulatory exposure than voice biometric data under GDPR, CCPA, and Illinois BIPA. From an enterprise legal review perspective, "we send text to an LLM" is a qualitatively different data processing claim than "we stream audio to our servers." In regulated verticals this distinction is frequently the difference between a deal closing and a security review blocking it.

**Q: How does the sliding-window Whisper approach avoid word-boundary artifacts?**
Whisper is a sequence-to-sequence model whose attention spans the entire input. When audio is chunked into hard non-overlapping segments, a word that begins 4.9 seconds into a 5-second chunk has only 0.1 seconds of acoustic context after it — insufficient for the model to resolve phonemes correctly. The sliding window keeps a 30-second audio buffer that advances by 5 seconds per inference. Every word is therefore transcribed with up to 25 seconds of prior acoustic context, which is sufficient for accurate phoneme resolution. The deduplication cursor (`_last_committed_ms`) ensures each word is emitted exactly once despite multiple overlapping windows covering it.

**Q: Why does self-hosted STT cost so much less than a managed API at scale?**
The gap is roughly 7x at the scale analyzed, not the order-of-magnitude figure people quote: $570K/day self-hosted versus $4.05M/day on a managed API. The mechanism is GPU utilization density. Whisper on a self-hosted A10G achieves 50x real-time factor — one GPU handles 50 simultaneous audio streams. A managed provider charges $0.006/audio-minute (OpenAI whisper-1 / gpt-4o-transcribe) and carries similar GPU costs but adds margin, reliability infrastructure, and per-request overhead. At 11.25M audio-hours/day the differential compounds to ~$3.5M/day. Two numbers get conflated here and should not be: the *marginal* self-hosted rate at full GPU utilization (~$0.00043/audio-min) and the *provisioned* rate you actually pay after a 70% utilization target and a peak/off-peak fleet profile (~$0.00085/audio-min). Budget against the provisioned rate. The crossover where self-hosting beats API on TCO — including the devops headcount the API price is really buying you — is in the low hundreds of thousands of audio-hours per month.

**Q: How does map-reduce handle a 2-hour meeting transcript?**
It splits the transcript on topic boundaries, summarizes each segment in parallel, then reconciles the segment summaries in one final merge call. A 2-hour meeting produces approximately 24,000 words — 32,000 tokens — which exceeds reliable action item extraction with a single LLM call even in 128K-context models (context length is not the binding constraint; attention dilution over long contexts is). Map-reduce segments the transcript by topic using sentence embedding cosine similarity drops as boundary signals. Each segment (typically 800-1,500 tokens) is summarized independently in parallel. The parallel chunk summaries are then merged in a final LLM call that reconciles action items across segments and resolves speaker references. The merge step is the quality bottleneck: if the same action item appears in two chunks under different phrasings, the merge must deduplicate it. Addressing this with a "seen action items" set passed into the merge prompt reduces duplicate action items by 73% in internal testing.

**Q: How is GDPR consent compliance architected to withstand regulatory audit?**
Four properties are required: (1) Consent is captured before transcription begins — the bot announcement and 60-second grace period must precede the first STT call. (2) Each consent event is written to an immutable audit log (S3 Object Lock, WORM mode, 7-year retention) before transcription continues. (3) Opt-out is honored within 60 seconds — the consent cache is checked per audio chunk, and chunks for opted-out participants are dropped before VAD and STT. (4) Consent records use SHA-256 hashed email addresses to minimize PII in the audit log itself while remaining linkable to a participant identity during regulatory review via a separately secured lookup table.

**Q: Why is speaker diarization harder in bot-in-meeting mode than local-process?**
In bot-in-meeting mode, all participants' audio arrives mixed in a single channel. The diarizer must perform blind source separation — distinguishing N voices from one stream using only acoustic embedding differences. When speakers overlap, embedding clustering fails. In local-process mode on macOS, Core Audio process taps (`AudioHardwareCreateProcessTap`, macOS 14.2+) provide separate audio taps per process, and ScreenCaptureKit (macOS 12.3+) provides per-application capture: the Zoom process audio (remote participants) and the local microphone (host) are already separated. This means the host is always correctly identified as a distinct channel, and remote participants arrive in a single channel rather than a mix of all participants including the host. The effective diarization problem reduces from N-speaker mixed to (N-1)-speaker mixed — a meaningful reduction in problem complexity.

**Q: How do you handle bot join failure gracefully without the user losing their meeting?**
A complete bot join failure must be surfaced to the user before the meeting starts, not discovered afterward. The AutoJoiner performs a pre-flight check 10 minutes before the meeting: it attempts to obtain a Zoom join token and validates that the meeting's host settings allow external participants. If this check fails, the user receives a push notification and email with: (1) the reason for failure in plain language, (2) a meeting ID and instructions for manually uploading a recording afterward, (3) an IT configuration guide if the failure is due to organization policy. The manual upload path uses the same STT and summarization pipeline as real-time capture — users get a complete summary within 5 minutes of upload. Approximately 8% of enterprise users end up using the upload path regularly as a deliberate privacy choice.

**Q: What makes action item extraction harder than meeting summarization?**
Summarization is a compression task — reduce N words to M words while preserving the salient information. Action item extraction is a structured information extraction task with semantic ambiguity. The model must: (1) distinguish a committed action ("I will send the proposal by Friday") from a conditional or hypothetical ("we might want to send a proposal"), (2) identify the owner when the action is stated in passive voice ("the proposal should be sent"), (3) resolve pronoun references across the transcript ("John said he'd handle it" requires knowing what "it" refers to from 3 minutes earlier), and (4) assign a due date from relative time references ("by end of quarter" requires knowing the current date). Each of these sub-tasks has its own failure mode. In practice, owner identification is the hardest: in meetings with absent stakeholders ("I'll check with Sarah"), the owner is someone not present whose name must be preserved verbatim.

**Q: How durable is the local-process moat, given that OS audio APIs keep improving?**
It is a real but decaying moat, and the decay is the interesting part. The initial advantage was genuine: macOS shipped Core Audio process taps (macOS 14.2) and per-application capture via ScreenCaptureKit (macOS 12.3), so a native Mac app could separate the conferencing app's audio from the local microphone without joining the meeting as a bot. Competitors could not replicate that without shipping a native client on every platform and navigating each OS's audio stack (macOS ScreenCaptureKit and Core Audio taps, Windows WASAPI loopback, Android AudioRecord) with different permission models each. But the moat is engineering effort, not a patent: Granola itself has since shipped Windows, iOS, and Android clients, which demonstrates the port is tractable. What persists is the *privacy claim* the architecture licenses — "your audio never leaves your device" — which is a procurement requirement in healthcare, legal, and financial services that bot-in-meeting competitors cannot credibly make regardless of platform coverage. Design for the privacy posture, not for the OS exclusivity.

**Q: How do you measure STT quality in production without ground-truth transcripts?**
Use three unsupervised proxies that need no reference transcript. (1) Speaker turn consistency: if a segment is attributed to Speaker A but the previous 5 segments were all Speaker B with no pause longer than 200ms, the diarizer likely mislabeled. A high rate of single-segment speaker switches is a diarization degradation signal. (2) Whisper confidence scores: Whisper's log-probabilities per token are available when running with `logprobs=True`. A rolling average log-prob below -0.8 per token indicates model uncertainty — typically triggered by noise, non-English speech, or audio quality issues — without requiring a ground-truth reference. (3) Action item downstream proxy: if the fraction of meetings where the user edits or deletes AI-generated action items exceeds a threshold (typically 35%), this is a leading indicator of transcript quality degradation upstream. This last metric has a 2-hour lag but is the most direct signal that output quality has degraded in a way that matters to users.
