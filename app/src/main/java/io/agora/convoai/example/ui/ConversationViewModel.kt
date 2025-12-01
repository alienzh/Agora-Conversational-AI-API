package io.agora.convoai.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.agora.convoai.example.rtm.RtmManager
import io.agora.convoai.example.rtm.IRtmManagerListener
import io.agora.convoai.example.rtc.RtcManager
import io.agora.convoai.example.api.AgentStarter
import io.agora.convoai.example.api.TokenGenerator
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngineEx
import io.agora.rtm.RtmClient
import io.agora.convoai.convoaiApi.AgentState
import io.agora.convoai.convoaiApi.ConversationalAIAPIConfig
import io.agora.convoai.convoaiApi.ConversationalAIAPIImpl
import io.agora.convoai.convoaiApi.IConversationalAIAPI
import io.agora.convoai.convoaiApi.IConversationalAIAPIEventHandler
import io.agora.convoai.convoaiApi.InterruptEvent
import io.agora.convoai.convoaiApi.MessageError
import io.agora.convoai.convoaiApi.MessageReceipt
import io.agora.convoai.convoaiApi.Metric
import io.agora.convoai.convoaiApi.ModuleError
import io.agora.convoai.convoaiApi.StateChangeEvent
import io.agora.convoai.convoaiApi.Transcript
import io.agora.convoai.convoaiApi.TranscriptRenderMode
import io.agora.convoai.convoaiApi.VoiceprintStateChangeEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for managing conversation-related business logic
 */
class ConversationViewModel : ViewModel() {

    companion object {
        private const val TAG = "ConversationViewModel"
        const val userId = 1001086
        const val agentUid = 1009527

        /**
         * Generate a random channel name
         */
        fun generateRandomChannelName(): String {
            return "channel_kotlin_${(1000..9999).random()}"
        }
    }

    /**
     * Connection state enum
     */
    enum class ConnectionState {
        Idle,
        Connecting,
        Connected,
        Error
    }

    // UI State - shared between AgentHomeFragment and VoiceAssistantFragment
    data class ConversationUiState constructor(
        val statusMessage: String = "",
        val isMuted: Boolean = false,
        // Channel and user info
        val channelName: String = "",
        val userUid: Int = 0,
        val agentUid: Int = 0,
        // Connection state
        val connectionState: ConnectionState = ConnectionState.Idle,
        // Agent state
        val agentStarted: Boolean = false
    )

    private val _uiState = MutableStateFlow(ConversationUiState())
    val uiState: StateFlow<ConversationUiState> = _uiState.asStateFlow()

    // Transcript list - separate from UI state
    private val _transcriptList = MutableStateFlow<List<Transcript>>(emptyList())
    val transcriptList: StateFlow<List<Transcript>> = _transcriptList.asStateFlow()

    private val _agentState = MutableStateFlow<AgentState?>(null)
    val agentState: StateFlow<AgentState?> = _agentState.asStateFlow()

    private var unifiedToken: String? = null

    private var conversationalAIAPI: IConversationalAIAPI? = null

    private var channelName: String = ""

    private var rtcJoined = false
    private var rtmLoggedIn = false

    // Agent management
    private var agentId: String? = null
    private var agentStarted = false

    private val rtcEventHandler = object : IRtcEngineEventHandler() {
        override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
            viewModelScope.launch {
                rtcJoined = true
                _uiState.value = _uiState.value.copy(
                    statusMessage = "Joined RTC channel successfully"
                )
                Log.d(TAG, "RTC joined channel: $channel, uid: $uid")
                checkJoinAndLoginComplete()
            }
        }

        override fun onUserJoined(uid: Int, elapsed: Int) {
            viewModelScope.launch {
                if (uid == agentUid) {
                    _uiState.value = _uiState.value.copy(
                        statusMessage = "Agent joined the channel",
                        agentUid = uid
                    )
                    Log.d(TAG, "Agent joined the channel, uid: $uid")
                } else {
                    Log.d(TAG, "User joined the channel, uid: $uid")
                }
            }
        }

