package com.wilsonngja.rotitalk.view

import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import com.wilsonngja.rotitalk.databinding.ActivityMainBinding
import com.wilsonngja.rotitalk.repository.UserRepository
import com.wilsonngja.rotitalk.viewmodel.MainPageViewModel


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val repository  = UserRepository()
    private val viewModel: MainPageViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)



    }
}
