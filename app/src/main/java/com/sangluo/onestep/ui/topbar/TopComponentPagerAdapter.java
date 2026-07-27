package com.sangluo.onestep.ui.topbar;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/** Adapter owning the stable ordering of top status component views. */
public final class TopComponentPagerAdapter
        extends RecyclerView.Adapter<TopComponentPagerAdapter.PageHolder> {
    private final List<TopComponentPage> pages = new ArrayList<>();

    public TopComponentPagerAdapter() {
        setHasStableIds(true);
    }

    @NonNull
    @Override
    public PageHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        FrameLayout container = new FrameLayout(parent.getContext());
        container.setLayoutParams(new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return new PageHolder(container);
    }

    @Override
    public void onBindViewHolder(@NonNull PageHolder holder, int position) {
        View pageView = pages.get(position).view;
        ViewParent parent = pageView.getParent();
        if (parent instanceof ViewGroup) {
            ((ViewGroup) parent).removeView(pageView);
        }
        holder.container.removeAllViews();
        holder.container.addView(pageView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    @Override
    public void onViewRecycled(@NonNull PageHolder holder) {
        holder.container.removeAllViews();
    }

    @Override
    public int getItemCount() {
        return pages.size();
    }

    @Override
    public long getItemId(int position) {
        return pages.get(position).id;
    }

    public long getPageId(int position) {
        return position >= 0 && position < pages.size() ? pages.get(position).id : -1L;
    }

    public int indexOfPageId(long pageId) {
        for (int i = 0; i < pages.size(); i++) {
            if (pages.get(i).id == pageId) {
                return i;
            }
        }
        return -1;
    }

    public boolean setPages(List<TopComponentPage> nextPages) {
        if (hasSamePages(nextPages)) {
            return false;
        }
        pages.clear();
        pages.addAll(nextPages);
        notifyDataSetChanged();
        return true;
    }

    private boolean hasSamePages(List<TopComponentPage> nextPages) {
        if (pages.size() != nextPages.size()) {
            return false;
        }
        for (int i = 0; i < pages.size(); i++) {
            TopComponentPage current = pages.get(i);
            TopComponentPage next = nextPages.get(i);
            if (current.id != next.id || current.view != next.view) {
                return false;
            }
        }
        return true;
    }

    static final class PageHolder extends RecyclerView.ViewHolder {
        final FrameLayout container;

        PageHolder(FrameLayout container) {
            super(container);
            this.container = container;
        }
    }
}
