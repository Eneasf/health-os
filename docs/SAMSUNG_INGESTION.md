# Samsung Ingestion — Paths, Data Contract & Reconciliation Design

**Version:** 0.2 — **PROPOSAL, NOT APPROVED. Nothing here is implemented.**
**Scope note (v0.2):** originally a Health-Connect-only design. Health Connect was evaluated and rejected (§0.6). The document now covers **all candidate Samsung ingestion paths**, because Health Connect is not the only one and the collector question is *not* closed — see §0.8.
**Date:** 2026-08-27
**Relates to:** `docs/SYSTEM_DESIGN.md` (§2 ingestion, §4 dedup tiers), `docs/HANDOVER.md`

---

## 0. Discovery phase — do this before designing anything further

**Status: this section is the current work. Everything from §1 onward is provisional until it completes.**

The documentation describes what the Health Connect API *can* carry. It says nothing about what the apps on this specific phone actually write. Every open question below is empirically answerable in an afternoon, and several of them change the design materially.

Known connected apps on device: **Samsung Health**, **eGym**, **MyFitnessPal**. There may be others — enumerate them first.

### 0.1 Step one — Health Connect Toolbox (~30 min, no code)

Google ships a developer tool that reads and writes Health Connect directly. Install it via `adb` and use it to answer the **binary** questions before writing a line of Kotlin:

- Does eGym data appear in Health Connect *at all*?
- Does MyFitnessPal nutrition appear?
- Is anything writing `RestingHeartRateRecord` or HRV?

If the answer to a question is "nothing there", that branch of the design dies immediately and cheaply. The Toolbox is an interactive spot-check tool, though — it will not produce a census, group by writing app, or export machine-readable output. That is what §0.2 is for.

### 0.2 Step two — the probe app

A **throwaway, read-only audit instrument**. Not a prototype collector. Its only job is to dump what Health Connect will actually give this phone, so §3's contract rests on evidence instead of documentation.

Fork `android/health-samples` (the official `HealthConnectSample`) rather than starting cold — it already has the permission plumbing and Gradle setup.

**Hard requirements**

| Requirement | Why |
|---|---|
| **Read-only.** No insert, update, or delete calls to Health Connect, ever. | An audit tool that mutates the thing it audits is worthless, and a write bug would corrupt the real source. |
| Request every read permission the contract might need; **record granted vs denied** | The denial list is itself a finding. |
| Capture **full `Metadata`** per record: `id`, `dataOrigin.packageName`, `device`, `lastModifiedTime`, `clientRecordId`, `recordingMethod` | `dataOrigin` is the duplication detector (G7). `recordingMethod` distinguishes measured from manually-entered. |
| Emit a **field-presence census**, not just sample records | Same technique used to audit the SQLite DB: for each record type, count records and count non-null per field. This is what turns "nutrition might sync" into a number. |
| Group all counts **by `dataOrigin.packageName`** | Reveals whether the same fact arrives from two apps. |
| **Probe the history boundary explicitly** — attempt reads at 7 / 45 / 400 days and record success or the exact exception | Confirms `READ_HEALTH_DATA_HISTORY` behaviour on this device rather than trusting the docs. |
| Output **one JSON file** via the share sheet or SAF save. No network, no background service, no cloud. | Keeps the probe trivially auditable and leaks nothing. |
| One-shot manual trigger. A button and a status line. | Any more UI and it starts becoming an app. |

**Explicitly out of scope** — these belong to the real collector, not the probe: writing to Health Connect, changes-token subscription, encryption, the sync folder, and any mapping onto the tables in §3.

> ⚠️ **Kill the probe when discovery ends.** A probe that grows features becomes the collector by accident — and inherits none of the envelope, dedup, or delete-path design in §2–§6. Build the collector fresh from this document.

**Target output shape**

```
NutritionRecord — 47 records, 2 origins, window 2026-07-28..2026-08-27
  origins:  com.myfitnesspal.android 47   com.sec.android.app.shealth 0
  fields:   name 47/47   mealType 47/47   energy 47/47   protein 47/47
            totalFat 47/47   sugar 0/47   sodium 12/47
  recordingMethod: MANUALLY_ENTERED 47

ExerciseSessionRecord — 18 records, 2 origins
  origins:  com.egym.app 6   com.sec.android.app.shealth 12
  ⚠ 3 sessions overlap in time across origins -> probable duplication
```

### 0.3 Questions the probe must answer

