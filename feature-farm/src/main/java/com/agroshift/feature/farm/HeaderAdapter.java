package com.agroshift.feature.farm;

import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/** Lets a page header scroll together with its list. */
public final class HeaderAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private final View header;

    public HeaderAdapter(View header) {
        this.header = header;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (header.getParent() instanceof ViewGroup previous) {
            previous.removeView(header);
        }
        header.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return new RecyclerView.ViewHolder(header) {
        };
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
    }

    @Override
    public int getItemCount() {
        return 1;
    }
}
