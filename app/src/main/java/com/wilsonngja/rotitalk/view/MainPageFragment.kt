package com.wilsonngja.rotitalk.view

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.rabbitmq.client.ConnectionFactory
import com.wilsonngja.rotitalk.R
import com.wilsonngja.rotitalk.databinding.FragmentMainPageBinding
import com.wilsonngja.rotitalk.services.MatchMakingService
import com.wilsonngja.rotitalk.viewmodel.MainPageViewModel
import io.github.cdimascio.dotenv.Dotenv
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException
import org.redisson.Redisson
import org.redisson.api.RList
import org.redisson.api.RedissonClient
import org.redisson.config.Config
import java.io.IOException


class MainPageFragment : Fragment() {
    private var _binding: FragmentMainPageBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainPageViewModel by viewModels()
    private lateinit var matchMakingService : MatchMakingService

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMainPageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        defineHostRoomAction(binding)
        defineJoinRoomAction(binding)

    }

    private fun defineHostRoomAction(binding: FragmentMainPageBinding) {
        binding.btnHostARoom.setOnClickListener {
            val bottomSheetDialog = BottomSheetDialog(requireContext(), R.style.BottomSheetTheme)
            val view = layoutInflater.inflate(R.layout.overlay_bottom_create_room, null)

            val backButton = view.findViewById<ImageView>(R.id.imgViewBackButton)
            backButton.setOnClickListener {
                bottomSheetDialog.dismiss()
            }

            val editText = view.findViewById<EditText>(R.id.editText)
            val createRoom = view.findViewById<Button>(R.id.btnCreateRoom)
            val roomExistsTextView = view.findViewById<TextView>(R.id.textViewRoomExists)

            // Observe the state of the button using ViewModel
            viewModel.isButtonEnabled.observe(viewLifecycleOwner) { isEnabled ->
                createRoom.isEnabled = isEnabled
                if (isEnabled) {
                    createRoom.setTextColor(ContextCompat.getColor(requireContext(), R.color.secondary_blue))
                    createRoom.setBackgroundResource(R.drawable.button_blue_background)
                } else {
                    createRoom.setTextColor(ContextCompat.getColor(requireContext(), R.color.invalid_button))
                    createRoom.setBackgroundResource(R.drawable.button_invalid)
                }
            }

            // Update ViewModel when text changes in EditText
            editText.doAfterTextChanged { text ->
                viewModel.onRoomNameChange(text.toString())
            }

            createRoom.setOnClickListener {
                val exchangeName = editText.text.toString()
                if (exchangeName.isEmpty()) {
                    Log.e("RabbitMQ", "Exchange name is empty")
                    return@setOnClickListener
                }

                CoroutineScope(Dispatchers.Main).launch {
                    try {
                        val roomExists = matchMakingService.roomExists(exchangeName)
                        if (roomExists) {
                            roomExistsTextView.visibility = View.VISIBLE
                        } else {
                            matchMakingService.createRoom(exchangeName)
                            bottomSheetDialog.dismiss()
                            val action = MainPageFragmentDirections.actionMainPageFragmentToMatchingPageHostFragment(exchangeName)

                            findNavController().navigate(action)
                        }
                    } catch (e: Exception) {
                        Log.e("RabbitMQ", "Error handling room actions", e)
                    }
                }
            }

            bottomSheetDialog.setContentView(view)
            bottomSheetDialog.show()
        }
    }

    private fun defineJoinRoomAction(binding: FragmentMainPageBinding) {
        binding.btnJoinARoom.setOnClickListener {
            val bottomSheetDialog = BottomSheetDialog(requireContext(), R.style.BottomSheetTheme)
            val view = layoutInflater.inflate(R.layout.overlay_bottom_join_room, null)

            val backButton = view.findViewById<ImageView>(R.id.imgViewBackButton)
            backButton.setOnClickListener {
                bottomSheetDialog.dismiss()
            }

            val editText = view.findViewById<EditText>(R.id.editText)
            val joinRoom = view.findViewById<Button>(R.id.btnJoinRoom)
            val roomDoesNotExistTextView = view.findViewById<TextView>(R.id.textViewRoomDoesNotExists)

            // Observe the state of the button using ViewModel
            viewModel.isButtonEnabled.observe(viewLifecycleOwner) { isEnabled ->
                joinRoom.isEnabled = isEnabled
                if (isEnabled) {
                    joinRoom.setTextColor(ContextCompat.getColor(requireContext(), R.color.secondary_green))
                    joinRoom.setBackgroundResource(R.drawable.button_green_background)
                } else {
                    joinRoom.setTextColor(ContextCompat.getColor(requireContext(), R.color.invalid_button))
                    joinRoom.setBackgroundResource(R.drawable.button_invalid)
                }
            }

            // Update ViewModel when text changes in EditText
            editText.doAfterTextChanged { text ->
                viewModel.onRoomNameChange(text.toString())
            }

            joinRoom.setOnClickListener {
                val exchangeName = editText.text.toString()
                if (exchangeName.isEmpty()) {
                    Log.e("RabbitMQ", "Exchange name is empty")
                    return@setOnClickListener
                }

                CoroutineScope(Dispatchers.Main).launch {
                    try {
                        val roomExists = withContext(Dispatchers.IO) {
                            matchMakingService.roomExists(exchangeName)
                        }

                        if (roomExists) {
                            lifecycleScope.launch {
                                try {
                                    val dotenv = Dotenv.configure()
                                        .directory("/assets")
                                        .filename("env")
                                        .ignoreIfMissing()
                                        .load()

                                    val factoryHost = dotenv["HOST"]
                                    val factoryUsername = dotenv["USERNAME"]
                                    val factoryPassword = dotenv["PASSWORD"]

                                    val host = factoryHost
                                    val username = factoryUsername
                                    val password = factoryPassword

                                    // Call getBindings in a background thread
                                    val participantCount = withContext(Dispatchers.IO) {
                                        getBindings(host, username, password, exchangeName)
                                    }

                                    // Update UI on the main thread
                                    withContext(Dispatchers.Main) {
                                        if (participantCount > 4) {
                                            roomDoesNotExistTextView.text = "Room has reached its max capacity"
                                            roomDoesNotExistTextView.visibility = View.VISIBLE
                                        } else {
                                            bottomSheetDialog.dismiss()
                                            val action = MainPageFragmentDirections.actionMainPageFragmentToMatchingPageJoineeFragment(exchangeName)
                                            findNavController().navigate(action)
                                        }
                                    }
                                } catch (e: IOException) {
                                    Log.e("RabbitMQ", "Network error: ${e.message}")
                                } catch (e: JSONException) {
                                    Log.e("RabbitMQ", "JSON error: ${e.message}")
                                } catch (e: Exception) {
                                    Log.e("RabbitMQ", "Unexpected error: ${e.message}")
                                }
                            }

                        } else {
                            roomDoesNotExistTextView.visibility = View.VISIBLE
                        }
                    } catch (e: Exception) {
                        Log.e("RabbitMQ", "Error joining room", e)
                    }
                }

            }

            bottomSheetDialog.setContentView(view)
            bottomSheetDialog.show()
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        matchMakingService = MatchMakingService(context)
    }

    fun getBindings(baseUrl: String, username: String, password: String, exchangeName: String): Int {
        val client = OkHttpClient()
        val url = "https://$baseUrl/api/exchanges/%2F/$exchangeName/bindings/source"

        val request = Request.Builder()
            .url(url)
            .header("Authorization", Credentials.basic(username, password))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code $response")

            val responseBody = response.body?.string() // Get the JSON string content
            Log.d("RabbitMQ", responseBody.toString())
            if (responseBody != null) {
                try {
                    val jsonArray = JSONArray(responseBody) // Parse it as a JSON array
                    Log.d("RabbitMQ", jsonArray.length().toString())
                    return jsonArray.length()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        return 0
    }

}