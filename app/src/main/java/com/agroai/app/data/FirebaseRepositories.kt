package com.agroai.app.data

import android.app.Activity
import com.agroai.app.data.model.FarmerProfile
import com.agroai.app.data.local.CachedFarmerProfileEntity
import com.agroai.app.data.local.SyncDao
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

class AuthRepository(private val auth: FirebaseAuth) {
    val currentUid: String? get() = auth.currentUser?.uid
    val currentPhone: String get() = auth.currentUser?.phoneNumber.orEmpty()

    fun requestOtp(
        activity: Activity,
        phoneNumber: String,
        resendToken: PhoneAuthProvider.ForceResendingToken?,
        callbacks: PhoneAuthProvider.OnVerificationStateChangedCallbacks,
    ) {
        val builder = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
        resendToken?.let(builder::setForceResendingToken)
        PhoneAuthProvider.verifyPhoneNumber(builder.build())
    }

    suspend fun signIn(credential: PhoneAuthCredential) = auth.signInWithCredential(credential).await().user
    suspend fun idToken(): String? = auth.currentUser?.getIdToken(false)?.await()?.token
    fun credential(verificationId: String, code: String) = PhoneAuthProvider.getCredential(verificationId, code)
    fun signOut() = auth.signOut()
}

class FarmerRepository(private val firestore: FirebaseFirestore, private val local: SyncDao) {
    suspend fun load(uid: String): FarmerProfile? {
        val snapshot = firestore.collection("farmers").document(uid).get().await()
        if (!snapshot.exists()) return null
        val location = snapshot.get("location") as? Map<*, *> ?: emptyMap<String, Any>()
        val farm = snapshot.get("farm") as? Map<*, *> ?: emptyMap<String, Any>()
        return FarmerProfile(
            uid = snapshot.getString("uid") ?: uid,
            name = snapshot.getString("name").orEmpty(),
            phoneNumber = snapshot.getString("phoneNumber").orEmpty(),
            preferredLanguage = snapshot.getString("preferredLanguage") ?: "en",
            state = location["state"] as? String ?: "",
            district = location["district"] as? String ?: "",
            village = location["village"] as? String ?: "",
            area = (farm["area"] as? Number)?.toDouble() ?: 0.0,
            areaUnit = farm["areaUnit"] as? String ?: "acre",
            soilType = farm["soilType"] as? String ?: "unknown",
            irrigationType = farm["irrigationType"] as? String ?: "seasonal",
            currentCrops = (snapshot.get("currentCrops") as? List<*>)?.filterIsInstance<String>().orEmpty(),
            goals = (snapshot.get("goals") as? List<*>)?.filterIsInstance<String>().orEmpty(),
            experienceLevel = snapshot.getString("experienceLevel").orEmpty(),
            onboardingCompleted = snapshot.getBoolean("onboardingCompleted") == true,
            createdAt = snapshot.getTimestamp("createdAt"), updatedAt = snapshot.getTimestamp("updatedAt"),
        ).also { local.cacheProfile(it.toCache()) }
    }

    suspend fun loadCached(uid: String): FarmerProfile? = local.cachedProfile(uid)?.toProfile()

    suspend fun save(profile: FarmerProfile) {
        val values = hashMapOf<String, Any>(
            "uid" to profile.uid,
            "name" to profile.name,
            "phoneNumber" to profile.phoneNumber,
            "preferredLanguage" to profile.preferredLanguage,
            "location" to mapOf("state" to profile.state, "district" to profile.district, "village" to profile.village),
            "farm" to mapOf("area" to profile.area, "areaUnit" to profile.areaUnit, "soilType" to profile.soilType, "irrigationType" to profile.irrigationType),
            "currentCrops" to profile.currentCrops,
            "goals" to profile.goals,
            "experienceLevel" to profile.experienceLevel,
            "onboardingCompleted" to profile.onboardingCompleted,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        val ref = firestore.collection("farmers").document(profile.uid)
        firestore.runTransaction { transaction ->
            val existing = transaction.get(ref)
            if (!existing.exists()) values["createdAt"] = FieldValue.serverTimestamp()
            transaction.set(ref, values, SetOptions.merge())
        }.await()
        load(profile.uid)
    }
}

private fun FarmerProfile.toCache() = CachedFarmerProfileEntity(
    uid, name, phoneNumber, preferredLanguage, state, district, village, area, areaUnit, soilType, irrigationType,
    currentCrops.joinToString("|"), goals.joinToString("|"), experienceLevel, onboardingCompleted,
    createdAt?.seconds, createdAt?.nanoseconds, updatedAt?.seconds, updatedAt?.nanoseconds,
)

private fun CachedFarmerProfileEntity.toProfile() = FarmerProfile(
    uid, name, phoneNumber, preferredLanguage, state, district, village, area, areaUnit, soilType, irrigationType,
    currentCrops.split('|').filter(String::isNotBlank), goals.split('|').filter(String::isNotBlank),
    experienceLevel, onboardingCompleted,
    createdAtSeconds?.let { Timestamp(it, createdAtNanos ?: 0) },
    updatedAtSeconds?.let { Timestamp(it, updatedAtNanos ?: 0) },
)
