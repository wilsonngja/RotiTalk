package com.wilsonngja.rotitalk.adapter

import com.wilsonngja.rotitalk.R
import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.firebase.ui.firestore.FirestoreRecyclerAdapter
import com.firebase.ui.firestore.FirestoreRecyclerOptions
import com.wilsonngja.rotitalk.model.ChatModel
import com.wilsonngja.rotitalk.viewmodel.GamingPageViewModel


class ChatRecyclerAdapter(
    options: FirestoreRecyclerOptions<ChatModel>,
    private val context: Context,
    private val viewModel: GamingPageViewModel
) : FirestoreRecyclerAdapter<ChatModel, ChatRecyclerAdapter.ChatModelViewHolder>(options) {

    class ChatModelViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var incomingLinearLayout : LinearLayout = itemView.findViewById(R.id.incomingMessage)
        var outgoingLinearLayout : LinearLayout = itemView.findViewById(R.id.outgoingMessage)
        var incomingMessageName: TextView = itemView.findViewById(R.id.incomingMessageName)
        var incomingMessageText: TextView = itemView.findViewById(R.id.incomingMessageText)
        var outgoingMessageName: TextView = itemView.findViewById(R.id.outgoingMessageName)
        var outgoingMessageText: TextView = itemView.findViewById(R.id.outgoingMessageText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatModelViewHolder {
        val view: View =
            LayoutInflater.from(context).inflate(R.layout.chat_message_recycler_row, parent, false)
        return ChatModelViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatModelViewHolder, position: Int, model: ChatModel) {
        Log.d("FirestoreDebug", "OnBind Called")

        if (viewModel._player == model.name) {
            holder.outgoingLinearLayout.visibility = View.VISIBLE
            holder.incomingLinearLayout.visibility = View.GONE
            holder.outgoingMessageName.visibility = View.GONE
            holder.outgoingMessageText.text = model.message
        } else {
            holder.outgoingLinearLayout.visibility = View.GONE
            holder.incomingLinearLayout.visibility = View.VISIBLE
            holder.incomingMessageName.text = model.name
            holder.incomingMessageText.text = model.message
        }
    }


}