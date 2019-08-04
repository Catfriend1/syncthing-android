package com.nutomic.syncthingandroid.views;

import android.content.Context;
import androidx.recyclerview.widget.RecyclerView;
// import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.nutomic.syncthingandroid.R;

import java.util.ArrayList;

public class TipListAdapter extends RecyclerView.Adapter<TipListAdapter.ViewHolder> {

    // private static final String TAG = "TipListAdapter";

    private ArrayList<TipEntry> mTipData = new ArrayList<TipEntry>();
    private ItemClickListener mOnClickListener;
    private LayoutInflater mLayoutInflater;

    private class TipEntry {
        public String title;
        public String text;

        public TipEntry(String title, String text) {
            this.title = title;
            this.text = text;
        }
    }

    public interface ItemClickListener {
        void onItemClick(View view, String title, String text);
    }

    public TipListAdapter(Context context) {
        mLayoutInflater = LayoutInflater.from(context);
    }

    public void add(String title, String text) {
        mTipData.add(new TipEntry(title, text));
    }

    public void setOnClickListener(ItemClickListener onClickListener) {
        mOnClickListener = onClickListener;
    }

    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        public TextView tipTitle;
        public TextView tipText;
        public View layout;

        public ViewHolder(View view) {
            super(view);
            tipTitle = view.findViewById(R.id.tip_title);
            tipText = view.findViewById(R.id.tip_text);
            view.setOnClickListener(this);
        }

        @Override
        public void onClick(View view) {
            int position = getAdapterPosition();
            String title = mTipData.get(position).title;
            String text = mTipData.get(position).text;
            if (mOnClickListener != null) {
                mOnClickListener.onItemClick(view, title, text);
            }
        }
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = mLayoutInflater.inflate(R.layout.item_tip, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, final int position) {
        viewHolder.tipTitle.setText(mTipData.get(position).title);
        viewHolder.tipText.setText(mTipData.get(position).text);
    }

    @Override
    public int getItemCount() {
        return mTipData.size();
    }

}
