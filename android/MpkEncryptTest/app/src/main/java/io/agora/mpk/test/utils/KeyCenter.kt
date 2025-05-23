package io.agora.mpk.test.utils

import io.agora.media.RtcTokenBuilder
import io.agora.mpk.test.BuildConfig
import io.agora.rtm.RtmTokenBuilder
import io.agora.rtm.RtmTokenBuilder2
import java.util.Random

object KeyCenter {
    private const val USER_MAX_UID = 10000

    const val APP_ID: String = BuildConfig.APP_ID
    const val APP_CERTIFICATE: String = BuildConfig.APP_CERTIFICATE
    const val RTC_TOKEN: String = BuildConfig.RTC_TOKEN
    private var USER_RTC_UID = -1
    private var USER_RTM_UID = -1

    private val randomUserUid: Int
        get() {
            return Random().nextInt(USER_MAX_UID)
        }

    fun getRtcUid(): Int {
        if (USER_RTC_UID == -1) {
            USER_RTC_UID = randomUserUid
        }
        return USER_RTC_UID
    }

    fun getRtmUid(): Int {
        if (USER_RTM_UID == -1) {
            USER_RTM_UID = randomUserUid
        }
        return USER_RTM_UID
    }

    fun getRtcToken(channelId: String?, uid: Int): String {
        if (BuildConfig.RTC_TOKEN.isNotEmpty()) {
            return BuildConfig.RTC_TOKEN
        }

        if (BuildConfig.APP_CERTIFICATE.isEmpty()) {
            return ""
        }
        return RtcTokenBuilder().buildTokenWithUid(
            BuildConfig.APP_ID,
            BuildConfig.APP_CERTIFICATE,
            channelId,
            uid,
            RtcTokenBuilder.Role.Role_Publisher,
            0
        )
    }

    fun getRtmToken(uid: Int): String? {
        val appCertificate = BuildConfig.APP_CERTIFICATE

        if (appCertificate.isEmpty()) {
            return ""
        }
        return try {
            RtmTokenBuilder().buildToken(
                BuildConfig.APP_ID,
                appCertificate, uid.toString(),
                RtmTokenBuilder.Role.Rtm_User,
                0
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getRtmToken2(uid: Int): String {
        val appId = APP_ID
        val appCertificate = BuildConfig.APP_CERTIFICATE
        if (appCertificate.isEmpty()) {
            return ""
        }
        return try {
            RtmTokenBuilder2().buildToken(
                appId,
                appCertificate, uid.toString(),
                24 * 60 * 60
            )
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }
}
