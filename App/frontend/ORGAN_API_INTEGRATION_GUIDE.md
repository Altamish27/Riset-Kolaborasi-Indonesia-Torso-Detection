# Removal of Hardcoded Organ Data - Integration Guide

## Overview

Semua hardcoded data organ (Jantung, Paru-paru, Hati, Lambung, Usus, Ginjal, Sistem Syaraf) telah dihapus dari aplikasi. Data sekarang akan diambil 100% dari API backend.

**Status:** Database Room sekarang empty pada awal. Data dimuat dari API saat aplikasi startup.

---

## Changes Made

### 1. **AnatomyDatabase.kt** - Removed Prepopulation
- ❌ Dihapus: `PrepopulateCallback` class
- ❌ Dihapus: `getInitialOrgans()` function dengan semua hardcoded data
- ✅ Database sekarang murni untuk penyimpanan, tanpa data initial

**Before:**
```kotlin
.addCallback(PrepopulateCallback())  // Hard-coded 7 organs
```

**After:**
```kotlin
// No callback - database is empty initially
.build()
```

### 2. **OrganService.kt** (NEW) - Fetch from API
- ✅ Dibuat: Service untuk fetch organ data dari API
- ✅ Method: `syncOrgansFromAPI()` - fetch dan cache ke database
- ✅ Method: `getOrgansFromDB()` - baca dari local database
- ✅ Method: `getOrganByName()` - search organ by name

**Location:** `app/src/main/java/com/anatomy/app/services/OrganService.kt`

### 3. **AnatomyApp.kt** - API Sync on Startup
- ✅ Dimodifikasi: Menambahkan `OrganService` initialization
- ✅ Menambahkan background sync dari API saat app start
- ✅ Non-blocking: App tetap responsive selama fetch data

**Before:**
```kotlin
database = AnatomyDatabase.getInstance(this)
```

**After:**
```kotlin
database = AnatomyDatabase.getInstance(this)
organService = OrganService(this)
// Background sync in separate coroutine
CoroutineScope(Dispatchers.Default).launch {
    syncOrgansFromAPI()
}
```

---

## Implementation Steps for Backend Integration

### Step 1: Create Backend API Endpoint

Buat endpoint di backend (Django) yang mengembalikan daftar organ:

**Endpoint:** `GET /organs` atau `GET /api/organs`

**Response Format:**
```json
{
  "organs": [
    {
      "name": "Jantung",
      "short_description": "Organ pompa darah utama",
      "long_description": "Jantung adalah organ pompa utama..."
    },
    {
      "name": "Paru-paru",
      "short_description": "Organ pernapasan utama",
      "long_description": "Paru-paru adalah sepasang organ..."
    },
    ...
  ]
}
```

### Step 2: Update API Models (ApiModels.kt)

Tambahkan model untuk organ data:

```kotlin
@kotlinx.serialization.Serializable
data class OrganListResponse(
    val organs: List<OrganDTO> = emptyList()
)

@kotlinx.serialization.Serializable
data class OrganDTO(
    val name: String,
    val short_description: String,
    val long_description: String
) {
    fun toOrganEntity() = OrganEntity(
        name = name,
        short_description = short_description,
        long_description = long_description
    )
}
```

**Location:** `app/src/main/java/com/anatomy/app/network/ApiModels.kt`

### Step 3: Update ApiService Interface

Tambahkan endpoint ke ApiService:

```kotlin
interface ApiService {
    // ... existing endpoints ...
    
    @GET("/organs")  // Sesuaikan path dengan backend Anda
    suspend fun getOrgans(): OrganListResponse
}
```

**Location:** `app/src/main/java/com/anatomy/app/network/ApiService.kt`

### Step 4: Complete OrganService Implementation

Uncomment dan lengkapi method `syncOrgansFromAPI()` di OrganService.kt:

