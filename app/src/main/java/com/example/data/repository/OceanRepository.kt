package com.example.data.repository

import com.example.data.crypto.CryptoUtils
import com.example.data.model.*
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class OceanRepository {
    private val db = FirebaseManager.database
    private val auth = FirebaseManager.auth

    val currentUid: String?
        get() = auth.currentUser?.uid

    // --- Authentication ---
    suspend fun signIn(rawId: String, pass: String): String {
        var email = rawId.trim()
        if (!email.contains("@")) {
            email = email.replace(Regex("[^0-9+]"), "") + "@oceanclient.app"
        }
        val result = auth.signInWithEmailAndPassword(email, pass).await()
        return result.user?.uid ?: throw Exception("Sign in failed")
    }

    suspend fun signUp(rawId: String, pass: String): String {
        var email = rawId.trim()
        if (!email.contains("@")) {
            email = email.replace(Regex("[^0-9+]"), "") + "@oceanclient.app"
        }
        val result = auth.createUserWithEmailAndPassword(email, pass).await()
        return result.user?.uid ?: throw Exception("Sign up failed")
    }

    fun signOut() {
        val uid = currentUid
        if (uid != null) {
            FirebaseManager.setOffline(uid)
        }
        auth.signOut()
    }

    suspend fun getUser(uid: String): User? {
        val snap = db.getReference("users/$uid").get().await()
        return if (snap.exists()) {
            User(
                uid = uid,
                name = snap.child("name").getValue(String::class.java) ?: "User",
                bio = snap.child("bio").getValue(String::class.java) ?: "",
                dpUrl = snap.child("dpUrl").getValue(String::class.java) ?: "",
                arId = snap.child("arId").getValue(String::class.java) ?: "",
                searchNum = snap.child("searchNum").getValue(String::class.java),
                ringtoneUrl = snap.child("ringtoneUrl").getValue(String::class.java),
                callerToneUrl = snap.child("callerToneUrl").getValue(String::class.java)
            )
        } else null
    }

    suspend fun saveProfile(name: String, bio: String, dpUrl: String): String {
        val uid = currentUid ?: throw Exception("Not signed in")
        val arId = "ar-" + (100000000..999999999).random()
        val email = auth.currentUser?.email ?: ""
        var searchNum: String? = null
        if (email.endsWith("@oceanclient.app")) {
            searchNum = email.substringBefore("@")
        }

        val userData = mutableMapOf<String, Any>(
            "uid" to uid,
            "name" to name,
            "bio" to bio,
            "dpUrl" to dpUrl,
            "arId" to arId
        )
        if (searchNum != null) {
            userData["searchNum"] = searchNum
        }

        db.getReference("users/$uid").setValue(userData).await()
        db.getReference("arIds/$arId").setValue(uid).await()
        if (searchNum != null) {
            db.getReference("arIds/$searchNum").setValue(uid).await()
        }

        return arId
    }

    suspend fun updateProfile(updates: Map<String, Any>) {
        val uid = currentUid ?: return
        db.getReference("users/$uid").updateChildren(updates).await()
    }

    // --- Search & Contacts ---
    suspend fun searchUser(query: String): User? {
        val q = query.trim()
        val snap = db.getReference("arIds/$q").get().await()
        if (!snap.exists()) return null
        val targetUid = snap.getValue(String::class.java) ?: return null
        return getUser(targetUid)
    }

    suspend fun sendFriendRequest(targetUid: String) {
        val myUid = currentUid ?: return
        db.getReference("requests/$targetUid/$myUid").setValue(mapOf("time" to ServerValue.TIMESTAMP)).await()
    }

    suspend fun respondToRequest(senderUid: String, accept: Boolean) {
        val myUid = currentUid ?: return
        if (accept) {
            db.getReference("contacts/$myUid/$senderUid").setValue(true).await()
            db.getReference("contacts/$senderUid/$myUid").setValue(true).await()
        }
        db.getReference("requests/$myUid/$senderUid").removeValue().await()
    }

    suspend fun addContactDirectly(peerUid: String) {
        val myUid = currentUid ?: return
        db.getReference("contacts/$myUid/$peerUid").setValue(true).await()
        db.getReference("contacts/$peerUid/$myUid").setValue(true).await()
    }

    fun getRequestsFlow(): Flow<List<User>> = callbackFlow {
        val myUid = currentUid ?: run {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val ref = db.getReference("requests/$myUid")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<User>()
                val keys = snapshot.children.mapNotNull { it.key }
                if (keys.isEmpty()) {
                    trySend(emptyList())
                    return
                }
                var loaded = 0
                for (uid in keys) {
                    db.getReference("users/$uid").get().addOnSuccessListener { uSnap ->
                        if (uSnap.exists()) {
                            list.add(
                                User(
                                    uid = uid,
                                    name = uSnap.child("name").getValue(String::class.java) ?: "User",
                                    bio = uSnap.child("bio").getValue(String::class.java) ?: "",
                                    dpUrl = uSnap.child("dpUrl").getValue(String::class.java) ?: "",
                                    arId = uSnap.child("arId").getValue(String::class.java) ?: ""
                                )
                            )
                        }
                        loaded++
                        if (loaded == keys.size) {
                            trySend(list)
                        }
                    }.addOnFailureListener {
                        loaded++
                        if (loaded == keys.size) {
                            trySend(list)
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    fun getContactsFlow(): Flow<List<Triple<User, UserPresence, Int>>> = callbackFlow {
        val myUid = currentUid ?: run {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val ref = db.getReference("contacts/$myUid")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val contactUids = snapshot.children.mapNotNull { it.key }
                if (contactUids.isEmpty()) {
                    trySend(emptyList())
                    return
                }

                val results = mutableListOf<Triple<User, UserPresence, Int>>()
                var counter = 0
                for (fUid in contactUids) {
                    val cid = getChatId(myUid, fUid)
                    db.getReference("users/$fUid").get().addOnSuccessListener { uSnap ->
                        db.getReference("status/$fUid").get().addOnSuccessListener { sSnap ->
                            db.getReference("unreadCounts/$myUid/$cid").get().addOnSuccessListener { unSnap ->
                                if (uSnap.exists()) {
                                    val user = User(
                                        uid = fUid,
                                        name = uSnap.child("name").getValue(String::class.java) ?: "User",
                                        bio = uSnap.child("bio").getValue(String::class.java) ?: "",
                                        dpUrl = uSnap.child("dpUrl").getValue(String::class.java) ?: "",
                                        arId = uSnap.child("arId").getValue(String::class.java) ?: "",
                                        ringtoneUrl = uSnap.child("ringtoneUrl").getValue(String::class.java),
                                        callerToneUrl = uSnap.child("callerToneUrl").getValue(String::class.java)
                                    )
                                    val presence = UserPresence(
                                        state = sSnap.child("state").getValue(String::class.java) ?: "offline",
                                        lastSeen = sSnap.child("lastSeen").getValue(Long::class.java) ?: 0L
                                    )
                                    val unread = (unSnap.getValue(Long::class.java) ?: 0L).toInt()
                                    results.add(Triple(user, presence, unread))
                                }
                                counter++
                                if (counter == contactUids.size) {
                                    trySend(results.toList())
                                }
                            }
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    fun getChatId(u1: String, u2: String): String =
        if (u1 < u2) "${u1}_${u2}" else "${u2}_${u1}"

    // --- Messages ---
    fun getMessagesFlow(chatId: String, isGroup: Boolean, clearedAt: Long = 0L): Flow<List<Message>> = callbackFlow {
        val path = if (isGroup) "groupMessages/$chatId" else "messages/$chatId"
        val ref = db.getReference(path)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<Message>()
                for (child in snapshot.children) {
                    val mId = child.key ?: ""
                    val sender = child.child("sender").getValue(String::class.java) ?: ""
                    val rawText = child.child("text").getValue(String::class.java) ?: ""
                    val isFile = child.child("isFile").getValue(Boolean::class.java) ?: false
                    val fileData = child.child("fileData").getValue(String::class.java)
                    val fileType = child.child("fileType").getValue(String::class.java)
                    val timestamp = child.child("timestamp").getValue(Long::class.java) ?: 0L
                    val msgStatus = child.child("msgStatus").getValue(String::class.java) ?: "sent"
                    val senderName = child.child("senderName").getValue(String::class.java)
                    val senderDp = child.child("senderDp").getValue(String::class.java)
                    val isSystem = child.child("system").getValue(Boolean::class.java) ?: false

                    if (timestamp >= clearedAt) {
                        val decrypted = if (!isFile && !isSystem) {
                            CryptoUtils.decrypt(rawText)
                        } else rawText

                        list.add(
                            Message(
                                id = mId,
                                sender = sender,
                                senderName = senderName,
                                senderDp = senderDp,
                                text = decrypted,
                                timestamp = timestamp,
                                msgStatus = msgStatus,
                                isFile = isFile,
                                fileData = fileData,
                                fileType = fileType,
                                system = isSystem
                            )
                        )
                    }
                }
                trySend(list)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun sendMessage(
        chatId: String,
        peerUid: String?,
        plainText: String,
        isGroup: Boolean,
        senderName: String,
        senderDp: String,
        replyToQuote: String? = null
    ) {
        val myUid = currentUid ?: return
        var finalText = plainText
        if (!replyToQuote.isNullOrBlank()) {
            finalText = "Replying to: \"${replyToQuote.take(30)}...\"\n\n$plainText"
        }
        val encrypted = CryptoUtils.encrypt(finalText)

        val msgData = mutableMapOf<String, Any>(
            "sender" to myUid,
            "text" to encrypted,
            "timestamp" to ServerValue.TIMESTAMP,
            "msgStatus" to "sent"
        )
        if (isGroup) {
            msgData["senderName"] = senderName
            msgData["senderDp"] = senderDp
            db.getReference("groupMessages/$chatId").push().setValue(msgData).await()
        } else {
            db.getReference("messages/$chatId").push().setValue(msgData).await()
            if (peerUid != null) {
                val unreadRef = db.getReference("unreadCounts/$peerUid/$chatId")
                val unreadSnap = unreadRef.get().await()
                val count = (unreadSnap.getValue(Long::class.java) ?: 0L) + 1
                unreadRef.setValue(count).await()
                db.getReference("contacts/$peerUid/$myUid").setValue(true).await()
            }
            setTyping(chatId, false)
        }
    }

    suspend fun sendFileMessage(
        chatId: String,
        peerUid: String?,
        fileName: String,
        fileUrl: String,
        fileType: String,
        isGroup: Boolean,
        senderName: String,
        senderDp: String
    ) {
        val myUid = currentUid ?: return
        val msgData = mutableMapOf<String, Any>(
            "sender" to myUid,
            "text" to "\uD83D\uDCC1 $fileName",
            "isFile" to true,
            "fileData" to fileUrl,
            "fileType" to fileType,
            "timestamp" to ServerValue.TIMESTAMP,
            "msgStatus" to "sent"
        )
        if (isGroup) {
            msgData["senderName"] = senderName
            msgData["senderDp"] = senderDp
            db.getReference("groupMessages/$chatId").push().setValue(msgData).await()
        } else {
            db.getReference("messages/$chatId").push().setValue(msgData).await()
            if (peerUid != null) {
                val unreadRef = db.getReference("unreadCounts/$peerUid/$chatId")
                val unreadSnap = unreadRef.get().await()
                val count = (unreadSnap.getValue(Long::class.java) ?: 0L) + 1
                unreadRef.setValue(count).await()
                db.getReference("contacts/$peerUid/$myUid").setValue(true).await()
            }
        }
    }

    fun markMessageRead(chatId: String, messageId: String) {
        db.getReference("messages/$chatId/$messageId/msgStatus").setValue("read")
    }

    suspend fun resetUnread(chatId: String) {
        val myUid = currentUid ?: return
        db.getReference("unreadCounts/$myUid/$chatId").setValue(0).await()
    }

    fun setTyping(chatId: String, isTyping: Boolean) {
        val myUid = currentUid ?: return
        db.getReference("typing/$chatId/$myUid").setValue(isTyping)
    }

    fun getTypingFlow(chatId: String, peerUid: String): Flow<Boolean> = callbackFlow {
        val ref = db.getReference("typing/$chatId/$peerUid")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.getValue(Boolean::class.java) ?: false)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun clearChat(chatId: String, isGroup: Boolean) {
        val myUid = currentUid ?: return
        val path = if (isGroup) "userGroups/$myUid/$chatId/clearedAt" else "userChats/$myUid/$chatId/clearedAt"
        db.getReference(path).setValue(ServerValue.TIMESTAMP).await()
    }

    suspend fun deleteMessage(chatId: String, msgId: String, isGroup: Boolean) {
        val path = if (isGroup) "groupMessages/$chatId/$msgId" else "messages/$chatId/$msgId"
        db.getReference(path).removeValue().await()
    }

    // --- Blocks ---
    suspend fun setBlocked(peerUid: String, block: Boolean) {
        val myUid = currentUid ?: return
        if (block) {
            db.getReference("blocks/$myUid/$peerUid").setValue(true).await()
        } else {
            db.getReference("blocks/$myUid/$peerUid").removeValue().await()
        }
    }

    fun getBlockFlow(peerUid: String): Flow<Pair<Boolean, Boolean>> = callbackFlow {
        val myUid = currentUid ?: run {
            trySend(Pair(false, false))
            close()
            return@callbackFlow
        }
        var blockedByMe = false
        var blockedByPeer = false

        val l1 = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                blockedByMe = snapshot.getValue(Boolean::class.java) ?: false
                trySend(Pair(blockedByMe, blockedByPeer))
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        val l2 = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                blockedByPeer = snapshot.getValue(Boolean::class.java) ?: false
                trySend(Pair(blockedByMe, blockedByPeer))
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        val ref1 = db.getReference("blocks/$myUid/$peerUid")
        val ref2 = db.getReference("blocks/$peerUid/$myUid")
        ref1.addValueEventListener(l1)
        ref2.addValueEventListener(l2)
        awaitClose {
            ref1.removeEventListener(l1)
            ref2.removeEventListener(l2)
        }
    }

    // --- Groups ---
    suspend fun createGroup(name: String, dpUrl: String): String {
        val myUid = currentUid ?: throw Exception("Not signed in")
        val grpRef = db.getReference("groups").push()
        val grpId = grpRef.key ?: ""
        val groupData = mapOf(
            "id" to grpId,
            "name" to name,
            "dpUrl" to dpUrl,
            "admin" to myUid,
            "createdAt" to ServerValue.TIMESTAMP
        )
        grpRef.setValue(groupData).await()
        db.getReference("groupMembers/$grpId/$myUid").setValue(true).await()
        db.getReference("userGroups/$myUid/$grpId").setValue(true).await()
        db.getReference("groupMessages/$grpId").push().setValue(
            mapOf(
                "system" to true,
                "text" to "Group created",
                "timestamp" to ServerValue.TIMESTAMP
            )
        ).await()
        return grpId
    }

    fun getUserGroupsFlow(): Flow<List<Group>> = callbackFlow {
        val myUid = currentUid ?: run {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val ref = db.getReference("userGroups/$myUid")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val groupIds = snapshot.children.mapNotNull { it.key }
                if (groupIds.isEmpty()) {
                    trySend(emptyList())
                    return
                }
                val groups = mutableListOf<Group>()
                var counter = 0
                for (gid in groupIds) {
                    db.getReference("groups/$gid").get().addOnSuccessListener { gSnap ->
                        if (gSnap.exists()) {
                            groups.add(
                                Group(
                                    id = gid,
                                    name = gSnap.child("name").getValue(String::class.java) ?: "Group",
                                    dpUrl = gSnap.child("dpUrl").getValue(String::class.java) ?: "",
                                    admin = gSnap.child("admin").getValue(String::class.java) ?: "",
                                    createdAt = gSnap.child("createdAt").getValue(Long::class.java) ?: 0L
                                )
                            )
                        }
                        counter++
                        if (counter == groupIds.size) {
                            trySend(groups)
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun joinGroup(groupId: String, userName: String) {
        val myUid = currentUid ?: return
        db.getReference("userGroups/$myUid/$groupId").setValue(true).await()
        db.getReference("groupMembers/$groupId/$myUid").setValue(true).await()
        db.getReference("groupMessages/$groupId").push().setValue(
            mapOf(
                "system" to true,
                "text" to "$userName joined the group via QR code",
                "timestamp" to ServerValue.TIMESTAMP
            )
        ).await()
    }

    // --- Status / Stories ---
    suspend fun uploadStatus(mediaData: String, type: String) {
        val myUid = currentUid ?: return
        val statusRef = db.getReference("statuses/$myUid").push()
        statusRef.setValue(
            mapOf(
                "img" to mediaData,
                "type" to type,
                "timestamp" to ServerValue.TIMESTAMP
            )
        ).await()
    }

    fun getStatusesFlow(): Flow<Map<String, List<StatusStory>>> = callbackFlow {
        val ref = db.getReference("statuses")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val now = System.currentTimeMillis()
                val map = mutableMapOf<String, MutableList<StatusStory>>()
                for (userSnap in snapshot.children) {
                    val uid = userSnap.key ?: continue
                    for (statSnap in userSnap.children) {
                        val img = statSnap.child("img").getValue(String::class.java) ?: continue
                        val type = statSnap.child("type").getValue(String::class.java) ?: "image"
                        val timestamp = statSnap.child("timestamp").getValue(Long::class.java) ?: 0L
                        if (now - timestamp < 24 * 3600 * 1000) {
                            map.getOrPut(uid) { mutableListOf() }.add(
                                StatusStory(
                                    id = statSnap.key ?: "",
                                    uid = uid,
                                    img = img,
                                    type = type,
                                    timestamp = timestamp
                                )
                            )
                        }
                    }
                }
                trySend(map)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    // --- Calls & Signaling ---
    suspend fun createCall(calleeUid: String, type: String): String {
        val myUid = currentUid ?: throw Exception("Not signed in")
        val callRef = db.getReference("calls").push()
        val callId = callRef.key ?: ""
        val callData = mapOf(
            "caller" to myUid,
            "callee" to calleeUid,
            "type" to type,
            "status" to "calling",
            "timestamp" to ServerValue.TIMESTAMP
        )
        callRef.setValue(callData).await()
        db.getReference("incomingCalls/$calleeUid").setValue(
            mapOf(
                "callId" to callId,
                "caller" to myUid,
                "type" to type,
                "status" to "calling"
            )
        ).await()
        return callId
    }

    fun listenIncomingCall(myUid: String): Flow<IncomingCall?> = callbackFlow {
        val ref = db.getReference("incomingCalls/$myUid")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val callId = snapshot.child("callId").getValue(String::class.java) ?: ""
                    val caller = snapshot.child("caller").getValue(String::class.java) ?: ""
                    val type = snapshot.child("type").getValue(String::class.java) ?: "voice"
                    val status = snapshot.child("status").getValue(String::class.java) ?: "calling"
                    trySend(IncomingCall(callId, caller, type, status))
                } else {
                    trySend(null)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun acceptCall(callId: String) {
        val myUid = currentUid ?: return
        db.getReference("incomingCalls/$myUid/status").setValue("answered").await()
        db.getReference("calls/$callId/status").setValue("answered").await()
    }

    suspend fun rejectCall(callId: String, callerUid: String) {
        val myUid = currentUid ?: return
        db.getReference("incomingCalls/$myUid").removeValue().await()
        db.getReference("calls/$callId/status").setValue("ended").await()
        db.getReference("calls/$callId/endedAt").setValue(ServerValue.TIMESTAMP).await()
    }

    suspend fun endCall(callId: String, peerUid: String, isCaller: Boolean, type: String) {
        val myUid = currentUid ?: return
        db.getReference("calls/$callId/status").setValue("ended").await()
        db.getReference("calls/$callId/endedAt").setValue(ServerValue.TIMESTAMP).await()

        val logData = mapOf(
            "peer" to peerUid,
            "type" to type,
            "timestamp" to ServerValue.TIMESTAMP
        )
        db.getReference("callLogs/$myUid").push().setValue(logData).await()
        val peerLogData = mapOf(
            "peer" to myUid,
            "type" to type,
            "timestamp" to ServerValue.TIMESTAMP
        )
        db.getReference("callLogs/$peerUid").push().setValue(peerLogData).await()

        if (isCaller) {
            db.getReference("incomingCalls/$peerUid").removeValue().await()
        } else {
            db.getReference("incomingCalls/$myUid").removeValue().await()
        }
    }

    fun getCallLogsFlow(): Flow<List<CallLog>> = callbackFlow {
        val myUid = currentUid ?: run {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val ref = db.getReference("callLogs/$myUid").orderByChild("timestamp")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<CallLog>()
                for (child in snapshot.children) {
                    val peer = child.child("peer").getValue(String::class.java) ?: ""
                    val type = child.child("type").getValue(String::class.java) ?: "voice"
                    val timestamp = child.child("timestamp").getValue(Long::class.java) ?: 0L
                    list.add(CallLog(id = child.key ?: "", peer = peer, type = type, timestamp = timestamp))
                }
                list.reverse()
                trySend(list)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    // --- Helper Wrappers & Flow Extensions ---
    suspend fun login(rawId: String, pass: String): String = signIn(rawId, pass)
    fun logout() = signOut()

    fun getProfileFlow(uid: String): Flow<User?> = callbackFlow {
        val ref = db.getReference("users/$uid")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    trySend(
                        User(
                            uid = uid,
                            name = snapshot.child("name").getValue(String::class.java) ?: "User",
                            bio = snapshot.child("bio").getValue(String::class.java) ?: "",
                            dpUrl = snapshot.child("dpUrl").getValue(String::class.java) ?: "",
                            arId = snapshot.child("arId").getValue(String::class.java) ?: "",
                            searchNum = snapshot.child("searchNum").getValue(String::class.java),
                            ringtoneUrl = snapshot.child("ringtoneUrl").getValue(String::class.java),
                            callerToneUrl = snapshot.child("callerToneUrl").getValue(String::class.java)
                        )
                    )
                } else {
                    trySend(null)
                }
            }
            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    fun getContactsWithPresenceAndUnreadFlow(uid: String): Flow<List<Triple<User, UserPresence, Int>>> = getContactsFlow()
    fun getGroupsFlow(uid: String): Flow<List<Group>> = getUserGroupsFlow()
    fun getFriendRequestsFlow(uid: String): Flow<List<User>> = getRequestsFlow()
    fun getStatusStoriesFlow(): Flow<Map<String, List<StatusStory>>> = getStatusesFlow()

    fun getIncomingCallFlow(myUid: String): Flow<IncomingCall?> = callbackFlow {
        val ref = db.getReference("incomingCalls/$myUid")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val callId = snapshot.child("callId").getValue(String::class.java) ?: ""
                    val caller = snapshot.child("caller").getValue(String::class.java) ?: ""
                    val type = snapshot.child("type").getValue(String::class.java) ?: "voice"
                    val status = snapshot.child("status").getValue(String::class.java) ?: "calling"

                    db.getReference("users/$caller").get().addOnSuccessListener { uSnap ->
                        val name = uSnap.child("name").getValue(String::class.java) ?: "User"
                        val dp = uSnap.child("dpUrl").getValue(String::class.java) ?: ""
                        trySend(IncomingCall(callId, caller, name, dp, type, status))
                    }.addOnFailureListener {
                        trySend(IncomingCall(callId, caller, "User", "", type, status))
                    }
                } else {
                    trySend(null)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun updateProfile(
        uid: String,
        name: String,
        bio: String,
        dpUrl: String,
        ringtoneUrl: String? = null,
        callerToneUrl: String? = null
    ) {
        val updates = mutableMapOf<String, Any>(
            "name" to name,
            "bio" to bio,
            "dpUrl" to dpUrl
        )
        if (ringtoneUrl != null) updates["ringtoneUrl"] = ringtoneUrl
        if (callerToneUrl != null) updates["callerToneUrl"] = callerToneUrl
        db.getReference("users/$uid").updateChildren(updates).await()
    }

    suspend fun uploadStatusStory(uid: String, mediaData: String, type: String) = uploadStatus(mediaData, type)

    suspend fun deleteStatusStory(uid: String, storyId: String) {
        db.getReference("statuses/$uid/$storyId").removeValue().await()
    }

    suspend fun acceptFriendRequest(myUid: String, senderUid: String) = respondToRequest(senderUid, true)
    suspend fun declineFriendRequest(myUid: String, senderUid: String) = respondToRequest(senderUid, false)

    suspend fun deleteContact(myUid: String, contactUid: String, contactName: String) {
        db.getReference("contacts/$myUid/$contactUid").removeValue().await()
        db.getReference("contacts/$contactUid/$myUid").removeValue().await()
        val cid = getChatId(myUid, contactUid)
        db.getReference("userChats/$myUid/$cid/clearedAt").setValue(ServerValue.TIMESTAMP).await()
    }

    fun getGroupMessagesFlow(groupId: String): Flow<List<Message>> = getMessagesFlow(groupId, isGroup = true)
    fun getOneToOneMessagesFlow(myUid: String, peerUid: String): Flow<List<Message>> =
        getMessagesFlow(getChatId(myUid, peerUid), isGroup = false)

    fun isUserBlockedByMe(myUid: String, peerUid: String): Flow<Boolean> = callbackFlow {
        val ref = db.getReference("blocks/$myUid/$peerUid")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.getValue(Boolean::class.java) ?: false)
            }
            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    fun hasUserBlockedMe(myUid: String, peerUid: String): Flow<Boolean> = callbackFlow {
        val ref = db.getReference("blocks/$peerUid/$myUid")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.getValue(Boolean::class.java) ?: false)
            }
            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    fun getTypingIndicatorFlow(myUid: String, peerUid: String): Flow<Boolean> =
        getTypingFlow(getChatId(myUid, peerUid), peerUid)

    suspend fun initiateCall(myUid: String, peerUid: String, type: String): String =
        createCall(peerUid, type)

    suspend fun toggleBlockUser(myUid: String, peerUid: String, currentlyBlocked: Boolean) =
        setBlocked(peerUid, !currentlyBlocked)

    suspend fun clearGroupChat(groupId: String) = clearChat(groupId, isGroup = true)
    suspend fun clearOneToOneChat(myUid: String, peerUid: String) =
        clearChat(getChatId(myUid, peerUid), isGroup = false)

    suspend fun sendGroupMessage(
        groupId: String,
        myUid: String,
        text: String,
        isFile: Boolean = false,
        fileData: String? = null,
        fileType: String? = null
    ) {
        val user = getUser(myUid)
        val name = user?.name ?: "User"
        val dp = user?.dpUrl ?: ""
        if (isFile && fileData != null && fileType != null) {
            sendFileMessage(groupId, null, text, fileData, fileType, isGroup = true, senderName = name, senderDp = dp)
        } else {
            sendMessage(groupId, null, text, isGroup = true, senderName = name, senderDp = dp)
        }
    }

    suspend fun sendOneToOneMessage(
        myUid: String,
        peerUid: String,
        text: String,
        replyToQuote: String? = null,
        isFile: Boolean = false,
        fileData: String? = null,
        fileType: String? = null
    ) {
        val cid = getChatId(myUid, peerUid)
        val user = getUser(myUid)
        val name = user?.name ?: "User"
        val dp = user?.dpUrl ?: ""
        if (isFile && fileData != null && fileType != null) {
            sendFileMessage(cid, peerUid, text, fileData, fileType, isGroup = false, senderName = name, senderDp = dp)
        } else {
            sendMessage(cid, peerUid, text, isGroup = false, senderName = name, senderDp = dp, replyToQuote = replyToQuote)
        }
    }

    suspend fun setTypingIndicator(myUid: String, peerUid: String, typing: Boolean) =
        setTyping(getChatId(myUid, peerUid), typing)

    suspend fun deleteGroupMessage(groupId: String, msgId: String) =
        deleteMessage(groupId, msgId, isGroup = true)

    suspend fun deleteOneToOneMessage(myUid: String, peerUid: String, msgId: String) =
        deleteMessage(getChatId(myUid, peerUid), msgId, isGroup = false)
}
