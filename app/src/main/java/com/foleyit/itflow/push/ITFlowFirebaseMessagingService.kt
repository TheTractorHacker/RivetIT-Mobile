package com.foleyit.itflow.push

import com.foleyit.itflow.data.api.ApiClient
import com.foleyit.itflow.data.api.FcmTokenRequest
import com.foleyit.itflow.ui.navigation.DeepLinks
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ITFlowFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val title  = message.notification?.title ?: message.data["title"] ?: "ITFlow"
        val body   = message.notification?.body  ?: message.data["body"]  ?: return
        val action = message.data["action"]?.takeIf { DeepLinks.ALLOWED_ROUTE.matches(it) }
        NotificationHelper.createChannel(this)
        NotificationHelper.show(this, title, body, action)
    }

    override fun onNewToken(token: String) {
        // Re-register updated FCM token with the server
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ApiClient.service().registerFcmToken(FcmTokenRequest(token))
            } catch (_: Exception) {
                // Will be registered on next login if the API call fails here
            }
        }
    }
}