| # | Question | What it decides |
|---|---|---|
| Q1 | Does MyFitnessPal write `NutritionRecord` directly? | Resolves **G3**. If yes, nutrition streams without Samsung as relay. |
| Q2 | Per-item or daily rollup? Which nutrient fields populated? | Whether `food_log_items` (822 rows) survives or collapses into `daily_nutrition` only. |
| Q3 | Does eGym write `ExerciseSessionRecord`? With calories/HR, or bare start/end? | How much of the session skeleton comes free (see §3.6). |
| Q4 | Do the same meal / session appear from two `dataOrigin` packages? | Severity of **G7**. Drives the dedup key. |
| Q5 | Does Samsung *also* write nutrition when MFP is connected? | Whether nutrition has one owner or two competing ones. |
| Q6 | Is anything writing `RestingHeartRateRecord` / HRV? | Whether to add tables for recovery tracking. |
| Q7 | Does a 60-day-old read succeed, and what exactly fails without the history permission? | Confirms G6 on this device. |
| Q8 | What is `recordingMethod` on sleep and exercise — measured or manual? | Trust level per source; feeds the hierarchy of truth in §6. |

### 0.4 Discovery results — observed 2026-08-27 via Health Connect Toolbox

Aggregate reads over 2026-08-01..08-27 grouped by day, plus non-aggregate `ExerciseSession` reads showing `dataOrigin`.

| # | Question | Result | Status |
|---|---|---|---|
| Q1 | MFP writes `NutritionRecord`? | **All nutrient fields null**, every day, including days with 2,000+ kcal logged | ❌ **negative** — see caveat below |
| Q2 | Per-item or rollup? | Moot — nothing present | — |
| Q3 | eGym writes `ExerciseSessionRecord`? | **eGym does not write to Health Connect at all** | ❌ **confirmed negative** |
| Q4 | Same fact from two origins? | **Yes.** Withings + Samsung Health both write exercise sessions. Duplicates match **exactly at second granularity** | ✅ **confirmed** |
| Q5 | Samsung also writes nutrition? | Nothing writes nutrition | ❌ |
| Q6 | `RestingHeartRate` / HRV? | `RestingHeartRate` null. HRV not aggregatable; not separately confirmed | ❌ |
| Q7 | History window | Not yet probed | ⏳ open |
| Q8 | `recordingMethod` | Not yet captured | ⏳ open |

**Only two `dataOrigin` packages appear: Withings and Samsung Health.**

Additional observations:

| Data type | Observed | Consequence |
|---|---|---|
| `Weight` | null | Withings does **not** write weight to HC (or permission absent — see caveat). Keep the OAuth API path (§3.6) |
| `ActiveCaloriesBurned` | null | No active-calorie data exists |
| `TotalCaloriesBurned` | **exactly equals** `BasalMetabolicRate` (1677.18 kcal) | Passthrough of BMR only — §3.3's "join calories from a separate record" has nothing to join |
| `BasalMetabolicRate` | 1677.18 kcal, constant | `data/baseline_metrics.json` records **1777**. 100 kcal discrepancy — reconcile which is trusted |
| `HeartRateSeries` | 891 / 675 / 550 samples on Aug 1–3 | vs **13/day** in `heart_rate_records`. Root cause found — see §0.5 |
| `SleepSession` Aug 1 | `PT1H12M` | DB holds that nap at **72.5 min**. Independent validation of the Round 1 sleep extraction ✅ |
| `ExerciseSession` Aug 1 | `PT16M` | DB has **zero** exercise rows for Aug 1–3 — HC holds a session the DB lacks |

> ⚠️ **Outstanding caveat on the null results.** The Toolbox returns null both when data is absent *and* when it lacks read permission for that type — the two are indistinguishable in its output. Before treating Q1 (nutrition) and the `Weight` null as settled, confirm under **Health Connect → App permissions → Health Connect Toolbox** that those permissions are actually granted, and re-run. Q3 (eGym) is not affected: eGym is absent as a *writer*, which no permission on the reader side would change.

### 0.5 Incidental finding — 1.34M heart rate samples already on disk

Chasing the 13/day vs 891/day gap: the export CSV has 28,173 rows and the DB has 28,173 — a clean 1:1, so the parser drops nothing. But each CSV row is a **summary**; its `binning_data` column points at JSON blobs holding the actual sample series, and **nothing reads them**.

```
heart_rate json files in export        :   25,847
TOTAL HR sample points sitting unparsed: 1,335,759
currently in heart_rate_records        :    28,173
multiplier                             :       47x
```

```json
{"elapsed_time": 1120, "heart_rate": 120.0, "start_time": 1645967118544}   // sampling_rate: 1000
```

**The HR granularity visible in Health Connect is not something Health Connect provides — it is something the existing parser leaves behind.** Recovering it requires no Android work, no permissions, and no sync folder. Tracked as **F7** in `docs/HANDOVER.md`.

### 0.6 Verdict — the case for the bridge has narrowed sharply

The original motivation was *"same granularity, without the daily 159 MB payload."* Discovery shows Health Connect does not deliver the granularity half:

