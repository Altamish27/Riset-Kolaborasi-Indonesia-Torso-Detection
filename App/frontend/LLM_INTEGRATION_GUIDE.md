# LLM Integration Guide - Organ Explanation via AI

## Overview

Aplikasi sekarang menggunakan backend LLM yang sudah di-deploy untuk memberikan penjelasan organ secara dinamis. Tidak ada lagi hardcoded penjelasan - semua dikambil dari AI backend.

**Status:** ✅ Frontend siap untuk menerima penjelasan dari LLM

---

## Architecture Flow

```
┌─────────────────────────────────────────────────────────────┐
│                    User Points Camera                        │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
        ┌──────────────────────────┐
        │  TFLite Model Detection  │
        │  best_model_torso_float  │
        │  32.tflite               │
        └────────────┬─────────────┘
                     │
                     ▼ (1.5 sec hold)
        ┌──────────────────────────┐
        │    Organ Detected        │
        │  e.g. "Jantung"          │
        └────────────┬─────────────┘
                     │
                     ▼
        ┌──────────────────────────────────────┐
        │  TTS Voice Prompt:                   │
        │  "Terdeteksi Jantung.                │
        │   Mau dengar penjelasan lanjut?"     │
        └────────────┬─────────────────────────┘
                     │
                     ▼
        ┌──────────────────────────┐
        │  Voice Recognition       │
        │  Listen for "Ya/Tidak"   │
        └────────────┬─────────────┘
                     │
         ┌───────────┴───────────┐
         │ User says "Ya"        │
         └───────────┬───────────┘
                     │
                     ▼
        ┌──────────────────────────────────┐
        │  Call Backend LLM Endpoint       │
        │  POST /llm/explain_organ         │
        │  { organ_name: "Jantung" }       │
        └────────────┬─────────────────────┘
                     │
                     ▼
        ┌────────────────────────────────────────┐
        │  LLM Backend (Already Deployed)        │
        │  Generates Explanation via AI          │
        │  (uses Claude, Gemini, or custom LLM)  │
        └────────────┬─────────────────────────┘
                     │
                     ▼
        ┌────────────────────────────────────────┐
        │  API Response                          │
        │ {                                      │
        │   organ_name: "Jantung",               │
        │   explanation: "Jantung adalah...",    │
        │   thinking: "(optional)"               │
        │ }                                      │
        └────────────┬─────────────────────────┘
                     │
                     ▼
        ┌────────────────────────────────────────┐
        │  Display LLMExplanationPopupSheet      │
        │  Show organ name + explanation         │
        └────────────┬─────────────────────────┘
                     │
                     ▼
        ┌────────────────────────────────────────┐
        │  Text-to-Speech (TTS)                  │
        │  Read explanation aloud                │
        │  Lang: Indonesian by default           │
        └────────────────────────────────────────┘
```

---

## Implementation Details

### 1. **API Models** (`ApiModels.kt`)

Request dan response models untuk LLM communication:

```kotlin
@Serializable
data class LLMExplanationRequest(
    val organ_name: String,
    val language: String = "id"  // "id" atau "en"
)

@Serializable
data class LLMExplanationResponse(
    val organ_name: String? = null,
    val explanation: String? = null,
    val short_description: String? = null,
    val error: String? = null,
    val thinking: String? = null  // LLM thinking process
)
```

### 2. **API Endpoint** (`ApiService.kt`)

```kotlin
@POST("/llm/explain_organ")
suspend fun getOrganExplanation(@Body request: LLMExplanationRequest): LLMExplanationResponse
```

**Endpoint Configuration:**
- Path: `/llm/explain_organ` (adjust jika backend punya path berbeda)
- Method: POST
- Content-Type: application/json
- Authorization: Bearer token (via HttpClientFactory interceptor)

### 3. **LLM Service** (`services/LLMService.kt`)

Service untuk handle komunikasi dengan backend LLM:

```kotlin
val llmService = LLMService(context)

// Get explanation
val response = llmService.getOrganExplanation(
    organName = "Jantung",
    language = "id"  // Indonesian
)

// Get explanation text directly
val text = llmService.getExplanationText("Jantung")
```

**Methods:**
- `getOrganExplanation()` - Call API dan return response
- `getExplanationText()` - Get explanation text dengan fallback
- `isValidOrganName()` - Validate input

### 4. **UI Components**

