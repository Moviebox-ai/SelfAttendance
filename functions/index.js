const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue, Timestamp } = require("firebase-admin/firestore");

// Initialize Firebase Admin SDK
initializeApp();
const db = getFirestore();

const TRIAL_COLLECTION = "businessTrials";
const USERS_COLLECTION = "users";
const TRIAL_DURATION_DAYS = 7;
const TRIAL_DURATION_MS = TRIAL_DURATION_DAYS * 24 * 60 * 60 * 1000;

/**
 * verifyUserTrial - Firebase Cloud Function (Callable)
 *
 * Verifies and anchors the 7-day Business Mode free trial strictly on the server
 * based on the caller's unique User ID (UID) stored in Firestore.
 *
 * Why this is essential:
 * - Prevents trial reset after app uninstall/reinstall.
 * - Prevents client-side clock tampering (always uses server clock).
 * - Binds the trial start date permanently to the unique User ID in Firestore.
 * - Guarantees that even if local cache or device storage is wiped, the server
 *   evaluates the true elapsed days and remaining days accurately.
 */
exports.verifyUserTrial = onCall(
  {
    cors: true,
    region: "us-central1",
  },
  async (request) => {
    // 1. Enforce Authentication
    if (!request.auth || !request.auth.uid) {
      throw new HttpsError(
        "unauthenticated",
        "User must be authenticated with Firebase to verify business trial."
      );
    }

    const uid = request.auth.uid;
    const email = request.auth.token.email || null;
    const nowServerTimeMs = Date.now();

    console.log(`[verifyUserTrial] Checking trial for UID: ${uid}, Email: ${email}`);

    try {
      const trialDocRef = db.collection(TRIAL_COLLECTION).document(uid);
      const userDocRef = db.collection(USERS_COLLECTION).document(uid);

      // Check existing trial records in parallel
      const [trialSnap, userSnap] = await Promise.all([
        trialDocRef.get(),
        userDocRef.get(),
      ]);

      let trialStartTimeMs = null;
      let existingRecordFound = false;

      // Check 1: Dedicated businessTrials collection by UID
      if (trialSnap.exists) {
        const data = trialSnap.data();
        if (data.trialStartTime) {
          trialStartTimeMs = convertToMillis(data.trialStartTime);
          existingRecordFound = true;
          console.log(`[verifyUserTrial] Found trial in businessTrials/${uid}: ${trialStartTimeMs}`);
        }
      }

      // Check 2: User profile document
      if (!trialStartTimeMs && userSnap.exists) {
        const userData = userSnap.data();
        if (userData.businessTrialStartTime) {
          trialStartTimeMs = convertToMillis(userData.businessTrialStartTime);
          existingRecordFound = true;
          console.log(`[verifyUserTrial] Found trial in users/${uid}: ${trialStartTimeMs}`);
        }
      }

      // Check 3: If no record found by UID, check if there is an email-based record (anti-account recycling)
      if (!trialStartTimeMs && email) {
        const emailHash = hashEmail(email);
        const emailDocRef = db.collection(TRIAL_COLLECTION).document(`email_${emailHash}`);
        const emailSnap = await emailDocRef.get();
        if (emailSnap.exists) {
          const emailData = emailSnap.data();
          if (emailData.trialStartTime) {
            trialStartTimeMs = convertToMillis(emailData.trialStartTime);
            existingRecordFound = true;
            console.log(`[verifyUserTrial] Found trial in email anchor: ${trialStartTimeMs}`);
          }
        }
      }

      // If this is the user's very first time accessing Business Mode:
      if (!trialStartTimeMs || trialStartTimeMs <= 0) {
        trialStartTimeMs = nowServerTimeMs;
        const initialTimestamp = Timestamp.fromMillis(trialStartTimeMs);

        const newTrialData = {
          uid: uid,
          email: email,
          trialStartTime: initialTimestamp,
          createdAt: initialTimestamp,
          trialDurationDays: TRIAL_DURATION_DAYS,
          status: "active",
          verifiedBy: "firebase_cloud_function",
          lastVerifiedAt: initialTimestamp,
        };

        const batch = db.batch();
        batch.set(trialDocRef, newTrialData, { merge: true });

        // Also write email anchor to prevent multiple accounts using the same email
        if (email) {
          const emailHash = hashEmail(email);
          const emailDocRef = db.collection(TRIAL_COLLECTION).document(`email_${emailHash}`);
          batch.set(emailDocRef, newTrialData, { merge: true });
        }

        // Mirror on user's profile if user document exists
        if (userSnap.exists) {
          batch.set(
            userDocRef,
            {
              businessTrialStartTime: initialTimestamp,
              lastTrialCheckAt: initialTimestamp,
            },
            { merge: true }
          );
        }

        await batch.commit();
        console.log(`[verifyUserTrial] Created new authoritative trial record for UID: ${uid} starting at ${trialStartTimeMs}`);
      } else {
        // Update last verification ping
        try {
          await trialDocRef.set(
            {
              lastVerifiedAt: Timestamp.fromMillis(nowServerTimeMs),
            },
            { merge: true }
          );
        } catch (pingErr) {
          console.warn("[verifyUserTrial] Failed to update lastVerifiedAt:", pingErr.message);
        }
      }

      // Compute trial status strictly based on server time
      const diffMs = Math.max(0, nowServerTimeMs - trialStartTimeMs);
      const daysElapsed = Math.floor(diffMs / (24 * 60 * 60 * 1000));
      const remainingDays = Math.max(0, TRIAL_DURATION_DAYS - daysElapsed);
      const trialExpiryTimeMs = trialStartTimeMs + TRIAL_DURATION_MS;
      const isTrialActive = daysElapsed < TRIAL_DURATION_DAYS && nowServerTimeMs < trialExpiryTimeMs;

      return {
        success: true,
        uid: uid,
        trialStartTime: trialStartTimeMs,
        trialExpiryTime: trialExpiryTimeMs,
        serverTime: nowServerTimeMs,
        isTrialActive: isTrialActive,
        remainingDays: remainingDays,
        elapsedDays: daysElapsed,
        trialDurationDays: TRIAL_DURATION_DAYS,
        existingRecordFound: existingRecordFound,
        verifiedByServer: true,
      };
    } catch (error) {
      console.error(`[verifyUserTrial] Error verifying trial for UID ${uid}:`, error);
      if (error instanceof HttpsError) {
        throw error;
      }
      throw new HttpsError("internal", `Failed to verify business trial: ${error.message}`);
    }
  }
);

/**
 * Helper: Normalizes and hashes email to prevent dot and plus-tag aliasing
 */
function hashEmail(email) {
  const crypto = require("crypto");
  const cleaned = email.trim().toLowerCase();
  const [localPart, domain] = cleaned.split("@", 2);
  let normalized = cleaned;
  if (domain === "gmail.com" || domain === "googlemail.com") {
    const baseLocal = localPart.split("+")[0].replace(/\./g, "");
    normalized = `${baseLocal}@gmail.com`;
  }
  return crypto.createHash("sha256").update(normalized).digest("hex");
}

/**
 * Helper: Converts Firestore Timestamp, Date, or numeric millisecond to Long ms
 */
function convertToMillis(timestampObj) {
  if (!timestampObj) return null;
  if (typeof timestampObj.toMillis === "function") {
    return timestampObj.toMillis();
  }
  if (typeof timestampObj.toDate === "function") {
    return timestampObj.toDate().getTime();
  }
  if (typeof timestampObj === "number") {
    return timestampObj;
  }
  if (timestampObj._seconds !== undefined) {
    return timestampObj._seconds * 1000 + Math.floor((timestampObj._nanoseconds || 0) / 1000000);
  }
  return null;
}
