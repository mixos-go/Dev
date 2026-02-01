package com.termux.app.ui.modals;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.termux.R;
import com.termux.app.MainActivity;

public class DevModalFragment extends Fragment {

    private View menuContainer;
    private View listContainer;
    private RecyclerView itemsList;
    private TextView listTitle;
    private String currentCategory;

    public static DevModalFragment newInstance() {
        return new DevModalFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_modal_dev, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViews(view);
        setupListeners(view);
    }

    private void initViews(View view) {
        menuContainer = view.findViewById(R.id.menu_container);
        listContainer = view.findViewById(R.id.list_container);
        itemsList = view.findViewById(R.id.items_list);
        listTitle = view.findViewById(R.id.list_title);

        itemsList.setLayoutManager(new LinearLayoutManager(getContext()));
    }

    private void setupListeners(View view) {
        // Overlay click to close
        view.findViewById(R.id.modal_overlay).setOnClickListener(v -> closeModal());
        
        // Menu items
        view.findViewById(R.id.menu_packages).setOnClickListener(v -> showList("Packages"));
        view.findViewById(R.id.menu_runtime).setOnClickListener(v -> showList("Runtime"));
        view.findViewById(R.id.menu_libraries).setOnClickListener(v -> showList("Libraries"));
        view.findViewById(R.id.menu_git).setOnClickListener(v -> showList("Git"));
        view.findViewById(R.id.menu_domain).setOnClickListener(v -> showList("Domain"));
        
        // Back button in list view
        view.findViewById(R.id.btn_back).setOnClickListener(v -> showMenu());
        
        // Close button
        view.findViewById(R.id.btn_close).setOnClickListener(v -> closeModal());
    }

    private void showList(String category) {
        currentCategory = category;
        listTitle.setText(category);
        
        menuContainer.setVisibility(View.GONE);
        listContainer.setVisibility(View.VISIBLE);
        
        // Load items based on category
        loadItems(category);
    }

    private void showMenu() {
        listContainer.setVisibility(View.GONE);
        menuContainer.setVisibility(View.VISIBLE);
    }

    private void loadItems(String category) {
        // This would be populated with actual installed packages from Termux
        // For now, showing placeholder data structure
    }

    private void closeModal() {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).hideModal();
        }
    }
}
