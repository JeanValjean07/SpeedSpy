package com.suming.speedspy

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import coil.load
import data.model.VideoItem




class VideoPagingAdapter : PagingDataAdapter<VideoItem, VideoPagingAdapter.ViewHolder>(diffCallback) {

    //DiffUtil 对比器
    companion object {
        val diffCallback = object : DiffUtil.ItemCallback<VideoItem>() {
            override fun areItemsTheSame(oldItem: VideoItem, newItem: VideoItem): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: VideoItem, newItem: VideoItem): Boolean {
                return oldItem == newItem
            }
        }
    }


    //做成内部类
    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tvName)
        val tvDuration: TextView = itemView.findViewById(R.id.tvDuration)
        val tvThumb: ImageView = itemView.findViewById(R.id.ivThumb)
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.activity_main_items, parent, false)
        return ViewHolder(view)   //把xml转为view对象，并避免每次都实例化一次
    }


    @SuppressLint("SetTextI18n", "QueryPermissionsNeeded")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position) ?: return
        holder.tvName.text = item.name
        holder.tvDuration.text = "${item.durationMs / 1000} 秒"
        holder.tvThumb.load(item.thumbnailUri)
        //点击事件
        holder.itemView.setOnClickListener {

            val intent = Intent(holder.itemView.context, WorkingActivity::class.java).apply {
                putExtra("video", item)   // item 就是 VideoItem
            }
            holder.itemView.context.startActivity(intent)

        }
    }





}//class END