        override fun onUserOffline(uid: Int, reason: Int) {
            viewModelScope.launch {
                if (uid == agentUid) {
                    _uiState.value = _uiState.value.copy(
                        statusMessage = "Agent left the channel"
                    )
                    Log.d(TAG, "Agent left the channel, uid: $uid, reason: $reason")
                } else {
                    Log.d(TAG, "User left the channel, uid: $uid, reason: $reason")
                }
            }
        }

        override fun onError(err: Int) {
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(
                    connectionState = ConnectionState.Error,
                    statusMessage = "RTC error: $err"
                )
                Log.e(TAG, "RTC error: $err")
            }
        }

        override fun onTokenPrivilegeWillExpire(token: String?) {
            renewToken()
        }
    }

    // RTM listener
    private val rtmListener = object : IRtmManagerListener {
        override fun onFailed() {
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(
                    connectionState = ConnectionState.Error,
                    statusMessage = "RTM connection failed, attempting re-login"
                )
                Log.d(TAG, "RTM connection failed, attempting re-login with new token")
            }
            unifiedToken = null
        }

        override fun onTokenPrivilegeWillExpire(channelName: String) {
            renewToken()
        }
    }

    private fun renewToken() {
        viewModelScope.launch {
            val token = generateUnifiedToken(isSilent = true)
            if (token != null) {
                RtcManager.renewRtcToken(token)
                RtmManager.renewToken(token) { error ->
                    if (error != null) {
                        unifiedToken = null
                    }
                }
            } else {
                Log.e(TAG, "Failed to renew token")
            }
        }
    }

    private val conversationalAIAPIEventHandler = object : IConversationalAIAPIEventHandler {
        override fun onAgentStateChanged(agentUserId: String, event: StateChangeEvent) {
            _agentState.value = event.state
        }

        override fun onAgentInterrupted(agentUserId: String, event: InterruptEvent) {
            // Handle interruption

        }

        override fun onAgentMetrics(agentUserId: String, metric: Metric) {
            // Handle metrics
        }

        override fun onAgentError(agentUserId: String, error: ModuleError) {
            // Handle agent error
        }

        override fun onMessageError(agentUserId: String, error: MessageError) {
            // Handle message error
        }

        override fun onTranscriptUpdated(agentUserId: String, transcript: Transcript) {
            // Handle transcript updates with typing animation for agent messages
            addTranscript(transcript)
        }

        override fun onMessageReceiptUpdated(agentUserId: String, receipt: MessageReceipt) {
            // Handle message receipt
        }

        override fun onAgentVoiceprintStateChanged(agentUserId: String, event: VoiceprintStateChangeEvent) {
            // Update voice print state to notify Activity

        }

        override fun onDebugLog(log: String) {
            Log.d("conversationalAIAPI", log)
        }
    }

    init {
        // Create RTC engine and RTM client during initialization
        try {
            Log.d(TAG, "Initializing RTC engine and RTM client...")

            val rtcEngine = RtcManager.createRtcEngine(rtcEventHandler)
            val rtmClient = RtmManager.createRtmClient(userId)
            // Setup RTM listener
            RtmManager.addListener(rtmListener)

            if (rtcEngine != null) {
                initializeAPIs(rtcEngine, rtmClient)
                Log.d(TAG, "RTC engine and RTM client created successfully")
            } else {
                Log.e(TAG, "Failed to create RTC engine")
                _uiState.value = _uiState.value.copy(
                    statusMessage = "Failed to create RTC engine"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating RTC/RTM instances: ${e.message}", e)
            _uiState.value = _uiState.value.copy(
                statusMessage = "Error creating RTC/RTM: ${e.message}"
            )
        }
    }

    private fun initializeAPIs(rtcEngine: RtcEngineEx, rtmClient: RtmClient) {
        conversationalAIAPI = ConversationalAIAPIImpl(
            ConversationalAIAPIConfig(
                rtcEngine = rtcEngine,
                rtmClient = rtmClient,
                enableLog = true,
                renderMode = TranscriptRenderMode.Text
            )
        )
        conversationalAIAPI?.loadAudioSettings(Constants.AUDIO_SCENARIO_AI_CLIENT)
        conversationalAIAPI?.addHandler(conversationalAIAPIEventHandler)
    }

    /**
     * Check if both RTC and RTM are connected, then start agent
     */
    private fun checkJoinAndLoginComplete() {
        if (rtcJoined && rtmLoggedIn) {
            _uiState.value = _uiState.value.copy(
                statusMessage = "RTC and RTM connected successfully"
            )
            startAgent()
        }
    }

    /**
     * Start agent (called automatically after RTC and RTM are connected)
     */
    fun startAgent() {
        viewModelScope.launch {
            if (agentStarted) {
                Log.d(TAG, "Agent already started, agentId: $agentId")
                return@launch
            }

            if (channelName.isEmpty()) {
                _uiState.value = _uiState.value.copy(
                    connectionState = ConnectionState.Error,
                    statusMessage = "Channel name is empty, cannot start agent"
                )
                Log.e(TAG, "Channel name is empty, cannot start agent")
                return@launch
            }

            _uiState.value = _uiState.value.copy(
                statusMessage = "Generating agent token..."
            )

            // Generate token for agent (always required)
            val tokenResult = TokenGenerator.generateTokensAsync(
                channelName = channelName,
                uid = agentUid.toString()
            )

            val agentToken = tokenResult.fold(
                onSuccess = { token -> token },
                onFailure = { exception ->
                    _uiState.value = _uiState.value.copy(
                        connectionState = ConnectionState.Error,
                        statusMessage = "Failed to generate agent token: ${exception.message}"
                    )
                    Log.e(TAG, "Failed to generate agent token: ${exception.message}", exception)
                    return@launch
                }
            )

            _uiState.value = _uiState.value.copy(
                statusMessage = "Starting agent..."
            )

            val startAgentResult = AgentStarter.startAgentAsync(
                channelName = channelName,
                agentRtcUid = agentUid.toString(),
                token = agentToken
            )
            startAgentResult.fold(
                onSuccess = { agentId ->
                    this@ConversationViewModel.agentId = agentId
                    agentStarted = true
                    _uiState.value = _uiState.value.copy(
                        connectionState = ConnectionState.Connected,
                        agentStarted = true,
                        statusMessage = "Agent started successfully"
                    )
                    Log.d(TAG, "Agent started successfully, agentId: $agentId")
                },
                onFailure = { exception ->
                    _uiState.value = _uiState.value.copy(
                        connectionState = ConnectionState.Error,
                        statusMessage = "Failed to start agent: ${exception.message}"
                    )
                    Log.e(TAG, "Failed to start agent: ${exception.message}", exception)
                }
            )
        }
    }

    /**
     * Generate unified token for RTC and RTM
     *
     * @param isSilent Whether to suppress UI status updates (default: false)
     * @return Token string on success, null on failure (UI state is updated on failure regardless of isSilent)
     */
    private suspend fun generateUnifiedToken(isSilent: Boolean = false): String? {
        if (!isSilent) {
            _uiState.value = _uiState.value.copy(
                statusMessage = "Getting token..."
            )
        }

        // Get unified token for both RTC and RTM
        val tokenResult = TokenGenerator.generateTokensAsync(
            channelName = "",
            uid = userId.toString(),
        )

        return tokenResult.fold(
            onSuccess = { token ->
                unifiedToken = token
                token
            },
            onFailure = { exception ->
                _uiState.value = _uiState.value.copy(
                    connectionState = ConnectionState.Error,
                    statusMessage = "Failed to get token: ${exception.message}"
                )
                Log.e(TAG, "Failed to get token: ${exception.message}", exception)
                null
            }
        )
    }

    /**
     * Join RTC channel and login RTM
     * @param channelName Channel name to join
     */
    fun joinChannelAndLogin(channelName: String) {
        viewModelScope.launch {
            try {
                if (channelName.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        statusMessage = "Channel name cannot be empty",
                        connectionState = ConnectionState.Idle
                    )
                    Log.e(TAG, "Channel name is empty, cannot join channel")
                    return@launch
                }

                this@ConversationViewModel.channelName = channelName
                rtcJoined = false
                rtmLoggedIn = false

                _uiState.value = _uiState.value.copy(
                    channelName = channelName,
                    userUid = userId,
                    connectionState = ConnectionState.Connecting,
                    statusMessage = "Joining channel and logging in..."
                )

                // Get token if not available, otherwise use existing token
                val token = unifiedToken ?: generateUnifiedToken() ?: return@launch

                // Join RTC channel with the unified token
                RtcManager.joinChannel(token, channelName, userId)

                // Login RTM with the same unified token
                RtmManager.login(token) { exception ->
                    viewModelScope.launch {
                        if (exception == null) {
                            rtmLoggedIn = true
                            _uiState.value = _uiState.value.copy(
                                statusMessage = "RTM logged in successfully"
                            )
                            conversationalAIAPI?.subscribeMessage(channelName) { errorInfo ->
                                if (errorInfo != null) {
                                    Log.e(TAG, "Subscribe message error: ${errorInfo}")
                                }
                            }
                            checkJoinAndLoginComplete()
                        } else {
                            _uiState.value = _uiState.value.copy(
                                connectionState = ConnectionState.Error,
                                statusMessage = "RTM login failed: ${exception.message}"
                            )
                            Log.e(TAG, "RTM login failed: ${exception.message}", exception)
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    connectionState = ConnectionState.Error,
                    statusMessage = "Error: ${e.message}"
                )
                Log.e(TAG, "Error joining channel/login: ${e.message}", e)
            }
        }
    }

    /**
     * Toggle microphone mute state
     */
    fun toggleMute() {
        val newMuteState = !_uiState.value.isMuted
        _uiState.value = _uiState.value.copy(
            isMuted = newMuteState,
            statusMessage = if (newMuteState) "Microphone muted" else "Microphone unmuted"
        )
        RtcManager.muteLocalAudio(newMuteState)
        Log.d(TAG, "Microphone muted: $newMuteState")
    }

    /**
     * Add a new transcript to the list
     */
    fun addTranscript(transcript: Transcript) {
        viewModelScope.launch {
            val currentList = _transcriptList.value.toMutableList()
            // Update existing transcript if same turnId, otherwise add new
            val existingIndex =
                currentList.indexOfFirst { it.turnId == transcript.turnId && it.type == transcript.type }
            if (existingIndex >= 0) {
                currentList[existingIndex] = transcript
            } else {
                currentList.add(transcript)
            }
            _transcriptList.value = currentList
        }
    }

    /**
     * Clear all transcripts
     */
    fun clearTranscripts() {
        viewModelScope.launch {
            _transcriptList.value = emptyList()
            Log.d(TAG, "Transcripts cleared")
        }
    }

    /**
     * Hang up and cleanup connections
     */
    fun hangup() {
        viewModelScope.launch {
            try {
                conversationalAIAPI?.unsubscribeMessage(channelName) { errorInfo ->
                    if (errorInfo != null) {
                        Log.e(TAG, "Unsubscribe message error: ${errorInfo}")
                    }
                }

                // Stop agent if it was started
                if (agentStarted && agentId != null) {
                    _uiState.value = _uiState.value.copy(
                        statusMessage = "Stopping agent..."
                    )
                    val stopResult = AgentStarter.stopAgentAsync(
                        agentId = agentId!!
                    )
                    stopResult.fold(
                        onSuccess = {
                            Log.d(TAG, "Agent stopped successfully")
                        },
                        onFailure = { exception ->
                            Log.e(TAG, "Failed to stop agent: ${exception.message}", exception)
                        }
                    )
                    agentId = null
                    agentStarted = false
                }

                RtcManager.leaveChannel()
                RtmManager.logout()
                rtcJoined = false
                rtmLoggedIn = false
                _uiState.value = _uiState.value.copy(
                    statusMessage = "",
                    connectionState = ConnectionState.Idle,
                    agentStarted = false
                )
                clearTranscripts()
                Log.d(TAG, "Hangup completed")
            } catch (e: Exception) {
                Log.e(TAG, "Error during hangup: ${e.message}", e)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        RtcManager.leaveChannel()
        RtmManager.logout()
        // Note: RtcEngine.destroy() should be called carefully as it's a global operation
        // Consider managing RTC engine lifecycle at Application level
    }
}

