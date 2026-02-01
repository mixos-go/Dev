package com.termux.app.ui.adapters;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.termux.R;
import com.termux.app.ai.ChatMessage;

import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.MessageViewHolder> {

    private final List<ChatMessage> messages;

    public ChatAdapter(List<ChatMessage> messages) {
        this.messages = messages;
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_chat_message, parent, false);
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        ChatMessage message = messages.get(position);
        holder.bind(message);
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class MessageViewHolder extends RecyclerView.ViewHolder {
        private final LinearLayout messageContainer;
        private final TextView messageText;
        private final View bubbleBackground;

        MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            messageContainer = itemView.findViewById(R.id.message_container);
            messageText = itemView.findViewById(R.id.message_text);
            bubbleBackground = itemView.findViewById(R.id.bubble_background);
        }

        void bind(ChatMessage message) {
            messageText.setText(message.getContent());

            // Set alignment based on user/AI
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) messageContainer.getLayoutParams();
            
            if (message.isUser()) {
                params.gravity = Gravity.END;
                bubbleBackground.setBackgroundResource(R.drawable.bg_message_user);
                messageText.setTextColor(itemView.getContext().getColor(R.color.white));
            } else {
                params.gravity = Gravity.START;
                if (message.isError()) {
                    bubbleBackground.setBackgroundResource(R.drawable.bg_message_error);
                    messageText.setTextColor(itemView.getContext().getColor(R.color.state_error));
                } else {
                    bubbleBackground.setBackgroundResource(R.drawable.bg_message_ai);
                    messageText.setTextColor(itemView.getContext().getColor(R.color.text_primary));
                }
            }
            
            messageContainer.setLayoutParams(params);
        }
    }
}