#### `ScanAnatomyScreen` (Updated)
- Mendeteksi organ via TFLite
- Ask user confirmation
- Request penjelasan dari LLM saat user say "Ya"
- Display explanation di popup

**Key Changes:**
- Removed: Prefetching dari local database
- Added: `LLMService` initialization
- Added: `isLoadingExplanation` state
- Added: Background coroutine untuk LLM request

#### `LLMExplanationPopupSheet` (New)
- Display AI-generated explanation
- Centered organ name header
- Scrollable explanation text
- AI marker badge

```kotlin
LLMExplanationPopupSheet(
    organName = "Jantung",
    explanation = "Penjelasan dari LLM...",
    onDismiss = { /* close popup */ }
)
```

---

## Backend API Specification

### Endpoint: `/llm/explain_organ`

**Request:**
```json
{
  "organ_name": "Jantung",
  "language": "id"
}
```

**Response (Success):**
```json
{
  "organ_name": "Jantung",
  "explanation": "Jantung adalah organ pompa utama dalam sistem peredaran darah manusia...",
  "short_description": "Organ pompa darah utama",
  "thinking": "Generating explanation about Jantung..."
}
```

**Response (Error):**
```json
{
  "organ_name": "Jantung",
  "error": "Failed to generate explanation",
  "explanation": null
}
```

---

## Setup Checklist

- [x] **Frontend:** Add LLM API models (ApiModels.kt)
- [x] **Frontend:** Add LLM endpoint (ApiService.kt)
- [x] **Frontend:** Create LLMService
- [x] **Frontend:** Update ScanAnatomyScreen for LLM integration
- [x] **Frontend:** Create LLMExplanationPopupSheet
- [ ] **Backend:** Verify `/llm/explain_organ` endpoint exists
- [ ] **Backend:** Verify LLM integration (Claude, Gemini, etc.)
- [ ] **Backend:** Test endpoint with sample organ names
- [ ] **Backend:** Verify response format matches expected JSON
- [ ] **Test:** End-to-end: Detect → Confirm → Get explanation → Speak

---

## Testing

### 1. Manual Testing

1. Open app in Development mode
2. Navigate to "Mode Scan Anatomi"
3. Point camera at object that matches TFLite model
4. Wait 1.5 seconds for detection
5. Say "Ya" when prompted
6. Verify:
   - Loading spinner shows
   - LLM explanation received
   - Popup displays explanation
   - TTS reads explanation aloud

### 2. Debugging

Check Logcat for the following tags:
```bash
adb logcat | grep "LLMService\|ScanAnatomyScreen"
```

Expected logs:
```
D/LLMService: Requesting LLM explanation for: Jantung (language: id)
D/LLMService: LLM response received for Jantung
I/TTS: Speaking...explanation text...
```

### 3. Error Scenarios

**Scenario: Network Error**
- LLMService catches exception
- Returns error message: "Maaf, tidak dapat mendapatkan penjelasan..."
- User can try again after 2 seconds

**Scenario: Backend Returns null explanation**
- Check response has error field
- Show fallback message
- Reset scan state

**Scenario: Invalid organ name**
- LLMService validates organ name
- Returns false if invalid
- Prevents unnecessary API calls

---

## Customization

### Change LLM Endpoint Path

Edit `ApiService.kt`:
```kotlin
@POST("/your/custom/path")  // Change this
suspend fun getOrganExplanation(@Body request: LLMExplanationRequest): LLMExplanationResponse
```

### Change Request Language

In `ScanAnatomyScreen.kt`:
```kotlin
val explanation = withContext(Dispatchers.IO) {
    llmService.getExplanationText(
        organName = organ,
        language = "en"  // Change to "en" for English
    )
}
```

### Add Loading Indicator

Add to popup display logic:
```kotlin
if (isLoadingExplanation) {
    CircularProgressIndicator()  // Show while fetching
}
```

---

## Performance Considerations

### Network Time
- Typical LLM response: 1-3 seconds
- Status text shows "Meminta penjelasan dari AI..."
- User can wait or cancel

### Caching (Optional Enhancement)
Consider adding caching to avoid repeated LLM calls:
```kotlin
val explanationCache = mutableMapOf<String, String>()

suspend fun getOrganExplanation(organName: String): String {
    if (organName in explanationCache) {
        return explanationCache[organName]!!
    }
    // Call LLM...
    explanationCache[organName] = explanation
    return explanation
}
```

