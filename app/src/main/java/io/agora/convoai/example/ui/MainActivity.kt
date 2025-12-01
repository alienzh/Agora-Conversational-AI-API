package io.agora.convoai.example.ui

import androidx.navigation.fragment.NavHostFragment
import io.agora.convoai.example.R
import io.agora.convoai.example.databinding.ActivityMainBinding
import io.agora.convoai.example.tools.PermissionHelp
import io.agora.convoai.example.ui.common.BaseActivity

class MainActivity : BaseActivity<ActivityMainBinding>() {

    private lateinit var mPermissionHelp: PermissionHelp

    override fun getViewBinding(): ActivityMainBinding {
        return ActivityMainBinding.inflate(layoutInflater)
    }

    override fun initView() {
        // MainActivity is now just a container for fragments
        // Navigation is handled by NavHostFragment automatically
        mPermissionHelp = PermissionHelp(this)
    }

    override fun onHandleOnBackPressed() {
        // Navigation Component handles back press automatically
        // If Navigation can't handle it (at start destination), finish the activity
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.navHostFragment) as? NavHostFragment
        val navController = navHostFragment?.navController

        if (navController?.navigateUp() == false) {
            // If Navigation can't navigate up (at start destination), finish the activity
            super.onHandleOnBackPressed()
        }
    }

    fun checkMicrophonePermission(granted: (Boolean) -> Unit) {
        if (mPermissionHelp.hasMicPerm()) {
            granted.invoke(true)
        } else {
            mPermissionHelp.checkMicPerm(
                granted = { granted.invoke(true) },
                unGranted = {
                    showPermissionDialog(
                        "Permission Required",
                        "Microphone permission is required for voice chat. Please grant the permission to continue.",
                        onResult = {
                            if (it) {
                                mPermissionHelp.launchAppSettingForMic(
                                    granted = { granted.invoke(true) },
                                    unGranted = { granted.invoke(false) }
                                )
                            } else {
                                granted.invoke(false)
                            }
                        }
                    )
                }
            )
        }

    }

    private fun showPermissionDialog(title: String, content: String, onResult: (Boolean) -> Unit) {
        if (isFinishing || isDestroyed || supportFragmentManager.isStateSaved) return

        CommonDialog.Builder()
            .setTitle(title)
            .setContent(content)
            .setPositiveButton("Retry") {
                onResult.invoke(true)
            }
            .setNegativeButton("Exit") {
                onResult.invoke(false)
            }
            .setCancelable(false)
            .build()
            .show(supportFragmentManager, "permission_dialog")
    }
}
