# Firestore rules for `device_locks` — required console step

The Android-ID restore flow reads and writes `device_locks/{ANDROID_ID}` **before any account signs
in**, so the collection cannot sit behind the `users/{uid}` rules. Without a ruleset the lookup fails
with `PERMISSION_DENIED`, the gate releases after its bounded retries, and no lock is ever restored —
the feature compiles and does nothing.

Apply this in Firebase Console → Firestore Database → Rules → Publish. It is a server-side step; no
file in this repository changes it (`firebase.json`/`firestore.rules` are not part of this project).

## What the collection holds

| Field | Type | Meaning |
|---|---|---|
| document id | string | `Settings.Secure.ANDROID_ID` of the device (lower-cased hex) |
| `deviceId` | string | same value, so a document can never be written under the wrong key |
| `days` | int | whole days still owed by the timed uninstall lock |
| `expiryWallMs` | int | wall-clock deadline of that lock, in ms |
| `isTimedLockActive` | bool | always `true` in this collection; the client never writes `false` |
| `recordedAtMs` | int | client wall clock at mirror time; ordering/logging only, never trusted |

No names, emails, blocklists, URLs or search data. A device identifier is still personal data: keep
the privacy policy and Play Data Safety declaration in step with §6.6 of
[reinstall-restore-gap-analysis.md](reinstall-restore-gap-analysis.md).

## Ruleset

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {

    match /device_locks/{deviceId} {

      // Shape of a record that may exist at all.
      function isWellFormed() {
        let s = request.resource.data;
        return s.deviceId == deviceId
            && s.isTimedLockActive == true
            && s.days is int && s.days >= 1 && s.days <= 3650
            && s.expiryWallMs is int && s.expiryWallMs > request.time.toMillis()
            && s.recordedAtMs is int;
      }

      // A commitment may only grow: never deactivated, never shortened, never deleted.
      function isNonWeakening() {
        let before = resource.data;
        let after = request.resource.data;
        return after.isTimedLockActive == true
            && after.expiryWallMs >= before.expiryWallMs
            && after.days >= before.days
            && after.deviceId == deviceId;
      }

      allow get:  if true;
      allow list: if false;              // no enumeration of device ids
      allow create: if isWellFormed();
      allow update: if isWellFormed() && isNonWeakening();
      allow delete: if false;
    }
  }
}
```

## Why these rules are what make the restore trustworthy

The client is the user's own device and is always attacker-controlled, so the client-side merge
(`FirestoreCommitmentLockRepository.publishStronger`) is only politeness. The monotonic guarantee —
*a stored commitment can be extended but never lowered or removed* — has to be enforced here, by
`request.time` (server clock) and by the delete/`isTimedLockActive` denials. With this ruleset:

- Editing the local app to write `isTimedLockActive = false` is rejected.
- Writing a shorter `days`/earlier `expiryWallMs` is rejected.
- Deleting the document to simulate "never armed" is rejected.
- `list` is denied so device ids cannot be harvested from the collection.
- A forged *new* document for someone else's device id can only ever ADD protection; it cannot
  release anything. That asymmetry is fail-closed by design.

The client enforces the same ordering before it writes, in one place:
`CommitmentRestorePolicy.isStrongerThan(candidate, stored)` (used by
`FirestoreCommitmentLockRepository.publishStronger`). Its rule is a superset of `isNonWeakening()` —
permanent/untimed protection (`isTimedLockActive == true` with `days <= 0`) outranks every timed
record — which only matters until these rules are published, because `isWellFormed()` accepts
`days >= 1` and therefore cannot store an untimed commitment at all. That is consistent with the app:
`InputValidator.validateUninstallDays` accepts 1..30, so every lock this build arms is timed.

Residual, stated honestly: a factory reset regenerates `ANDROID_ID`, so the device no longer asks for
its old record; and rules cannot prove that the person holding the device is the person who armed it.
Both are product decisions, not code fixes (see §6.5 of the gap analysis).

## Verification after publishing

1. Active 30-day lock, ~5 days left → uninstall → reinstall → open the app.
2. The restore gate stays up briefly, then the lock is re-armed with the original deadline
   (`adb logcat | grep "FG:Commitment"` shows `Commitment re-armed: 5d left`; `adb logcat -s` cannot
   take a tag containing a colon, so grep the buffer instead).
3. Settings → Uninstall Protection shows the restored remaining days; device admin and accessibility
   must be re-granted by the user, as they always were after an uninstall.
4. Attempt a downgrade in the Firestore console (set `isTimedLockActive` to `false`) — the client's
   next mirror must be rejected with `PERMISSION_DENIED` and the local lock must stay armed.