| Source | Via Health Connect | Better alternative |
|---|---|---|
| Nutrition | ❌ nothing | Export only |
| eGym loads/sets | ❌ nothing (Q3), and no HC data type exists anyway (G8) | Console OCR — unchanged |
| Body composition | ❌ null | Withings OAuth API — already incremental |
| Resting HR / HRV | ❌ nothing writes it | — |
| Active calories | ❌ null | — |
| Heart rate | ✅ but **47× less** than the export already holds (§0.5) | Fix the local parser |
| Sleep | ✅ but export also carries `sleep_score`, which HC cannot represent (G1) | Export is strictly richer |
| Exercise | ✅ — and introduces a duplication problem that does not exist today (Q4) | — |
| Steps | ✅ genuinely new (not currently modelled) | — |

**What remains is freshness, not granularity or coverage** — and it arrives with a dedup burden attached.

**Recommendation: do not build a *Health Connect* collector.** This verdict is scoped to Health Connect only — see §0.8 for a materially better candidate found afterwards. Reordered priorities:

1. **F7** — parse the HR blobs. 47× more data, zero new dependencies, entirely local.
2. **eGym console OCR** (SYSTEM_DESIGN Stage 2) — unchanged by any of this; HC offers nothing here.
3. **Reconciliation harness** (§6) — still worth building; it audits export-vs-DB today.
4. **Health Connect bridge** — demote to optional. Revisit only if daily freshness becomes a real need, or if the nutrition permission caveat overturns Q1.
5. **Samsung Health Data SDK** (§0.8) — unverified, but addresses the exact gaps that sank Health Connect. Verify before ruling in or out.

Good news for the deferred design: Q4 found duplicates matching **exactly at second granularity**, so exact start/end matching suffices for dedup — no tolerance window needed, and G7 is easier than feared.

### 0.8 The path Health Connect discovery did not evaluate — Samsung Health Data SDK

**Found 2026-08-27, after the §0.6 verdict.** The Health Connect assessment was correct but incomplete: Health Connect is the *generic Android* API. Samsung ships its own **Health Data SDK**, and it exposes precisely what Health Connect structurally cannot.

| | Health Connect | Samsung Health Data SDK |
|---|---|---|
| `sleep_score` | ❌ no schema field (G1) | ✅ `DataType.SleepType.SLEEP_SCORE` |
| Sleep stages | ✅ | ✅ |
| Nutrition | ❌ null on device (Q1) | ✅ listed readable |
| Body composition | ❌ null | ✅ |
| Heart rate | ✅ but 47× coarser than the export | ✅ **granularity unverified** |
| eGym | ❌ does not write | ❌ does not write (unchanged) |
| Access gate | permissions only | **read in developer mode needs no partner request** |

**G1 may not be permanent.** It was recorded as "`sleep_score` is unreachable, export-only forever." That is true *of Health Connect*, not of Samsung as a source.

**The access gate is lower than expected.** Partner registration is required only to **write** data or to **distribute** an app. A personal, sideloaded, read-only collector runs in developer mode without registration.

#### Enabling developer mode

Samsung Health → ⋮ → Settings → About Samsung Health → tap the **version line ~10 times**. A *Developer mode (Samsung Health Data SDK)* entry appears; accept the notice and enable **Developer Mode for Data Read**.

Requirements: Samsung Health **6.30.2+**, Android **10+**, Java **17+**. **No emulator support** — the real phone is required. Library ships as `samsung-health-data-api-<version>.aar`, dropped into `app/libs/` and declared as `implementation(files("libs/samsung-health-data-api-<version>.aar"))`.

Working code exists in Samsung's codelabs — [sleep data](https://developer.samsung.com/codelab/health/sleep-data.html) (demonstrates `SLEEP_SCORE`) and [steps data](https://developer.samsung.com/codelab/health/steps-data.html) (permission/consent flow). Adapt those rather than starting cold.

#### The four questions that decide it

Read-only probe, same discipline as §0.2. **S1 and S2 are decisive.**

| # | Question | What it decides |
|---|---|---|
| **S1** | **HR granularity** — hourly aggregates, or the 1-minute `binning_data` bins? | If hourly only, the export stays **mandatory** for HR rather than merely being the backup |
| **S2** | **History range** — can it read 2022? | Health Connect's 30-day cap was the sting. An equivalent cap keeps backfill export-only |
| **S3** | **`SLEEP_SCORE` populated?** | Confirms G1 is recoverable by this path |
| **S4** | **Nutrition** — per-item or daily rollup? | Whether `food_log_items` (822 rows) gets a live path |

If S1 returns hourly-only **and** S2 is capped, the SDK yields fresh sleep scores and little else — worth knowing, not worth an app.

#### Known-good verification target

Do not verify the probe against itself. Read **2026-08-24**, which the database already holds from the export:

