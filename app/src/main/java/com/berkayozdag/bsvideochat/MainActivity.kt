package com.berkayozdag.bsvideochat

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.SurfaceView
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.berkayozdag.bsvideochat.databinding.ActivityMainBinding
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.LeaveChannelOptions
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import io.agora.rtc2.video.VideoCanvas
import io.agora.rtc2.video.VideoEncoderConfiguration

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    companion object {
        private const val APP_ID = "aaa0c5f814c94864a7bc01b7063d058f"
        private const val CHANNEL_NAME = "bsvideochat"
        private const val TOKEN =
            "007eJxTYFDL1N934yzrIrGpMSzO3M9fqbmtEN7IxXU8Tt7j+P2dlvcUGBITEw2STdMsDE2SLU0szEwSzZOSDQyTzA3MjFMMTC3SgpQb0hoCGRns5nxgYWSAQBCfmyGpuCwzJTU/OSOxhIEBAIbIIEA="
        private const val UID = 0
        private const val PERMISSION_ID = 12
    }

    private var isJoined = false
    private var agoraEngine: RtcEngine? = null
    private var localSurfaceView: SurfaceView? = null
    private var remoteSurfaceView: SurfaceView? = null

    private val requestedPermissions = arrayOf(
        android.Manifest.permission.RECORD_AUDIO,
        android.Manifest.permission.CAMERA
    )

    // İzinlerin kontrolünü yapar
    private fun checkPermission(): Boolean {
        return !(ContextCompat.checkSelfPermission(
            this,
            requestedPermissions[0]
        ) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            this,
            requestedPermissions[1]
        ) != PackageManager.PERMISSION_GRANTED)
    }

    // Kullanıcıya mesaj gösterir
    private fun showMessage(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    // Agora SDK'nın başlatılması ve video yapılandırmasının ayarlanması
    private fun setupVideoSdkEngine() {
        try {
            val config = RtcEngineConfig()
            config.mContext = applicationContext
            config.mAppId = APP_ID
            config.mEventHandler = mRtcEventHandler
            agoraEngine = RtcEngine.create(config)
            agoraEngine?.enableVideo()

            val videoConfig = VideoEncoderConfiguration(
                VideoEncoderConfiguration.VD_640x360,
                VideoEncoderConfiguration.FRAME_RATE.FRAME_RATE_FPS_15,
                VideoEncoderConfiguration.STANDARD_BITRATE,
                VideoEncoderConfiguration.ORIENTATION_MODE.ORIENTATION_MODE_FIXED_PORTRAIT
            )
            agoraEngine?.setVideoEncoderConfiguration(videoConfig)
        } catch (e: Exception) {
            e.message?.let { showMessage(it) }
        }
    }

    // Aktivite oluşturulurken çağrılır
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (!checkPermission()) {
            ActivityCompat.requestPermissions(this, requestedPermissions, PERMISSION_ID)
        } else {
            setupVideoSdkEngine()
        }

        binding.joinButton.setOnClickListener {
            joinCall()
        }

        binding.leaveButton.setOnClickListener {
            leaveCall()
        }
    }

    // İzin sonuçlarını alır ve işlem yapar
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_ID) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                setupVideoSdkEngine()
            } else {
                showMessage("Permissions not granted")
            }
        }
    }

    // Aktivite yok edilirken çağrılır
    override fun onDestroy() {
        super.onDestroy()
        agoraEngine?.let {
            it.stopPreview()
            val options = LeaveChannelOptions()
            it.leaveChannel(options)
            RtcEngine.destroy()
            agoraEngine = null
        }
        isJoined = false
    }

    // Çağrıdan ayrılmak için kullanılan method
    private fun leaveCall() {
        if (!isJoined) {
            showMessage("Join a channel first")
        } else {
            val options = LeaveChannelOptions()
            agoraEngine?.leaveChannel(options)
            showMessage("You left the channel")
            localSurfaceView?.visibility = GONE
            remoteSurfaceView?.visibility = GONE
            isJoined = false
        }
    }

    // Kanala katılmak için kullanılan method
    private fun joinCall() {
        if (checkPermission()) {
            val option = ChannelMediaOptions()
            option.channelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
            option.clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
            setupLocalVideo()
            localSurfaceView?.visibility = VISIBLE
            agoraEngine?.startPreview()
            agoraEngine?.joinChannel(TOKEN, CHANNEL_NAME, UID, option)
        } else {
            showMessage("Permission not granted")
        }
    }

    // Agora SDK olay işleyicisi
    private val mRtcEventHandler = object : IRtcEngineEventHandler() {
        override fun onUserJoined(uid: Int, elapsed: Int) {
            showMessage("Remote user joined: $uid")
            runOnUiThread { setupRemoteVideo(uid) }
        }

        override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
            isJoined = true
            showMessage("Joined channel: $channel")
        }

        override fun onUserOffline(uid: Int, reason: Int) {
            showMessage("User offline: $uid")
            runOnUiThread {
                remoteSurfaceView?.visibility = GONE
            }
        }

        override fun onError(err: Int) {
            showMessage("Error: $err")
        }
    }

    // Uzaktan gelen videoyu ayarlamak için kullanılan method
    private fun setupRemoteVideo(uid: Int) {
        remoteSurfaceView = SurfaceView(applicationContext)
        remoteSurfaceView?.setZOrderMediaOverlay(true)
        binding.remoteUser.addView(remoteSurfaceView)

        agoraEngine?.setupRemoteVideo(
            VideoCanvas(
                remoteSurfaceView,
                VideoCanvas.RENDER_MODE_FIT,
                uid
            )
        )
    }

    // Yerel videoyu ayarlamak için kullanılan method
    private fun setupLocalVideo() {
        localSurfaceView = SurfaceView(applicationContext)
        binding.localUser.addView(localSurfaceView)

        agoraEngine?.setupLocalVideo(
            VideoCanvas(
                localSurfaceView,
                VideoCanvas.RENDER_MODE_FIT,
                0
            )
        )
    }
}
