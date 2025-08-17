package com.hiennv.flutter_callkit_incoming

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class CallkitNotificationService : Service() {

    companion object {

        private val ActionForeground = listOf(
            CallkitConstants.ACTION_CALL_START,
            CallkitConstants.ACTION_CALL_ACCEPT
        )


        fun startServiceWithAction(context: Context, action: String, data: Bundle?) {
            val intent = Intent(context, CallkitNotificationService::class.java).apply {
                this.action = action
                putExtra(CallkitConstants.EXTRA_CALLKIT_INCOMING_DATA, data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && intent.action in ActionForeground) {
                data?.let {
                    if(it.getBoolean(CallkitConstants.EXTRA_CALLKIT_CALLING_SHOW, true)) {
                        ContextCompat.startForegroundService(context, intent)
                    }else {
                        context.startService(intent)
                    }
                }
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, CallkitNotificationService::class.java)
            context.stopService(intent)
        }

    }

    private val callkitNotificationManager: CallkitNotificationManager? =
        FlutterCallkitIncomingPlugin.getInstance()?.getCallkitNotificationManager()


    override fun onCreate() {
        startForegroundNotification()
        super.onCreate()
    }
    
    private fun startForegroundNotification() {
        try {
            // Create a basic notification for immediate display to satisfy Android 8.0+ requirements
            val tempNotificationId = 1000
            val data = Bundle()
            
            // Ensure notification channels exist
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val notificationManager = FlutterCallkitIncomingPlugin.getInstance()?.getCallkitNotificationManager()
                // Make sure to create all required channels first
                notificationManager?.createNotificationChanel(data)
                
                // Double check that the channel exists
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (nm.getNotificationChannel(CallkitNotificationManager.NOTIFICATION_CHANNEL_ID_ONGOING) == null) {
                    // Create channel directly if needed
                    val channel = NotificationChannel(
                        CallkitNotificationManager.NOTIFICATION_CHANNEL_ID_ONGOING,
                        "Ongoing Call",
                        NotificationManager.IMPORTANCE_HIGH
                    )
                    channel.setSound(null, null)
                    nm.createNotificationChannel(channel)
                }
            }
            
            // Create a properly configured notification
            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationCompat.Builder(this, CallkitNotificationManager.NOTIFICATION_CHANNEL_ID_ONGOING)
            } else {
                NotificationCompat.Builder(this)
            }
            
            val notification = builder
                .setContentTitle("Call service")
                .setContentText("Initializing call service...")
                .setSmallIcon(R.drawable.ic_accept) // Make sure this icon exists!
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setAutoCancel(false)
                .setSound(null)
                // Add a default action so notification is valid
                .setContentIntent(PendingIntent.getActivity(
                    this, 
                    0, 
                    Intent(this, CallkitIncomingActivity::class.java), 
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
                ))
                .build()
                
            // Start as foreground service with notification
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(tempNotificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL)
            } else {
                startForeground(tempNotificationId, notification)
            }
        } catch (e: Exception) {
            // Log the error to help diagnose issues
            Log.e("CallkitService", "Error starting foreground: ${e.message}", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action === CallkitConstants.ACTION_CALL_START) {
            intent.getBundleExtra(CallkitConstants.EXTRA_CALLKIT_INCOMING_DATA)
                ?.let {
                    if(it.getBoolean(CallkitConstants.EXTRA_CALLKIT_CALLING_SHOW, true)) {
                        FlutterCallkitIncomingPlugin.getInstance()?.getCallkitNotificationManager()
                            ?.createNotificationChanel(it)
                        showOngoingCallNotification(it)
                    }else {
                        stopSelf()
                    }
                }
        }
        if (intent?.action === CallkitConstants.ACTION_CALL_ACCEPT) {
            intent.getBundleExtra(CallkitConstants.EXTRA_CALLKIT_INCOMING_DATA)
                ?.let {
                    callkitNotificationManager?.clearIncomingNotification(it, true)
                    if (it.getBoolean(CallkitConstants.EXTRA_CALLKIT_CALLING_SHOW, true)) {
                        showOngoingCallNotification(it)
                    }else {
                        stopSelf()
                    }
                }
        }
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun showOngoingCallNotification(bundle: Bundle) {

        val callkitNotification =
            this.callkitNotificationManager?.getOnGoingCallNotification(bundle, false)
        if (callkitNotification != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    callkitNotification.id,
                    callkitNotification.notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
                )
            } else {
                startForeground(callkitNotification.id, callkitNotification.notification)
            }
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        callkitNotificationManager?.destroy()
    }

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }


    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }else {
            stopForeground(true)
        }
        stopSelf()
    }



}