```
sleep_sessions   total_sleep_minutes 432.5   sleep_score 90.0   duration 461.0
                 deep 132.5  rem 78.5  light 221.5  awake 29.0  efficiency 93.7
heart_rate       316 bins that date, avg 94.3 bpm
daily_nutrition  2120.2 kcal, 171.1 g protein, is_complete 1
```

Matching numbers verify the path end to end against independently-derived data. Disagreement is itself a finding — record which source wins in §6's hierarchy of truth.

### 0.9 The export is not going away, and its path is fixed

Whatever the SDK proves capable of, the manual export remains:

- It is the only **disaster-recovery** source for a derived database.
- It is almost certainly the only route to the **1,297,310 `binning_data` HR bins** and the **38,114 recovery samples** — those are export artefacts, and no API has shown signs of exposing them.
- It carries Samsung's proprietary fields wholesale.

**Correction to an earlier suggestion.** A previous note proposed "direct the export into a synced folder." That is not possible — the destination is automated and fixed:

```
Internal storage / Download / Samsung Health / samsunghealth_<user>_<YYYYMMDDHHMMSS>.zip
```

Samsung writes both the `.zip` and an extracted folder of the same name there. So the move is to **mirror that known path, not redirect it** — point a folder-sync tool (Syncthing, or `adb pull` over USB) at `Download/Samsung Health/` and let it land in `data/inbox/`. The path is stable and predictable, which makes this easier than redirection would have been. The export trigger itself stays manual; only the transfer is automated.

#### Getting the file off the phone — no app required

The phone is the source, so the transfer is a **pull from the Mac**, not a push from the phone. Options, ranked by whether the data stays under your control:

| Approach | Data leaves your control? | Notes |
|---|---|---|
| **`adb pull`, scripted** | No — USB, fully local | `scripts/pull_samsung_export.py`. Zero new software; adb is already installed and authorised |
| **Syncthing** | No — peer-to-peer, no server | Wireless and hands-off. Needs an Android battery-optimisation exemption or background sync stalls |
| **Own NAS** | No | Natural fit if one is already on the network |
| Cloud drive auto-upload | **Yes** | Easiest to set up, and puts hormone protocols and bloodwork on a third-party server. Contradicts §1. Client-side encryption would fix it, but encrypting on the phone means writing an app — the thing this avoids |

`scripts/pull_samsung_export.py` lists exports on the device, skips any already in `data/inbox/` or `archive/`, pulls the newest (or `--all`), and **verifies each transfer twice** — byte size against the device, then a zip integrity check — deleting the file rather than leaving a partial archive that looks valid to anything checking only for existence. Requires USB and a manual export trigger; the monthly cadence in §6 makes that acceptable.

**Note:** two exports now exist on the device — `...20260826150962` and `...20260827123333`, a day apart. That is a ready-made test case for the §6 reconciliation harness: two overlapping complete snapshots, where the correct behaviour is near-total agreement plus one day of new data.

### 0.10 Exit criteria

Discovery is done when §3's mapping tables carry an **observed record count and field-presence figure** for every row, and G1–G7 each have a confirmed severity. Then, and only then, revisit the build order in §7.

---

## 1. What this proposes

Replace the 159 MB manual Samsung Health export as the *daily* data path with an incremental Health Connect reader on the phone, writing JSON payloads to a synced folder that the existing Mac-side pipeline drains.

The export is **not** retired. It becomes a periodic reconciliation pass (§6) — the only source for fields Health Connect cannot carry, and the safety net for stream gaps.

```
┌─────────────┐   Health Connect    ┌──────────────┐   synced folder   ┌──────────────┐
│ Galaxy Watch│──── changes API ───▶│ Android      │──── JSON spool ──▶│ data/inbox/  │
│ Samsung H.  │                     │ collector    │   (encrypted)     │ (Mac)        │
└─────────────┘                     └──────────────┘                   └──────┬───────┘
                                                                              │
┌─────────────┐   OAuth2 REST (unchanged, do not route via HC)                │
│ Withings    │──────────────────────────────────────────────────────────────▶│
└─────────────┘                                                               ▼
                                                                    ┌──────────────────┐
┌─────────────┐   manual export, periodic (§6)                      │ ingest + dedup   │
│ Samsung ZIP │───────────────────────────────────────────────────▶ │ health_dashboard │
└─────────────┘                                                     └──────────────────┘
```

This is **not new architecture**. `data/inbox/` is already the drop zone in SYSTEM_DESIGN §2, Tier 1 SHA-256 dedup already guards it, and processed files already move to `archive/{source}/{YYYY-MM}/`. The bridge relocates that inbox to a synced folder and adds a second producer.

---

## 2. Payload envelope

One file per collection window. **Immutable once written** — revisions emit a new file, never a rewrite.

