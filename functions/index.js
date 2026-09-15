/**
 * OceanXChat - Firebase Cloud Functions
 * Provides real-time FCM notifications for VoIP Calling, Chat Messages, and Group Chats.
 *
 * Requirements met:
 * 1. High-priority data-only messages for incoming calls (wakes up Android device, triggers full-screen intent).
 * 2. Instant dismissal when call ends or caller hangs up.
 * 3. Chat and group message push notifications when recipient is offline or in the background.
 */

const functions = require("firebase-functions");
const admin = require("firebase-admin");

admin.initializeApp();
const db = admin.database();

/**
 * 1. Incoming Call Notification
 * Listens on /incomingCalls/{calleeUid}
 * Sends high-priority data-only FCM push to wake up device and launch IncomingCallActivity
 */
exports.onIncomingCall = functions.database
  .ref("/incomingCalls/{calleeUid}")
  .onWrite(async (change, context) => {
    const calleeUid = context.params.calleeUid;

    // If deleted, the call was cancelled or ended
    if (!change.after.exists()) {
      return null;
    }

    const callData = change.after.val();
    const status = callData.status || "calling";

    if (status !== "calling" && status !== "ringing") {
      return null;
    }

    const callerUid = callData.caller;
    const callId = callData.callId;
    const callType = callData.type || "voice";

    // Fetch caller profile
    const callerSnap = await db.ref(`users/${callerUid}`).once("value");
    const callerName = callerSnap.child("name").val() || "OceanXChat User";
    const callerDp = callerSnap.child("dpUrl").val() || "";

    // Fetch callee device tokens
    const tokens = await getUserFcmTokens(calleeUid);
    if (tokens.length === 0) {
      console.log(`[onIncomingCall] No FCM tokens found for callee ${calleeUid}`);
      return null;
    }

    // High priority data-only payload to guarantee immediate background execution
    const payload = {
      tokens: tokens,
      data: {
        type: "call",
        callId: String(callId),
        caller: String(callerUid),
        callerName: String(callerName),
        callerDp: String(callerDp),
        callType: String(callType),
        timestamp: String(Date.now())
      },
      android: {
        priority: "high",
        ttl: 60 * 1000 // 60 seconds validity for VoIP calls
      }
    };

    try {
      const response = await admin.messaging().sendEachForMulticast(payload);
      console.log(`[onIncomingCall] Sent call push to ${tokens.length} devices. Success: ${response.successCount}`);
      // Clean up invalid tokens
      await cleanupInvalidTokens(calleeUid, tokens, response);
    } catch (err) {
      console.error("[onIncomingCall] Error sending multicast push:", err);
    }
    return null;
  });

/**
 * 2. Call Status Monitor
 * Listens on /calls/{callId}/status
 * When a call is ended, sends a dismiss signal so ringing stops immediately
 */
exports.onCallEnded = functions.database
  .ref("/calls/{callId}/status")
  .onUpdate(async (change, context) => {
    const newStatus = change.after.val();
    if (newStatus !== "ended") return null;

    const callId = context.params.callId;
    const callSnap = await db.ref(`calls/${callId}`).once("value");
    if (!callSnap.exists()) return null;

    const calleeUid = callSnap.child("callee").val();
    if (!calleeUid) return null;

    const tokens = await getUserFcmTokens(calleeUid);
    if (tokens.length === 0) return null;

    const payload = {
      tokens: tokens,
      data: {
        type: "call_ended",
        callId: String(callId)
      },
      android: {
        priority: "high"
      }
    };

    try {
      await admin.messaging().sendEachForMulticast(payload);
    } catch (e) {
      console.error("[onCallEnded] Error sending dismiss push:", e);
    }
    return null;
  });

/**
 * 3. One-to-One Chat Message Notification
 * Listens on /messages/{chatId}/{messageId}
 */