### Timeout Handling
Current timeout: Same as HTTP client default (usually 30 seconds)
Consider adding shorter timeout for user experience:
```kotlin
// In HttpClientFactory
.callTimeout(10, TimeUnit.SECONDS)
```

---

## Troubleshooting

| Issue | Cause | Solution |
|-------|-------|----------|
| App crashes after saying "Ya" | LLMService not initialized | Verify `remember { LLMService(context) }` |
| Black screen in popup | Explanation text empty | Check backend response format |
| No TTS sound | AudioAssistant not initialized | Verify AnatomyApp.onCreate() |
| Connection timeout | Backend not responding | Verify LLM endpoint is running |
| Wrong explanation | Organ name mismatch | Check TFLite-to-organ name mapping |

---

## Next Steps

1. **Verify Backend Deployment**
   - Confirm `/llm/explain_organ` endpoint responds
   - Test with curl/Postman

2. **Adjust Organ Names**
   - Ensure organ names from TFLite match backend expectations
   - Update MockObjectAnalyzer if needed

3. **Optimize Response Time**
   - Consider streaming response for long explanations
   - Add progress indicator during fetch

4. **Add Analytics**
   - Track which organs are most queried
   - Monitor average response time
   - Log errors for debugging

5. **Localization**
   - Support multiple languages (id, en, etc.)
   - Store language preference in AppConfig

---

## Architecture Diagram

```
╔════════════════════════════════════════════════════════════════╗
║                     FRONTEND (Android)                         ║
╠════════════════════════════════════════════════════════════════╣
║                                                                ║
║  ┌──────────────────────┐          ┌─────────────────────┐   ║
║  │ ScanAnatomyScreen    │          │ TFLite             │   ║
║  │ (Detection & Prompt) │◄────────│ Model              │   ║
║  └──────────┬───────────┘          │ (best_model_torso) │   ║
║             │                       └─────────────────────┘   ║
║             │ Ask "Ya/Tidak"                                  ║
║             │                                                 ║
║  ┌──────────▼───────────┐                                     ║
║  │ LLMService          │                                     ║
║  │ (Request LLM)       │                                     ║
║  └──────────┬───────────┘                                     ║
║             │ HTTP POST                                       ║
║             │ /llm/explain_organ                              ║
║             │                                                 ║
║  ┌──────────┴───────────────────────────────────────────┐    ║
║  │ HttpClientFactory (with Auth interceptor)           │    ║
║  └──────────┬──────────────────────────────────────────┘    ║
║             │ [org.name, language]                            ║
║             │                                                 ║
╠═════════════╪═════════════════════════════════════════════════╣
║             │                                                 ║
║             ►─────────────────────► HTTP/REST ───────────┐    ║
║                                                          │    ║
║  ┌──────────────────────────────────────────────────────▼─── ║
║  │ BACKEND (Django + LLM)                                   ║
║  │                                                          ║
║  │ ┌────────────────────────────────────┐                  ║
║  │ │ /llm/explain_organ endpoint        │                  ║
║  │ └────────────┬───────────────────────┘                  ║
║  │              │                                          ║
║  │ ┌────────────▼───────────────────────┐                  ║
║  │ │ LLM Integration (Claude/Gemini)   │                  ║
║  │ │ Generates explanation              │                  ║
║  │ └────────────┬───────────────────────┘                  ║
║  │              │                                          ║
║  │ ┌────────────▼──────────────────────────┐               ║
║  │ │ Response { explanation: "..." }      │               ║
║  │ └────────────┬──────────────────────────┘               ║
║  └─────────────┼──────────────────────────────────────────┘ ║
║                │                                            ║
║  ◄─────────────┘                                            ║
║                                                            ║
║  ┌────────────────────────┐      ┌─────────────────────┐  ║
║  │ LLMExplanationPopup    │      │ AudioAssistant      │  ║
║  │ (Display & Read)       │─────►│ (TTS Voice Output)  │  ║
║  └────────────────────────┘      └─────────────────────┘  ║
║                                                            ║
╚════════════════════════════════════════════════════════════════╝
```

---

## Summary

✅ **Fully implemented:** Frontend LLM integration  
🔗 **Connected to:** Backend LLM service (already deployed)  
🎯 **Flow:** Detect → Confirm → Request → Display → Speak  
📱 **No hardcoded explanations:** All from backend AI  

**Status: Ready for Backend Verification** ✓