```json
{
  "schema_version": 1,
  "source": "health_connect",
  "device_id": "galaxy-watch-6",
  "collected_at": "2026-08-27T06:12:00Z",
  "window": { "from": "2026-08-26T00:00:00Z", "to": "2026-08-27T00:00:00Z" },
  "changes_token_prev": "CJq…",
  "changes_token_next": "CJr…",
  "record_count": 412,
  "content_sha256": "9f2c…",
  "records": [
    {
      "change_type": "upsert",
      "hc_type": "SleepSessionRecord",
      "hc_id": "a4f2-…",
      "last_modified": "2026-08-27T05:58:11Z",
      "payload": { }
    }
  ]
}
```

### Envelope rules

| Rule | Why |
|---|---|
| Write `NAME.json.tmp`, atomic-rename to `NAME.json` when flushed | Sync clients expose partially-written files. The agent ignores `*.tmp`. |
| `record_count` + `content_sha256` cover the `records` array | **Tier 1 file hashing does not catch truncation** — a truncated file simply hashes differently and passes the "unseen" check. The envelope must self-validate before any row is written. |
| `changes_token_prev` / `_next` chain every payload | Lets the agent detect a broken chain and fall back to a window read. See §5. |
| Never rewrite a delivered file | Sync conflict copies and re-delivery become harmless. |
| Quarantine `*(conflicted copy)*` and `*(1).json` to `data/needs_review/` | Drive/Dropbox invent these. Never ingest blind. |
| Archive locally after ingest — **not** to the synced folder | Otherwise deleted files resurrect on the next sync and round-trip forever. |

### Encryption (recommended)

Payloads are opaque blobs with exactly one consumer, so they can be encrypted client-side (`age` or `gpg`) and decrypted at ingest. The drive then holds ciphertext it cannot read. This preserves the "local-first / zero external cloud dependencies" claim in SYSTEM_DESIGN §1 in substance, and is only possible because the transport is blobs rather than a live database. Given the repo holds hormone protocols and bloodwork, take it.

---

## 3. Data contract — Health Connect → existing tables

Record class names verified against current Android developer documentation (see §9).

### 3.1 Sleep → `sleep_sessions` ✅ strong

| HC source | Target column | Note |
|---|---|---|
| `SleepSessionRecord.startTime` / `.endTime` | `start_time`, `end_time` | |
| derived from `.endTime` | `wake_date` | Matches the settled wake-date convention |
| `.endTime - .startTime` | `duration_minutes` | **Wall-clock. Never write 0** — this was defect F0. |
| `SleepSessionRecord.Stage` STAGE_TYPE_DEEP | `deep_sleep_minutes` | Clamp each stage to the session window before summing |
| … STAGE_TYPE_REM | `rem_sleep_minutes` | |
| … STAGE_TYPE_LIGHT | `light_sleep_minutes` | |
| … STAGE_TYPE_AWAKE / AWAKE_IN_BED / OUT_OF_BED | `awake_minutes` | Decide whether OUT_OF_BED counts as awake or excluded |
| deep+rem+light | `total_sleep_minutes` | Must satisfy `total_sleep_minutes <= duration_minutes` |
| computed | `efficiency_pct` | `total_sleep / duration * 100`, capped at 100 |
| `Metadata.id` | `id` | Replaces Samsung `datauuid` as PK — see §4 |
| local rule | `session_role`, `is_nap` | Derived Mac-side. Unchanged. |
| **— none —** | `sleep_score` | ❌ **GAP.** See §4. |

### 3.2 Heart rate → `heart_rate_records` ✅ strong

| HC source | Target | Note |
|---|---|---|
| `HeartRateRecord.samples[].beatsPerMinute` | `bpm` | Series type — one row per sample |
| `.samples[].time` | `timestamp`, `date` | |
| `Metadata.device` | `device_id` | 🎉 Would finally populate a column that is 100% NULL today |
| aggregate | `min_bpm`, `max_bpm` | Not HC fields — compute over the window |
| `RestingHeartRateRecord` | *(new)* | Not currently modelled. Worth capturing. |
| `HeartRateVariabilityRmssdRecord` | *(new)* | Not currently modelled. Relevant to recovery tracking. |

### 3.3 Exercise → `exercise_sessions` ⚠️ needs work

| HC source | Target | Note |
|---|---|---|
| `ExerciseSessionRecord.startTime` / `.endTime` | `start_time`, `end_time`, `date` | |
| `.exerciseType` | `exercise_type`, `exercise_name` | ⚠️ **HC uses its own `ExerciseType` enum — NOT Samsung's codes.** The `EXERCISE_TYPE_MAP` (15003, 15004, …) built in Round 1 **does not apply**. A second mapping table is required, and rows from the two sources will disagree on `exercise_type` unless normalised. |
| `.title` / `.notes` | `notes` | Could populate another 100%-NULL column |
| **not on the session record** | `calorie_burn_kcal` | ⚠️ `ActiveCaloriesBurnedRecord` / `TotalCaloriesBurnedRecord` are **separate records**. Must be joined by time window. |
| **not on the session record** | `mean_hr_bpm`, `max_hr_bpm` | ⚠️ Derive from `HeartRateRecord` samples inside the session window. Write **NULL**, never 0, when absent — this was defect F3's sibling. |

