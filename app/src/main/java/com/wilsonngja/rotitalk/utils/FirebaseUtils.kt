package com.wilsonngja.rotitalk.utils


import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore


object FirebaseUtils {


    fun currentUserDetails(): DocumentReference {
        return FirebaseFirestore.getInstance()
            .collection("users")
            .document(/* Provide the document ID here */)
    }

    fun getChatroomReference(chatroomId: String?): DocumentReference {
        return FirebaseFirestore.getInstance().collection("chatrooms").document(chatroomId!!)
    }

    fun getChatroomMessageReference(chatroomId: String?): CollectionReference {
        return getChatroomReference(chatroomId).collection("chat")
    }

}