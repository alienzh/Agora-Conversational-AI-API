package io.agora.convoai.example.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import io.agora.convoai.example.startup.ui.common.SnackbarHelper
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.agora.convoai.example.R
import io.agora.convoai.example.databinding.ItemTranscriptAgentBinding
import io.agora.convoai.example.databinding.ItemTranscriptUserBinding
import io.agora.convoai.example.ui.common.BaseFragment
import io.agora.convoai.convoaiApi.Transcript
import io.agora.convoai.convoaiApi.TranscriptStatus
import io.agora.convoai.convoaiApi.TranscriptType
import kotlinx.coroutines.launch
import kotlin.text.ifEmpty
import androidx.core.graphics.toColorInt
import androidx.navigation.fragment.findNavController
import io.agora.convoai.example.databinding.FragmentAgentLivingBinding

/**
 * Fragment for displaying voice assistant UI and transcript list
 * This fragment only handles UI display, agent is started in AgentHomeFragment
 */
class AgentLivingFragment : BaseFragment<FragmentAgentLivingBinding>() {

    private val viewModel: ConversationViewModel by activityViewModels()
    private val transcriptAdapter: TranscriptAdapter = TranscriptAdapter()

    // Track whether to automatically scroll to bottom
    private var autoScrollToBottom = true
    private var isScrollBottom = false

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentAgentLivingBinding? {
        return FragmentAgentLivingBinding.inflate(inflater, container, false)
    }

    override fun initData() {
        super.initData()

        // Observe UI state changes
        observeUiState()

        // Observe transcript list changes
        observeTranscriptList()
    }

    override fun initView() {
        mBinding?.apply {
            setOnApplyWindowInsets(root)

            // Initialize UI with current state from ViewModel
            val currentState = viewModel.uiState.value
            tvChannel.text = "Channel: ${currentState.channelName}"
            tvUid.text = "UserId: ${currentState.userUid}"
            tvAgentUid.text = "AgentUid: ${currentState.agentUid}"

            // Setup RecyclerView for transcript list
            setupRecyclerView()

            btnMute.setOnClickListener {
                viewModel.toggleMute()
            }
            btnHangup.setOnClickListener {
                handleHangup()
            }
        }
    }

    /**
     * Setup RecyclerView for transcript list
     */
    private fun setupRecyclerView() {
        mBinding?.rvTranscript?.apply {
            layoutManager = LinearLayoutManager(requireContext()).apply {
                reverseLayout = false
            }
            adapter = transcriptAdapter
            itemAnimator = null
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)
                    when (newState) {
                        RecyclerView.SCROLL_STATE_IDLE -> {
                            // Check if at bottom when scrolling stops
                            isScrollBottom = !recyclerView.canScrollVertically(1)
                            if (isScrollBottom) {
                                autoScrollToBottom = true
                                isScrollBottom = true
                            }
                        }

                        RecyclerView.SCROLL_STATE_DRAGGING -> {
                            // When user actively drags
                            autoScrollToBottom = false
                        }
                    }
                }

                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    // Show button when scrolling up a significant distance
                    if (dy < -50) {
                        if (recyclerView.canScrollVertically(1)) {
                            autoScrollToBottom = false
                        }
                    }
                }
            })
        }
    }

    override fun onHandleOnBackPressed() {
        // Handle back press (including swipe back gesture) same as hangup button
        handleHangup()
    }

    private fun handleHangup() {
        viewModel.hangup()
        if (isAdded) {
            findNavController().popBackStack()
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                mBinding?.apply {
                    // Update channel, user, agent info from state
                    if (state.channelName.isNotEmpty()) {
                        tvChannel.text = "Channel: ${state.channelName}"
                    }
                    if (state.userUid != 0) {
                        tvUid.text = "UserId: ${state.userUid}"
                    }
                    if (state.agentUid != 0) {
                        tvAgentUid.text = "AgentUid: ${state.agentUid}"
                    }

                    // Update mute button UI

                    btnMute.setImageResource(
                        if (state.isMuted) R.drawable.ic_mic_off else R.drawable.ic_mic
                    )
                    val muteBackground = if (state.isMuted) {
                        R.drawable.bg_button_mute_muted_selector
                    } else {
                        R.drawable.bg_button_mute_selector
                    }
                    btnMute.setBackgroundResource(muteBackground)
                }

                // Show status messages via Snackbar (only if fragment is visible)

                if (isAdded && isResumed) {
                    when {
                        state.connectionState == ConversationViewModel.ConnectionState.Error -> {
                            SnackbarHelper.showError(this@AgentLivingFragment, state.statusMessage)
                        }
                        state.statusMessage.isNotEmpty() -> {
                            SnackbarHelper.showNormal(this@AgentLivingFragment, state.statusMessage)
                        }
                    }
                }
            }
        }

        // Observe agent state
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.agentState.collect { agentState ->
                mBinding?.apply {
                    agentState?.let {
                        // Update agent status text using state.value
                        tvAgentStatus.text = it.value
                    } ?: run {
                        // Agent state is null, show default text
                        tvAgentStatus.text = "Unknown"
                    }
                }
            }
        }
    }

    private fun observeTranscriptList() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.transcriptList.collect { transcriptList ->
                // Update transcript list
                transcriptAdapter.submitList(transcriptList)
                if (autoScrollToBottom) {
                    scrollToBottom()
                }
            }
        }
    }

    /**
     * Scroll RecyclerView to the bottom to show latest transcript
     */
    private fun scrollToBottom() {
        mBinding?.rvTranscript?.apply {
            val lastPosition = transcriptAdapter.itemCount - 1
            if (lastPosition < 0) return

            stopScroll()
            val layoutManager = layoutManager as? LinearLayoutManager ?: return

            // Use single post call to handle all scrolling logic
            post {
                layoutManager.scrollToPosition(lastPosition)

                // Handle extra-long messages that exceed viewport height
                val lastView = layoutManager.findViewByPosition(lastPosition)
                if (lastView != null && lastView.height > height) {
                    val offset = height - lastView.height
                    layoutManager.scrollToPositionWithOffset(lastPosition, offset)
                }

                isScrollBottom = true
            }
        }
    }

}