### 3.4 Nutrition → `daily_nutrition` / `food_log_items` ⚠️ **conditional**

`NutritionRecord` (Interval) carries the macro and micro fields you need, plus `mealType` and `name`, and `HydrationRecord` maps to the `water_ml` column that is 100% NULL today.

**But Samsung's own FAQ describes the sync as "activity data, such as steps and exercise, heart rate, and sleep" — nutrition is not listed.** If Samsung Health does not write food logs to Health Connect, your 822 `food_log_items` and 286 `daily_nutrition` rows have no stream path at all, and nutrition stays export-only.

**However — MyFitnessPal is connected to Health Connect on this device.** That changes the picture: if MFP writes `NutritionRecord` **directly**, Samsung stops being in the path at all and nutrition gets a stream source independent of Samsung's sync scope. This is the more likely resolution of G3, and the better one.

> **Verify on device — see §0, questions Q1, Q2 and Q5.** Whether MFP writes per-item records or daily rollups decides whether `food_log_items` (822 rows) survives. Whether Samsung *also* writes nutrition decides whether there are two competing owners (G7).

### 3.5 eGym → `EGYM_WORKOUTS` (planned, SYSTEM_DESIGN Stage 2) ⚠️ **partial only**

eGym is connected to Health Connect on this device, which raises the obvious question: does that retire the planned eGym console OCR parser?

**No.** Health Connect has no data type for **weight lifted, sets, or repetitions**. `ExerciseSessionRecord` carries start/end, `exerciseType`, title/notes and an optional route; load and rep detail have to live in the consuming app's own database. The schema is built for endurance work.

That is precisely the data eGym exists to give you. SYSTEM_DESIGN §4 names *"eGym console: authoritative source for mechanical peak loads (kg) and machine power"*, and `EGYM_WORKOUTS` is keyed on `(user_id, date, exercise_name, set_number)`. None of it crosses.

| What eGym-via-HC gives | What it does not |
|---|---|
| Session exists, start/end, duration | **Peak load (kg)** |
| `exerciseType`, title | **Sets, reps** |
| Associated HR samples, calories | **Per-machine progression** |

**Net effect:** the OCR parser survives, but gets easier. Health Connect supplies the session *skeleton* automatically, so the console photo only has to yield loads and sets — a smaller extraction problem, plus an independent cross-check on session timing. Confirm scope via §0 Q3.

### 3.6 Body composition → `withings_readings` ❌ do not route through HC

Health Connect has `WeightRecord`, `BodyFatRecord`, `LeanBodyMassRecord`, `BoneMassRecord`, `BodyWaterMassRecord`, `BasalMetabolicRateRecord` — so the mapping is *possible*.

**Do it anyway? No.** The Withings OAuth2 sync is already incremental (`startdate`), already correct, and gives richer data directly from source. Routing it through Health Connect adds a lossy hop and a second provenance for the same fact. Keep `sync_withings.py` as-is.

Two Withings fields have **no HC equivalent** in any case: `pulse_wave_velocity_ms` and `visceral_fat_index`. Both are 100% NULL today regardless.

---

## 4. Gaps — what the stream cannot give you

| # | Gap | Severity | Mitigation |
|---|---|---|---|
| G1 | **`sleep_score`** — Samsung proprietary, HC schema is fixed with no vendor-extension field | 🔴 you have it on 938/1,157 sessions today | Export-only field. §6 reconciliation must backfill it. |
| G2 | **Samsung sleep factors** — `physical_recovery`, `mental_recovery`, `movement_awakening`, `factor_01..10` | 🟡 currently unused | Export-only. Ignore unless you start using them. |
| G3 | **Nutrition** — may not sync at all | 🔴 if confirmed | Export-only. Verify first (§3.4). |
| G4 | **Exercise taxonomy divergence** — HC enum vs Samsung codes | 🟠 | Normalise to a single internal vocabulary; keep raw source code in a `source_type_raw` column. |
| G5 | **Identity change** — HC `Metadata.id` ≠ Samsung `datauuid` | 🔴 | The same real-world sleep arrives under two different PKs. See below. |
| G6 | **30-day history window** | 🟢 | Needs `READ_HEALTH_DATA_HISTORY`; irrelevant for daily deltas, fatal for backfill. History is export-only. |
| G7 | **Multi-origin duplication** — Samsung Health, eGym and MyFitnessPal all write to Health Connect. The same meal or session can arrive from two packages. | 🔴 grows with every app connected | `Metadata.dataOrigin.packageName` must be captured in the envelope and become part of the dedup key. See below. |
| G8 | **eGym load / sets / reps** — no Health Connect data type exists | 🔴 for progressive-overload tracking | Console OCR remains required (§3.6). |

