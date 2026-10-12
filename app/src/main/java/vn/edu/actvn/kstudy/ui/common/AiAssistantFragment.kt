package vn.edu.actvn.kstudy.ui.common

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import vn.edu.actvn.kstudy.R
import vn.edu.actvn.kstudy.ui.adapter.ChatMessageAdapter
import vn.edu.actvn.kstudy.viewmodel.AiChatViewModel
import vn.edu.actvn.kstudy.viewmodel.AuthViewModel
import vn.edu.actvn.kstudy.viewmodel.KstudyViewModelFactory

// Chat screen of the AI assistant, shared by all three roles
class AiAssistantFragment : Fragment() {

    private var showBackButton: Boolean = false
    private var previousSoftInputMode: Int = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_UNSPECIFIED

    private lateinit var viewModel: AiChatViewModel

    companion object {
        // showBackButton: true when the screen is opened on top of another one instead of from a bottom tab
        fun newInstance(showBackButton: Boolean = false): AiAssistantFragment {
            return AiAssistantFragment().apply {
                arguments = Bundle().apply {
                    putBoolean("showBackButton", showBackButton)
                }
            }
        }
    }

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        arguments?.let {
            showBackButton = it.getBoolean("showBackButton", false)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        saved: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_ai_assistant, container, false)

        // Scoped to the activity so the conversation survives switching between bottom tabs
        viewModel = ViewModelProvider(requireActivity(), KstudyViewModelFactory)[AiChatViewModel::class.java]
        val authViewModel = ViewModelProvider(requireActivity())[AuthViewModel::class.java]

        val btnBack = view.findViewById<View>(R.id.btnBackAi)
        val btnClear = view.findViewById<View>(R.id.btnClearChat)
        val rv = view.findViewById<RecyclerView>(R.id.rvChat)
        val tvTyping = view.findViewById<TextView>(R.id.tvAiTyping)
        val layoutSuggestions = view.findViewById<LinearLayout>(R.id.layoutAiSuggestions)
        val etMessage = view.findViewById<EditText>(R.id.etAiMessage)
        val btnSend = view.findViewById<View>(R.id.btnSendAi)

        btnBack.visibility = if (showBackButton) View.VISIBLE else View.GONE
        btnBack.setOnClickListener {
            hideKeyboard(etMessage)
            parentFragmentManager.popBackStack()
        }

        rv.layoutManager = LinearLayoutManager(requireContext()).apply { stackFromEnd = true }
        val adapter = ChatMessageAdapter(emptyList())
        rv.adapter = adapter
        // Keep the latest message visible when the keyboard shrinks the list
        rv.addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, oldBottom ->
            if (bottom < oldBottom && adapter.itemCount > 0) {
                rv.scrollToPosition(adapter.itemCount - 1)
            }
        }

        authViewModel.currentUser.observe(viewLifecycleOwner) { user ->
            if (user != null) {
                viewModel.start(user.id, user.role, user.fullName)
            }
        }

        // A question can only be sent once the signed-in user is known and no answer is pending.
        // Until then the send button and the suggestions are disabled and the reason is shown,
        // instead of silently dropping what the user typed.
        fun updateInputState() {
            val ready = viewModel.isReady.value == true
            val canSend = ready && viewModel.isSending.value != true
            tvTyping.text = if (ready) "Trợ lý đang trả lời…" else "Đang tải thông tin người dùng…"
            tvTyping.visibility = if (canSend) View.GONE else View.VISIBLE
            btnSend.isEnabled = canSend
            btnSend.alpha = if (canSend) 1f else 0.4f
            for (i in 0 until layoutSuggestions.childCount) {
                val chip = layoutSuggestions.getChildAt(i)
                chip.isEnabled = canSend
                chip.alpha = if (canSend) 1f else 0.5f
            }
        }

        viewModel.suggestions.observe(viewLifecycleOwner) { suggestions ->
            showSuggestions(layoutSuggestions, suggestions)
            updateInputState()
        }

        viewModel.messages.observe(viewLifecycleOwner) { messages ->
            adapter.updateList(messages)
            if (messages.isNotEmpty()) {
                rv.scrollToPosition(messages.size - 1)
            }
        }

        viewModel.isReady.observe(viewLifecycleOwner) {
            updateInputState()
        }

        viewModel.isSending.observe(viewLifecycleOwner) {
            updateInputState()
        }

        btnSend.setOnClickListener {
            val text = etMessage.text.toString().trim()
            // The text is only cleared when the question was really accepted
            if (text.isNotEmpty() && viewModel.sendMessage(text)) {
                etMessage.text.clear()
            }
        }

        btnClear.setOnClickListener {
            viewModel.clearConversation()
        }

        // The activity is drawn edge-to-edge on new Android versions, so the keyboard does not
        // resize the window by itself: lift the input row by the part of the keyboard that
        // rises above this fragment's bottom edge (the bottom navigation bar sits below it).
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val imeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val location = IntArray(2)
            v.getLocationInWindow(location)
            val spaceBelow = v.rootView.height - (location[1] + v.height)
            v.setPadding(0, 0, 0, (imeHeight - spaceBelow).coerceAtLeast(0))
            insets
        }

        return view
    }

    // Still needed on Android versions where the activity is not drawn edge-to-edge:
    // there the system resizes the window and the insets listener above receives no keyboard inset.
    @Suppress("DEPRECATION")
    override fun onResume() {
        super.onResume()
        val window = requireActivity().window
        previousSoftInputMode = window.attributes.softInputMode
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    override fun onPause() {
        super.onPause()
        requireActivity().window.setSoftInputMode(previousSoftInputMode)
    }

    private fun showSuggestions(container: LinearLayout, suggestions: List<String>) {
        container.removeAllViews()
        for (question in suggestions) {
            val chip = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_ai_suggestion, container, false) as TextView
            chip.text = question
            chip.setOnClickListener {
                viewModel.sendMessage(question)
            }
            container.addView(chip)
        }
    }

    private fun hideKeyboard(view: View) {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }
}
