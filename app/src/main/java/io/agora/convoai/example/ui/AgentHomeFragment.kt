package io.agora.convoai.example.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import io.agora.convoai.example.startup.ui.common.SnackbarHelper
import io.agora.convoai.example.R
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import io.agora.convoai.example.KeyCenter
import io.agora.convoai.example.databinding.FragmentAgentHomeBinding
import io.agora.convoai.example.ui.common.BaseFragment
import kotlinx.coroutines.launch

/**
 * Fragment for initializing and starting the agent
 * Handles channel joining, RTM login, and agent startup
 */
class AgentHomeFragment : BaseFragment<FragmentAgentHomeBinding>() {

    private val viewModel: ConversationViewModel by activityViewModels()
    private var hasNavigated = false

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentAgentHomeBinding {
        return FragmentAgentHomeBinding.inflate(inflater, container, false)
    }

    override fun initView() {
        mBinding?.apply {
            setOnApplyWindowInsets(root)

            // Display App ID first 5 characters
            val appIdPrefix = KeyCenter.AGORA_APP_ID.take(5) + "***"
            tvAppId.text = appIdPrefix

            // Display Pipeline ID first 5 characters
            val pipelineIdPrefix = KeyCenter.PIPELINE_ID.take(5) + "***"
            tvPipelineId.text = pipelineIdPrefix

            btnStarter.setOnClickListener {
                // Generate random channel name each time joining channel
                val channelName = ConversationViewModel.generateRandomChannelName()

                // Check microphone permission before joining channel
                val mainActivity = activity as? MainActivity ?: return@setOnClickListener
                mainActivity.checkMicrophonePermission { granted ->
                    if (granted) {
                        viewModel.joinChannelAndLogin(channelName)
                    } else {
                        SnackbarHelper.showError(
                            this@AgentHomeFragment,
                            "Microphone permission is required to join channel"
                        )
                    }
                }
            }
        }

        // Observe UI state changes
        observeUiState()
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                mBinding?.apply {
                    // Reset hasNavigated flag when disconnected (after hangup)
                    if (state.connectionState != ConversationViewModel.ConnectionState.Connected && hasNavigated) {
                        hasNavigated = false
                    }

                    // Update UI state
                    val isConnecting = state.connectionState == ConversationViewModel.ConnectionState.Connecting
                    val isError = state.connectionState == ConversationViewModel.ConnectionState.Error
                    
                    // Update button state
                    btnStarter.isEnabled = !isConnecting && !isError
                    
                    // Update button loading state (simple text-based approach)
                    if (isConnecting) {
                        btnStarter.text = "Starting..."
                    } else {
                        btnStarter.text = "Start Agent"
                    }
                    
                    // Show error message if connection failed
                    if (isError && state.statusMessage.isNotEmpty() && isAdded && isResumed) {
                        SnackbarHelper.showError(this@AgentHomeFragment, state.statusMessage)
                    }

                    // Navigate to agent living when agent is started successfully (only once)
                    if (state.connectionState == ConversationViewModel.ConnectionState.Connected &&
                        state.agentStarted &&
                        !hasNavigated
                    ) {
                        hasNavigated = true
                        findNavController().navigate(R.id.action_agentHome_to_agentLiving)
                    }
                }
            }
        }
    }
}