### G5 is the one that will hurt

`sleep_sessions.id` is currently Samsung's `datauuid`. Health Connect assigns its own `Metadata.id`. **The same night, arriving via both paths, produces two rows with different primary keys** — silent duplication of exactly the kind Round 1 was cleaning up.

Required before any dual-path ingest:

1. Add a **natural key** for cross-source matching: `(user_id, start_time_rounded_to_minute, duration_bucket)`, or simply `(user_id, wake_date, start_time)`.
2. Add **provenance columns**: `source TEXT` (`health_connect` | `samsung_export` | `withings_api`) and `source_record_id TEXT`.
3. Make dedup match on the natural key, not the surrogate id — SYSTEM_DESIGN Tier 2 already works this way for `heart_rate_records` (`user_id, timestamp`); extend the same discipline to sleep and exercise.

**Do not start streaming until this exists.** Otherwise the first reconciliation doubles the sleep table.


### G7 compounds G5

With three apps writing, the identity problem is no longer one-to-one. The same real-world meal may exist as an MFP record *and* a Samsung relay of the same meal; an eGym session may also surface as a Samsung Health exercise session.

`Metadata.dataOrigin.packageName` is the only reliable discriminator. Requirements:

1. Carry `data_origin` on every record in the envelope (§2) and store it as a column.
2. Add a **precedence order per record class** — e.g. for nutrition, MFP direct beats Samsung relay; for exercise, eGym beats Samsung for gym sessions. Record it in §6's hierarchy of truth.
3. Detect **time-overlap collisions** across origins at ingest, not after the fact: two exercise sessions from different packages overlapping by more than a threshold are the same session until proven otherwise, and one must win.

Every additional connected app multiplies this. Enumerate the full connected-app list during §0 rather than assuming it is just these three.

---

## 5. Delete and revision handling

The Health Connect changes API returns **upserts and deletions**, and Samsung *revises* records after the fact — a sleep session is written at wake, then updated later when the score is computed. Two consequences:

- **There is no delete path in the pipeline today.** A record deleted on the phone would live in the DB forever. Add one: `change_type: "delete"` → soft-delete (`deleted_at`) rather than a hard `DELETE`, so reconciliation can tell "deleted upstream" from "never seen".
- **Revisions must win by recency, not arrival order.** Carry `last_modified` in the record and only overwrite when it is newer. Files can arrive out of order after an offline stretch.

**Token chain as a gap detector.** The agent stores the last `changes_token_next` it successfully drained. If the next payload's `changes_token_prev` doesn't match, the chain is broken — data was missed. Log it loudly, and fall back to a window read for the affected range. A silent gap is the failure mode this whole design exists to avoid; SYSTEM_DESIGN's Round 1 lesson was that a 26% loss reported itself as success.

---

## 6. Periodic reconciliation — the export as safety net

The stream is **fresh but lossy**: it carries only what apps wrote, only within permission windows, and gaps open silently when a sync toggle flips, an app updates, or the collector is reinstalled (permissions reset to a 30-day window). The export is **complete but heavy and manual**.

Run both. The export stops being the daily path and becomes an audit.

### Cadence

| Trigger | Cadence | Rationale |
|---|---|---|
| Routine reconciliation | **Monthly** | Comfortably inside the 30-day window, so a re-grant never loses coverage |
| Nutrition backfill | **Monthly**, or weekly if G3 confirmed | If nutrition never streams, the export *is* the nutrition path |
| After any collector reinstall / permission reset | **Immediately** | Permissions reset drops you to 30 days |
| After a broken token chain (§5) | **Immediately** | Known gap |
| Before any analysis you intend to act on | **Ad hoc** | Cheap insurance |

### Reconciliation must not blindly overwrite

Ingest the export into a **staging table**, compare, then report — do not UPSERT straight over streamed rows.

```
For each record class:
  A = rows from samsung_export (staging)
  B = rows already in the DB from health_connect

  match on the natural key (§4 G5), then classify:
    only in A          -> stream MISSED it        -> insert, flag source='samsung_export'
    only in B          -> export lacks it         -> usually fine (HC-only types); log
    in both, equal     -> agreement               -> no-op
    in both, differ    -> conflict                -> apply hierarchy of truth (below)

  Always: backfill export-only fields (sleep_score, Samsung factors) onto matched rows.
```

### Hierarchy of truth for this conflict class

Extends SYSTEM_DESIGN §4 Tier 3:

