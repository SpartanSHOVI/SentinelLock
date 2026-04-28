package com.sentinel.lock.adapters;

import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.sentinel.lock.R;
import com.sentinel.lock.data.LogManager;
import java.io.File;
import java.util.List;

public class LogAdapter extends RecyclerView.Adapter<LogAdapter.ViewHolder> {
    private final List<LogManager.LogEntry> entries;

    public LogAdapter(List<LogManager.LogEntry> entries) {
        this.entries = entries;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_log, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        LogManager.LogEntry entry = entries.get(position);
        holder.timeText.setText(entry.timestamp);
        holder.nameText.setText(entry.name);
        
        if (entry.imagePath != null && !entry.imagePath.equals("no_image")) {
            File imgFile = new File(entry.imagePath);
            if (imgFile.exists()) {
                holder.imageView.setImageBitmap(BitmapFactory.decodeFile(imgFile.getAbsolutePath()));
            } else {
                holder.imageView.setImageResource(android.R.drawable.ic_menu_report_image);
                holder.imageView.setAlpha(0.3f);
            }
        } else {
            holder.imageView.setImageResource(android.R.drawable.ic_menu_gallery);
            holder.imageView.setAlpha(0.1f);
        }
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        TextView timeText;
        TextView nameText;

        ViewHolder(View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.logImage);
            timeText = itemView.findViewById(R.id.logTime);
            nameText = itemView.findViewById(R.id.logName);
        }
    }
}
