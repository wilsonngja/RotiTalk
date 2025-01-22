package com.wilsonngja.rotitalk.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class MatchingPageViewModel() : ViewModel() {

    val _players = mutableListOf<String>()
    var _roomName = ""
    val _displayName = MutableLiveData<String>()
    var _players_background_color = mutableListOf<Int>()
    var _players_foreground_color = mutableListOf<Int>()


    val isButtonEnabled = MediatorLiveData<Boolean>().apply {
        addSource(_displayName) { input ->
            value = !input.isNullOrBlank()
        }
    }

    fun onRoomNameChange(newText: String) {
        _displayName.value = newText
    }
}