```kotlin
suspend fun syncOrgansFromAPI(): Boolean {
    return try {
        Log.d(TAG, "Starting organ sync from API...")
        
        val response = apiService.getOrgans()  // Call API
        val organs = response.organs.map { it.toOrganEntity() }
        organDao.insertAll(organs)  // Store in database
        
        Log.d(TAG, "Successfully synced ${organs.size} organs")
        true
        
    } catch (e: Exception) {
        Log.e(TAG, "Error syncing organs from API", e)
        false
    }
}
```

---

## Verification Checklist

- [ ] Backend API endpoint `/organs` created and tested
- [ ] API returns organ data in correct JSON format
- [ ] OrganDTO and OrganListResponse models added to ApiModels.kt
- [ ] ApiService.getOrgans() endpoint added
- [ ] OrganService.syncOrgansFromAPI() implemented
- [ ] App compiles without errors
- [ ] App launches and fetches organ data from API
- [ ] Organs appear in database after sync
- [ ] No hardcoded data remains in any source files
- [ ] Verify with: `grep -r "Jantung\|Paru-paru\|Hati" app/src/main/java/`

---

## Data Flow Diagram

```
┌─────────────────────────────────────────────────────────┐
│         App Startup (AnatomyApp.onCreate)              │
└───────────────────┬─────────────────────────────────────┘
                    │
                    ▼
        ┌──────────────────────┐
        │  Initialize Services │
        │  (Audio, Haptic, DB) │
        └──────────┬───────────┘
                   │
                   ▼
        ┌──────────────────────┐
        │  Launch Background   │
        │  Coroutine: Sync     │
        │  Organs from API     │
        └──────────┬───────────┘
                   │
                   ▼
        ┌──────────────────────────┐
        │  OrganService.syncOrgans │
        │  FromAPI()               │
        └──────────┬───────────────┘
                   │
                   ▼
        ┌──────────────────────────┐
        │  ApiService.getOrgans()  │
        │  (Call Backend API)      │
        └──────────┬───────────────┘
                   │
                   ▼
        ┌──────────────────────────────────┐
        │  Parse JSON Response             │
        │  Convert DTO → OrganEntity       │
        └──────────┬───────────────────────┘
                   │
                   ▼
        ┌──────────────────────────────────┐
        │  Save to Room Database           │
        │  (organDao.insertAll())          │
        └──────────────────────────────────┘
```

## Usage in App

Untuk mengakses organ data di UI:

```kotlin
// In ViewModel or composable:
val organService = OrganService(context)

// Get all organs
val organs = organService.getOrgansFromDB()

// Get specific organ
val jantung = organService.getOrganByName("Jantung")
```

---

## Benefits of This Approach

✅ **No Hardcoded Data** - Semua data di maintain di backend  
✅ **Easy Updates** - Ubah deskripsi organ di backend tanpa rebuild app  
✅ **Multi-Language Support** - Backend bisa serve deskripsi dalam berbagai bahasa  
✅ **Offline Support** - Data cached di local database setelah first sync  
✅ **Background Loading** - App tidak freeze saat fetch data  
✅ **Type-Safe** - Kotlin serialization dengan model yang jelas  

---

## Troubleshooting

### App crashes with "null pointer" on OrganService
**Cause:** Backend endpoint belum implement  
**Fix:** Implement `/organs` endpoint di backend terlebih dahulu

### No organs appear in database after sync
**Solution:**  
1. Check logcat: `adb logcat | grep OrganService`
2. Verify API endpoint returns correct JSON format
3. Verify network permissions di AndroidManifest.xml

### API call timeout
**Solution:**
1. Increase timeout di HttpClientFactory
2. Check network connectivity
3. Verify backend server is running

---

## Next Steps

1. **Implement Backend Endpoint**: Buat REST endpoint untuk mengembalikan organ list
2. **Complete API Integration**: Update ApiService dan OrganService
3. **Test Integration**: Jalankan app dan verify data loading
4. **Add Error Handling**: Handle network errors gracefully di UI
5. **Add Pull-to-Refresh**: Optional - allow user manual sync
