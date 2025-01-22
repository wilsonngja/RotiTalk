package com.wilsonngja.rotitalk.utils

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.wilsonngja.rotitalk.repository.UserRepository
import com.wilsonngja.rotitalk.viewmodel.MainPageViewModel

//class UserViewModelFactory(private val userRepository: UserRepository) : ViewModelProvider.Factory {
//    override fun <T : ViewModel> create(modelClass: Class<T>): T {
//        if (modelClass.isAssignableFrom(MainPageViewModel::class.java)) {
//            Log.d("debug", userRepository.getUsers().toString())
//            return MainPageViewModel(userRepository) as T
//        }
//        throw IllegalArgumentException("Unknown ViewModel class")
//    }
//}