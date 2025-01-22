package com.wilsonngja.rotitalk.viewmodel

import androidx.lifecycle.ViewModel

class GamingPageViewModel() : ViewModel() {
    var _players = mutableListOf<String>()
    var _questions = mutableListOf<String>()
    var _background = mutableListOf<Int>()
    var _foreground = mutableListOf<Int>()

    var _player = ""
    var _roomName = ""
}