exports.onNewMessage = functions.database
  .ref("/messages/{chatId}/{messageId}")
  .onCreate(async (snapshot, context) => {
    const msgData = snapshot.val();
    if (!msgData || msgData.system) return null;

    const chatId = context.params.chatId;
    const senderUid = msgData.sender;

    // chatId format is uid1_uid2
    const parts = chatId.split("_");
    if (parts.length !== 2) return null;

    const recipientUid = parts[0] === senderUid ? parts[1] : parts[0];

    // Check if recipient has blocked sender
    const blockSnap = await db.ref(`blocks/${recipientUid}/${senderUid}`).once("value");
    if (blockSnap.val() === true) return null;

    // Get sender info
    const senderSnap = await db.ref(`users/${senderUid}`).once("value");
    const senderName = senderSnap.child("name").val() || "OceanXChat";
    const senderDp = senderSnap.child("dpUrl").val() || "";

    const tokens = await getUserFcmTokens(recipientUid);
    if (tokens.length === 0) return null;

    const textPreview = msgData.isFile ? "\uD83D\uDCC1 Sent an attachment" : "Sent you a message";

    const payload = {
      tokens: tokens,
      data: {
        type: "message",
        chatId: chatId,
        senderUid: senderUid,
        senderName: senderName,
        senderDp: senderDp,
        title: senderName,
        body: textPreview
      },
      android: {
        priority: "high"
      }
    };

    try {
      const response = await admin.messaging().sendEachForMulticast(payload);
      await cleanupInvalidTokens(recipientUid, tokens, response);
    } catch (e) {
      console.error("[onNewMessage] Error sending push:", e);
    }
    return null;
  });

/**
 * 4. Group Message Notification
 * Listens on /groupMessages/{groupId}/{messageId}
 */
exports.onNewGroupMessage = functions.database
  .ref("/groupMessages/{groupId}/{messageId}")
  .onCreate(async (snapshot, context) => {
    const msgData = snapshot.val();
    if (!msgData || msgData.system) return null;

    const groupId = context.params.groupId;
    const senderUid = msgData.sender;

    const groupSnap = await db.ref(`groups/${groupId}`).once("value");
    const groupName = groupSnap.child("name").val() || "Group Chat";

    const membersSnap = await db.ref(`groupMembers/${groupId}`).once("value");
    const members = membersSnap.val() || {};

    const recipientUids = Object.keys(members).filter((uid) => uid !== senderUid);
    if (recipientUids.length === 0) return null;

    for (const memberUid of recipientUids) {
      const tokens = await getUserFcmTokens(memberUid);
      if (tokens.length === 0) continue;

      const senderName = msgData.senderName || "Member";
      const preview = msgData.isFile ? "\uD83D\uDCC1 Sent an attachment" : "Sent a message";

      const payload = {
        tokens: tokens,
        data: {
          type: "message",
          chatId: groupId,
          isGroup: "true",
          senderUid: senderUid,
          senderName: groupName,
          title: groupName,
          body: `${senderName}: ${preview}`
        },
        android: {
          priority: "high"
        }
      };

      try {
        const response = await admin.messaging().sendEachForMulticast(payload);
        await cleanupInvalidTokens(memberUid, tokens, response);
      } catch (err) {
        console.error(`[onNewGroupMessage] Error sending to member ${memberUid}:`, err);
      }
    }
    return null;
  });

// Helper: fetch all active FCM tokens for a user
async function getUserFcmTokens(uid) {
  const tokens = new Set();

  // Check /users/{uid}/devices
  const devicesSnap = await db.ref(`users/${uid}/devices`).once("value");
  if (devicesSnap.exists()) {
    devicesSnap.forEach((child) => {
      const token = child.child("fcmToken").val();
      if (token) tokens.add(token);
    });
  }

  // Check top-level /users/{uid}/fcmToken
  const singleTokenSnap = await db.ref(`users/${uid}/fcmToken`).once("value");
  const singleToken = singleTokenSnap.val();
  if (singleToken) tokens.add(singleToken);

  return Array.from(tokens);
}

// Helper: remove stale or expired tokens
async function cleanupInvalidTokens(uid, tokens, response) {
  if (!response || !response.responses) return;
  const staleTokens = [];
  response.responses.forEach((res, idx) => {
    if (!res.success && res.error) {
      const code = res.error.code;
      if (
        code === "messaging/invalid-registration-token" ||
        code === "messaging/registration-token-not-registered"
      ) {
        staleTokens.push(tokens[idx]);
      }
    }
  });

  if (staleTokens.length === 0) return;

  const devicesSnap = await db.ref(`users/${uid}/devices`).once("value");
  devicesSnap.forEach((child) => {
    if (staleTokens.includes(child.child("fcmToken").val())) {
      child.ref.remove();
    }
  });
}
