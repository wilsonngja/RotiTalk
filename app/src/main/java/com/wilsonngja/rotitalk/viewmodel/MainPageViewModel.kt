package com.wilsonngja.rotitalk.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class MainPageViewModel : ViewModel() {

    private val _roomName = MutableLiveData<String>()
    val roomName: LiveData<String> = _roomName

    val isButtonEnabled = MediatorLiveData<Boolean>().apply {
        addSource(_roomName) { input ->
            value = !input.isNullOrBlank()
        }
    }

    fun onRoomNameChange(newText: String) {
        _roomName.value = newText
    }
}
