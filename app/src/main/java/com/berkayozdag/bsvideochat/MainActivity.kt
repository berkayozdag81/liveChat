package com.berkayozdag.bsvideochat

import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.SurfaceView
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.berkayozdag.bsvideochat.databinding.ActivityMainBinding
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import io.agora.rtc2.video.VideoCanvas
import java.lang.Exception

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private val appId = "aaa0c5f814c94864a7bc01b7063d058f"
    private val channelName = "bsvideochat"
    private val token =
        "007eJxTYFDL1N934yzrIrGpMSzO3M9fqbmtEN7IxXU8Tt7j+P2dlvcUGBITEw2STdMsDE2SLU0szEwSzZOSDQyTzA3MjFMMTC3SgpQb0hoCGRns5nxgYWSAQBCfmyGpuCwzJTU/OSOxhIEBAIbIIEA="
    private val uid = 0

    private var isJoined = false
    private var agoraEngine: RtcEngine? = null
    private var localSurfaceView: SurfaceView? = null
    private var remoteSurfaceView: SurfaceView? = null


    private val PERMISSION_ID = 12
    private val REQUESTED_PERMISSION = arrayOf(
        android.Manifest.permission.RECORD_AUDIO,
        android.Manifest.permission.CAMERA
    )

    private fun checkPermission(): Boolean {
        return !(ContextCompat.checkSelfPermission(
            this,
            REQUESTED_PERMISSION[0]
        ) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            this,
            REQUESTED_PERMISSION[1]
        ) != PackageManager.PERMISSION_GRANTED
                )
    }

    private fun showMessage(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }


    private fun setupVideoSdkEngine() {
        try {
            val config = RtcEngineConfig()
            config.mContext = baseContext
            config.mAppId = appId
            config.mEventHandler = mRtcEventHandler
            agoraEngine = RtcEngine.create(config)
            agoraEngine!!.enableAudio()
        } catch (e: Exception) {
            e.message?.let { showMessage(it) }
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (!checkPermission()) {
            ActivityCompat.requestPermissions(this, REQUESTED_PERMISSION, PERMISSION_ID)
        }

        setupVideoSdkEngine()

        binding.joinButton.setOnClickListener {
            joinCall()
        }

        binding.leaveButton.setOnClickListener {
            leaveCall()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        agoraEngine!!.stopPreview()
        agoraEngine!!.leaveChannel()

        Thread {
            RtcEngine.destroy()
            agoraEngine = null
        }.start()
    }

    private fun leaveCall() {
        if (!isJoined) {
            showMessage("join a channel first")
        } else {
            agoraEngine!!.leaveChannel()
            showMessage("You left the channel")
            if (remoteSurfaceView != null) remoteSurfaceView!!.visibility = GONE
            if (localSurfaceView != null) localSurfaceView!!.visibility = GONE
            isJoined = false

        }
    }

    private fun joinCall() {
        if (checkPermission()) {
            val option = ChannelMediaOptions()
            option.channelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
            option.clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
            setupLocalVideo()
            localSurfaceView!!.visibility = VISIBLE
            agoraEngine!!.startPreview()
            agoraEngine!!.joinChannel(token, channelName, uid, option)
        } else {
            Toast.makeText(this, "Permission not granted", Toast.LENGTH_LONG).show()
        }
    }


    private val mRtcEventHandler: IRtcEngineEventHandler = object : IRtcEngineEventHandler() {
        override fun onUserJoined(uid: Int, elapsed: Int) {
            showMessage("remote user joined $uid")
            runOnUiThread { setupRemoteVideo(uid) }

        }

        override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
            isJoined = true
            showMessage("Joined channel $channel")

        }

        override fun onUserOffline(uid: Int, reason: Int) {
            showMessage("user offline")

            remoteSurfaceView!!.visibility = GONE
        }
    }

    private fun setupRemoteVideo(uid: Int) {
        remoteSurfaceView = SurfaceView(baseContext)
        remoteSurfaceView!!.setZOrderMediaOverlay(true)
        binding.remoteUser.addView(remoteSurfaceView)

        agoraEngine!!.setupRemoteVideo(
            VideoCanvas(
                remoteSurfaceView,
                VideoCanvas.RENDER_MODE_FIT,
                uid
            )
        )
    }

    private fun setupLocalVideo() {
        localSurfaceView = SurfaceView(baseContext)
        binding.localUser.addView(localSurfaceView)

        agoraEngine!!.setupLocalVideo(
            VideoCanvas(
                localSurfaceView,
                VideoCanvas.RENDER_MODE_FIT,
                0
            )
        )
    }
}