/**
 * Adapter for displaying transcript list with different view types for USER and AGENT
 */
class TranscriptAdapter : ListAdapter<Transcript, RecyclerView.ViewHolder>(TranscriptDiffCallback()) {

    companion object {
        private const val VIEW_TYPE_USER = 0
        private const val VIEW_TYPE_AGENT = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position).type) {
            TranscriptType.USER -> VIEW_TYPE_USER
            TranscriptType.AGENT -> VIEW_TYPE_AGENT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_USER -> {
                UserViewHolder(ItemTranscriptUserBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            }

            VIEW_TYPE_AGENT -> {
                AgentViewHolder(ItemTranscriptAgentBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            }

            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val transcript = getItem(position)
        when (holder) {
            is UserViewHolder -> holder.bind(transcript)
            is AgentViewHolder -> holder.bind(transcript)
        }
    }

    /**
     * ViewHolder for USER transcript items
     */
    class UserViewHolder(private val binding: ItemTranscriptUserBinding) : RecyclerView.ViewHolder(binding.root) {
        private val tvType: TextView = binding.tvTranscriptType
        private val tvText: TextView = binding.tvTranscriptText
        private val tvStatus: TextView = binding.tvTranscriptStatus

        fun bind(transcript: Transcript) {
            // Set transcript type badge with green color for USER
            tvType.text = "USER"
            ContextCompat.getDrawable(binding.root.context, R.drawable.bg_type_badge)?.let { drawable ->
                drawable.setTint("#10B981".toColorInt())
                tvType.background = drawable
            }

            // Set transcript text
            tvText.text = transcript.text.ifEmpty { "(empty)" }

            // Set transcript status with appropriate color
            val (statusText, statusColor) = when (transcript.status) {
                TranscriptStatus.IN_PROGRESS -> "IN PROGRESS" to "#FF9800".toColorInt()
                TranscriptStatus.END -> "END" to "#4CAF50".toColorInt()
                TranscriptStatus.INTERRUPTED -> "INTERRUPTED" to "#F44336".toColorInt()
                TranscriptStatus.UNKNOWN -> "UNKNOWN" to "#9E9E9E".toColorInt()
            }
            tvStatus.text = statusText
            tvStatus.setTextColor(statusColor)
        }
    }

    /**
     * ViewHolder for AGENT transcript items
     */
    class AgentViewHolder(private val binding: ItemTranscriptAgentBinding) : RecyclerView.ViewHolder(binding.root) {
        private val tvType: TextView = binding.tvTranscriptType
        private val tvText: TextView = binding.tvTranscriptText
        private val tvStatus: TextView = binding.tvTranscriptStatus

        fun bind(transcript: Transcript) {
            // Set transcript type badge with indigo color for AGENT
            tvType.text = "AGENT"
            ContextCompat.getDrawable(binding.root.context, R.drawable.bg_type_badge)?.let { drawable ->
                drawable.setTint("#6366F1".toColorInt())
                tvType.background = drawable
            }

            // Set transcript text
            tvText.text = transcript.text.ifEmpty { "(empty)" }

            // Set transcript status with appropriate color
            val (statusText, statusColor) = when (transcript.status) {
                TranscriptStatus.IN_PROGRESS -> "IN PROGRESS" to "#FF9800".toColorInt()
                TranscriptStatus.END -> "END" to "#4CAF50".toColorInt()
                TranscriptStatus.INTERRUPTED -> "INTERRUPTED" to "#F44336".toColorInt()
                TranscriptStatus.UNKNOWN -> "UNKNOWN" to "#9E9E9E".toColorInt()
            }
            tvStatus.text = statusText
            tvStatus.setTextColor(statusColor)
        }
    }

    private class TranscriptDiffCallback : DiffUtil.ItemCallback<Transcript>() {
        override fun areItemsTheSame(oldItem: Transcript, newItem: Transcript): Boolean {
            return oldItem.turnId == newItem.turnId && oldItem.type == newItem.type
        }

        override fun areContentsTheSame(oldItem: Transcript, newItem: Transcript): Boolean {
            return oldItem == newItem
        }
    }
}