| Field class | Winner | Why |
|---|---|---|
| `sleep_score`, Samsung proprietary factors | **Export** | Only source that has them |
| Stage minutes, durations, timestamps | **Export** | Samsung's post-processed values are its own final word |
| Recency — records newer than the last export | **Stream** | Export simply doesn't have them yet |
| Body composition | **Withings API** | Unchanged from SYSTEM_DESIGN §4 |
| Nutrition | **Export** (until G3 resolved) | |

Rule of thumb: **the stream owns freshness, the export owns completeness and Samsung-only fields.**

### The output that matters

Every reconciliation must emit a written report — rows recovered, conflicts found, fields backfilled — not just a row count. Example:

```
RECONCILE 2026-08-27  (export 2026-08-26 .. stream since 2026-07-28)
  sleep_sessions   : 31 matched,  2 stream-missed (+2),  31 sleep_score backfilled
  exercise_sessions: 44 matched,  0 stream-missed,       3 taxonomy conflicts -> needs_review
  nutrition        : 26 export-only (G3 confirmed: no HC nutrition)
  heart_rate       : 4,102 matched, 118 stream-missed (+118)
  CHAIN: intact.  No token gaps.
```

If a reconciliation ever reports zero stream-missed rows across several cycles, that is evidence the stream is trustworthy — and the cadence can be relaxed. Until then it is unproven.

---

## 7. Build order

0. **Discovery (§0)** — Toolbox smoke test, then the read-only probe app. Answers Q1–Q8 and fixes the severity of G1–G8. **Nothing below is worth building until this completes**; the contract in §3 is documentation-derived guesswork until the probe replaces it with observed counts.
1. **Update §3 and §4 from probe output** — every mapping row gets an observed record count and field-presence figure.
2. **Schema: provenance + natural keys** (§4 G5) + `data_origin` (§4 G7) + soft-delete column (§5). Mac-side only, no phone code. **Must precede any streaming.**
3. **Reconciliation harness** (§6) using the export alone, against the current DB. Proves the comparison logic before a second source exists.
4. **Android collector** — built fresh, **not** grown from the §0.2 probe — Kotlin, sideloaded, single purpose: changes-token read → envelope → encrypted file → synced folder. No UI beyond a sync button and a status line.
5. **Agent-side spool drain** — envelope validation, quarantine rules, delete path.
6. **Retire the daily export.** Only after several clean reconciliations.

Steps 1–3 are Mac-side and useful even if the phone app is never built. Step 0 is cheap and decides whether the rest is worth doing at all. Step 4 is the only Android work, and it is a collector, not an app — **do not build a hybrid UI**; a PWA cannot reach Health Connect, and the viewer has no reason to be fused to the collector.

---

## 8. Open questions

1. **G3 — does Samsung write nutrition to Health Connect?** Blocks the nutrition path. Verify before anything else.
2. **Sync target** — Syncthing (peer-to-peer, no third party) vs private git remote vs consumer drive? SYSTEM_DESIGN §1 claims zero cloud dependencies; client-side encryption (§2) mostly reconciles this, but the choice is the owner's.
3. **`OUT_OF_BED` stages** — count as awake, or excluded from the session entirely?
4. **Retire the export, or keep it forever?** Recommendation: keep it. It is the only path to `sleep_score` and the only disaster-recovery source.
5. **`RestingHeartRateRecord` / HRV** — not currently modelled. Worth adding tables for, given the recovery-tracking focus in the protocols?

---

## 9. Sources

Record class names and permission behaviour verified 2026-08-27 against:

- [Health Connect data types](https://developer.android.com/health-and-fitness/health-connect/data-types)
- [Read raw data — Health Connect](https://developer.android.com/health-and-fitness/health-connect/read-data) (30-day window, `READ_HEALTH_DATA_HISTORY`, background reads)
- [Develop sleep experiences with Health Connect](https://developer.android.com/health-and-fitness/health-connect/experiences/sleep)
- [Health Connect FAQ — Samsung Developer](https://developer.samsung.com/health/health-connect-faq.html) (scope of Samsung sync)
- [Managing Sleep Data with Samsung Health and Health Connect](https://developer.samsung.com/health/blog/en/managing-sleep-data-with-samsung-health-and-health-connect)
- [Develop workout experiences with Health Connect](https://developer.android.com/health-and-fitness/health-connect/experiences/workouts) (no load/sets/reps data type)
- [Test your integration with the Health Connect Toolbox](https://developer.android.com/health-and-fitness/health-connect/test/health-connect-toolbox)
- [android/health-samples — HealthConnectSample](https://github.com/android/health-samples/tree/main/health-connect/HealthConnectSample) (fork base for the §0.2 probe)

API surfaces move. Re-verify §3 record classes and §4 G6 permission behaviour before implementing.
