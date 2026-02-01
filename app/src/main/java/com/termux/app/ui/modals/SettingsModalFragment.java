package com.termux.app.ui.modals;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.termux.R;
import com.termux.app.MainActivity;
import com.termux.app.ai.AIAgent;

public class SettingsModalFragment extends Fragment {

    private EditText inputAnthropicKey;
    private EditText inputHuggingfaceKey;
    private EditText inputTavilyKey;
    private EditText inputOllamaUrl;
    private Spinner spinnerModel;
    private Button btnSave;
    
    private AIAgent aiAgent;

    public static SettingsModalFragment newInstance() {
        return new SettingsModalFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_modal_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        aiAgent = new AIAgent(requireContext());
        
        initViews(view);
        setupListeners(view);
        loadCurrentSettings();
    }

    private void initViews(View view) {
        inputAnthropicKey = view.findViewById(R.id.input_anthropic_key);
        inputHuggingfaceKey = view.findViewById(R.id.input_huggingface_key);
        inputTavilyKey = view.findViewById(R.id.input_tavily_key);
        inputOllamaUrl = view.findViewById(R.id.input_ollama_url);
        spinnerModel = view.findViewById(R.id.spinner_model);
        btnSave = view.findViewById(R.id.btn_save);

        // Setup model spinner
        String[] models = {
            "claude-3-5-sonnet-20241022",
            "claude-3-opus-20240229",
            "claude-3-haiku-20240307",
            "llama3.2 (Ollama)",
            "codellama (Ollama)"
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            models
        );
        spinnerModel.setAdapter(adapter);
    }

    private void setupListeners(View view) {
        view.findViewById(R.id.modal_overlay).setOnClickListener(v -> closeModal());
        view.findViewById(R.id.btn_close).setOnClickListener(v -> closeModal());
        
        btnSave.setOnClickListener(v -> saveSettings());
        
        // Check for updates
        view.findViewById(R.id.btn_check_updates).setOnClickListener(v -> checkForUpdates());
    }

    private void loadCurrentSettings() {
        inputAnthropicKey.setText(aiAgent.getAnthropicApiKey());
        inputHuggingfaceKey.setText(aiAgent.getHuggingfaceApiKey());
        inputTavilyKey.setText(aiAgent.getTavilyApiKey());
        inputOllamaUrl.setText(aiAgent.getOllamaBaseUrl());
        
        // Select current model in spinner
        String currentModel = aiAgent.getPrimaryModel();
        for (int i = 0; i < spinnerModel.getCount(); i++) {
            if (spinnerModel.getItemAtPosition(i).toString().startsWith(currentModel)) {
                spinnerModel.setSelection(i);
                break;
            }
        }
    }

    private void saveSettings() {
        String anthropicKey = inputAnthropicKey.getText().toString().trim();
        String huggingfaceKey = inputHuggingfaceKey.getText().toString().trim();
        String tavilyKey = inputTavilyKey.getText().toString().trim();
        String ollamaUrl = inputOllamaUrl.getText().toString().trim();
        String selectedModel = spinnerModel.getSelectedItem().toString();
        
        // Extract model name (remove "(Ollama)" suffix if present)
        if (selectedModel.contains("(Ollama)")) {
            selectedModel = selectedModel.split(" ")[0];
        }

        aiAgent.saveSettings(anthropicKey, huggingfaceKey, tavilyKey, ollamaUrl, selectedModel);
        
        Toast.makeText(requireContext(), R.string.settings_saved, Toast.LENGTH_SHORT).show();
        closeModal();
    }

    private void checkForUpdates() {
        // Check for OTA updates from GitHub
        Toast.makeText(requireContext(), "Checking for updates...", Toast.LENGTH_SHORT).show();
    }

    private void closeModal() {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).hideModal();
        }
    }
}
