package com.termux.app.ui.fragments;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.termux.R;
import com.termux.app.ai.AIAgent;
import com.termux.app.ai.ChatMessage;
import com.termux.app.ui.adapters.ChatAdapter;

import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment {

    private RecyclerView chatMessagesList;
    private LinearLayout welcomeContainer;
    private LinearLayout typingIndicator;
    private EditText messageInput;
    private FrameLayout sendButton;
    private ChatAdapter chatAdapter;
    private List<ChatMessage> messages = new ArrayList<>();
    private AIAgent aiAgent;

    public static HomeFragment newInstance() {
        return new HomeFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViews(view);
        setupRecyclerView();
        setupListeners();
        initAIAgent();
    }

    private void initViews(View view) {
        chatMessagesList = view.findViewById(R.id.chat_messages_list);
        welcomeContainer = view.findViewById(R.id.welcome_container);
        typingIndicator = view.findViewById(R.id.typing_indicator);
        messageInput = view.findViewById(R.id.message_input);
        sendButton = view.findViewById(R.id.send_button);

        // Quick action chips
        TextView quickTerminal = view.findViewById(R.id.quick_action_terminal);
        TextView quickFile = view.findViewById(R.id.quick_action_file);
        TextView quickSearch = view.findViewById(R.id.quick_action_search);

        if (quickTerminal != null) {
            quickTerminal.setOnClickListener(v -> setInputText("Run a terminal command: "));
        }
        if (quickFile != null) {
            quickFile.setOnClickListener(v -> setInputText("Create a file called "));
        }
        if (quickSearch != null) {
            quickSearch.setOnClickListener(v -> setInputText("Search the web for "));
        }
    }

    private void setupRecyclerView() {
        chatAdapter = new ChatAdapter(messages);
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        layoutManager.setStackFromEnd(true);
        chatMessagesList.setLayoutManager(layoutManager);
        chatMessagesList.setAdapter(chatAdapter);
    }

    private void setupListeners() {
        sendButton.setOnClickListener(v -> sendMessage());

        messageInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage();
                return true;
            }
            return false;
        });

        messageInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                updateSendButtonState(s.length() > 0);
            }
        });
    }

    private void initAIAgent() {
        aiAgent = new AIAgent(getContext());
    }

    private void setInputText(String text) {
        messageInput.setText(text);
        messageInput.setSelection(text.length());
        messageInput.requestFocus();
    }

    private void updateSendButtonState(boolean enabled) {
        sendButton.setAlpha(enabled ? 1.0f : 0.5f);
        sendButton.setEnabled(enabled);
    }

    private void sendMessage() {
        String text = messageInput.getText().toString().trim();
        if (text.isEmpty()) return;

        // Hide welcome screen
        welcomeContainer.setVisibility(View.GONE);
        chatMessagesList.setVisibility(View.VISIBLE);

        // Add user message
        ChatMessage userMessage = new ChatMessage(text, true);
        messages.add(userMessage);
        chatAdapter.notifyItemInserted(messages.size() - 1);
        chatMessagesList.scrollToPosition(messages.size() - 1);

        // Clear input
        messageInput.setText("");

        // Show typing indicator
        showTypingIndicator(true);

        // Send to AI
        aiAgent.sendMessage(text, new AIAgent.AIResponseCallback() {
            @Override
            public void onResponse(String response) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    showTypingIndicator(false);
                    ChatMessage aiMessage = new ChatMessage(response, false);
                    messages.add(aiMessage);
                    chatAdapter.notifyItemInserted(messages.size() - 1);
                    chatMessagesList.scrollToPosition(messages.size() - 1);
                });
            }

            @Override
            public void onToolUse(String toolName, String toolInput, String toolResult) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    // Create a tool execution message
                    ChatMessage toolMessage = new ChatMessage(
                        "[Executing " + toolName + "...]\n" + toolResult, false);
                    toolMessage.setToolCalls(toolName);
                    messages.add(toolMessage);
                    chatAdapter.notifyItemInserted(messages.size() - 1);
                    chatMessagesList.scrollToPosition(messages.size() - 1);
                });
            }

            @Override
            public void onError(String error) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    showTypingIndicator(false);
                    ChatMessage errorMessage = new ChatMessage("Error: " + error, false);
                    errorMessage.setError(true);
                    messages.add(errorMessage);
                    chatAdapter.notifyItemInserted(messages.size() - 1);
                    chatMessagesList.scrollToPosition(messages.size() - 1);
                });
            }
        });
    }

    private void showTypingIndicator(boolean show) {
        typingIndicator.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            startTypingAnimation();
        }
    }

    private void startTypingAnimation() {
        View dot1 = typingIndicator.findViewById(R.id.typing_dot_1);
        View dot2 = typingIndicator.findViewById(R.id.typing_dot_2);
        View dot3 = typingIndicator.findViewById(R.id.typing_dot_3);

        if (dot1 != null && dot2 != null && dot3 != null) {
            dot1.animate().alpha(0.3f).setDuration(300).withEndAction(() ->
                dot1.animate().alpha(1f).setDuration(300).start()
            ).start();
            
            dot2.postDelayed(() ->
                dot2.animate().alpha(0.3f).setDuration(300).withEndAction(() ->
                    dot2.animate().alpha(1f).setDuration(300).start()
                ).start(), 100);
            
            dot3.postDelayed(() ->
                dot3.animate().alpha(0.3f).setDuration(300).withEndAction(() ->
                    dot3.animate().alpha(1f).setDuration(300).start()
                ).start(), 200);
        }
    }

    public void clearChat() {
        messages.clear();
        chatAdapter.notifyDataSetChanged();
        welcomeContainer.setVisibility(View.VISIBLE);
        chatMessagesList.setVisibility(View.GONE);
    }
}
