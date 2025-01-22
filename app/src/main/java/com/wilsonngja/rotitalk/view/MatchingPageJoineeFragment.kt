package com.wilsonngja.rotitalk.view

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.DeliverCallback
import com.wilsonngja.rotitalk.R
import com.wilsonngja.rotitalk.databinding.FragmentMatchingPageJoineeBinding
import com.wilsonngja.rotitalk.services.MatchMakingService
import com.wilsonngja.rotitalk.viewmodel.MatchingPageViewModel
import io.github.cdimascio.dotenv.Dotenv
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.IOException
import org.json.JSONArray
import org.redisson.Redisson
import org.redisson.api.RList
import org.redisson.api.RedissonClient
import org.redisson.config.Config
import java.io.File

class MatchingPageJoineeFragment : Fragment() {
    private var _binding: FragmentMatchingPageJoineeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MatchingPageViewModel by viewModels()

    private var name = ""
    private lateinit var playerList: RList<String>
    private lateinit var matchMakingService: MatchMakingService

//    private val matchMakingService = MatchMakingService(requireContext())


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMatchingPageJoineeBinding.inflate(inflater, container, false)

        val currentDir = System.getProperty("user.dir")
        val absolutePath = File("").absolutePath

        Log.d("Directory_Debug", "Current Directory: $currentDir")
        Log.d("Directory_Debug", "Absolute Path: $absolutePath")
//        Log.d("Directory_Debug", "Assets Check: ${assets.list("")?.joinToString()}")

        val args: MatchingPageHostFragmentArgs by navArgs()
        viewModel._roomName = args.roomName

        binding.textViewRoomId.text = viewModel._roomName

        return binding.root
    }

    @SuppressLint("MissingInflatedId")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val bottomSheetDialog = BottomSheetDialog(requireContext(), R.style.BottomSheetTheme)
        val view = layoutInflater.inflate(R.layout.overlay_bottom_display_name, null)

        val done = view.findViewById<Button>(R.id.btnDone)
        val editText = view.findViewById<EditText>(R.id.editTextName)

        val viewModel: MatchingPageViewModel by viewModels()

        // Retrieve the argument
//        Toast.makeText(context, "Host Name: $roomName", Toast.LENGTH_SHORT).show()



        binding.exitRoom.setOnClickListener {
            val view = layoutInflater.inflate(R.layout.overlay_middle_exit_room, null)
            val dialog = Dialog(requireContext(), R.style.BottomSheetTheme)
            dialog.setContentView(view)

            // Set the dialog width and height
            dialog.window?.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            )

            // Retrieve the layout parameters and adjust margins
            dialog.window?.let { window ->
                val params = window.attributes
                val marginInPixels = (20 * resources.displayMetrics.density).toInt() // Example: 20dp margin
                params.width = WindowManager.LayoutParams.MATCH_PARENT
                params.horizontalMargin = 0f // Ensure it's applied using padding/margin below
                window.decorView.setPadding(marginInPixels, 0, marginInPixels, 0) // Add left/right padding
                window.attributes = params
            }


            val cancel =  view.findViewById<Button>(R.id.btnCancel)
            val exit = view.findViewById<Button>(R.id.btnExit)

            cancel.setOnClickListener {
                dialog.dismiss()
            }

            exit.setOnClickListener {
                shutdownRoom()
                dialog.dismiss()

            }


            dialog.window?.setGravity(Gravity.CENTER)
            dialog.show()
        }

        editText.doAfterTextChanged { text ->
            viewModel.onRoomNameChange(text.toString())
        }


        viewModel.isButtonEnabled.observe(viewLifecycleOwner) { isEnabled ->
            done.isEnabled = isEnabled
            if (isEnabled) {
                done.setTextColor(ContextCompat.getColor(requireContext(), R.color.secondary_blue))
                done.setBackgroundResource(R.drawable.button_blue_background)
            } else {
                done.setTextColor(ContextCompat.getColor(requireContext(), R.color.invalid_button))

                done.setBackgroundResource(R.drawable.button_invalid)
            }
        }

        done.setOnClickListener {
            joinQueue(editText)
            bottomSheetDialog.dismiss()
            name=editText.text.toString()
            binding.imageView.setColorFilter(Color.argb(50, 100, 50, 50), PorterDuff.Mode.SRC_ATOP)
        }


        val back = view.findViewById<ImageView>(R.id.imgViewBackButton)
        back.setOnClickListener {
            shutdownRoom()
            bottomSheetDialog.dismiss()
        }



        bottomSheetDialog.setContentView(view)
        bottomSheetDialog.show()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        matchMakingService = MatchMakingService(context)
    }

    fun joinQueue(editText: EditText) {
        val dotenv = Dotenv.configure()
            .directory("/assets")
            .filename("env")
            .ignoreIfMissing()
            .load()

        val factoryHost = dotenv["HOST"]
        val factoryPort = dotenv["PORT"]?.toInt() ?: 5671  // provides default value if null
        val factoryUsername = dotenv["USERNAME"]
        val factoryPassword = dotenv["PASSWORD"]

        val factory: ConnectionFactory = ConnectionFactory().apply {
            host = factoryHost
            port = factoryPort
            username = factoryUsername
            password = factoryPassword
            useSslProtocol()
        }

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val queueName = editText.text?.toString() ?: throw NullPointerException("EditText is null")
                name = queueName

                if (queueName.isEmpty()) {
                    Log.e("RabbitMQ", "Queue name cannot be empty")
                    return@launch
                }

                factory.newConnection().use { connection ->
                    connection.createChannel().use { channel ->
                        channel.queueDeclare(queueName, true, false, false, null)
                        Log.d("RabbitMQ", "$queueName Declared")
                        channel.queueBind(queueName, viewModel._roomName, "")

                        Log.d("RabbitMQ", "Queue $queueName bound to exchange ${viewModel._roomName}")

                        var message = "update_ui"
                        channel.basicPublish(viewModel._roomName, "", null, message.toByteArray())

                        getBindings(factory.host, factory.username, factory.password, viewModel._roomName)
                        view?.let { matchMakingService.updatePlayersUI(it, viewModel) }
                        // Set up DeliverCallback to listen for incoming messages
                        val deliverCallback = DeliverCallback { _, delivery ->
                            message = String(delivery.body, Charsets.UTF_8)
                            Log.d("RabbitMQ", "Message received: $message")

                            if (message == "update_ui") {
                                if (view != null) {
                                    Log.d("RabbitMQ", "View != null")
                                    getBindings(factory.host, factory.username, factory.password, viewModel._roomName)
                                    view?.let { matchMakingService.updatePlayersUI(it, viewModel) }
                                    Log.d("RabbitMQ", viewModel._players.toString())
                                }
                            } else if (message == "close_room") {
                                findNavController().navigate(R.id.action_matchingPageJoineeFragment_to_mainPageFragment)
                            } else if (message == "start_game") {
                                val players = viewModel._players
                                val questions = mutableListOf(
                                    "What do you think my favorite kind of movie is?",
                                    "What do you think I spend most of my time doing?",
                                    "If we met at a party, what vibe would you pick up from me?",
                                    "Based on my appearance, do I seem like a morning or night person?",
                                    "Do you think I’m someone who likes routine or spontaneity?",
                                    "Do I seem like someone who prefers the city or nature?",
                                    "What kind of friend do you think I am?",
                                    "What’s a dream you’ve always had but haven’t pursued yet?",
                                    "What’s a habit or mindset you’ve been trying to change?",
                                    "What’s a song that perfectly describes your current mood?",
                                    "What’s something you wish more people knew about you?",
                                    "What’s a trait you admire in others that you want to cultivate in yourself?",
                                    "How do you think I’ve changed since we met?",
                                    "What’s something I could do to be a better friend/partner/person?",
                                    "What’s a strength of mine you think I underestimate?",
                                    "What’s a way I could support you better in the future?",
                                    "What do you admire most about me?",
                                    "What’s one word to describe how you feel after this conversation?",
                                    "What’s a question you wish I’d asked you?",
                                    "Share a song that resonates with you and explain why."
                                )

                                val background = viewModel._players_background_color
                                val foreground = viewModel._players_foreground_color

                                val action = MatchingPageJoineeFragmentDirections.actionMatchingPageJoineeFragmentToGamingPageFragment(players.toTypedArray(),
                                    questions.toTypedArray(), background.toIntArray(), foreground.toIntArray(),
                                    name, viewModel._roomName )
                                findNavController().navigate(action)
                            }
                        }

                        // Start consuming messages
                        channel.basicConsume(queueName, true, deliverCallback) { _ ->
                            Log.d("RabbitMQ", "Consumer cancelled for queue: $queueName")
                        }

                        Log.d("RabbitMQ", "Consumer started for queue: $queueName")

                        while (true) {
                            delay(1000)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("RabbitMQ", "Connection or channel creation failed", e)
            }
        }

    }

    private fun shutdownRoom() {
        val dotenv = Dotenv.configure()
            .directory("/assets")
            .filename("env")
            .ignoreIfMissing()
            .load()

        val factoryHost = dotenv["HOST"]
        val factoryPort = dotenv["PORT"]?.toInt() ?: 5671  // provides default value if null
        val factoryUsername = dotenv["USERNAME"]
        val factoryPassword = dotenv["PASSWORD"]

        val factory = ConnectionFactory().apply {
            host = factoryHost
            port = factoryPort
            username = factoryUsername
            password = factoryPassword
            useSslProtocol()
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                factory.newConnection().use { connection ->
                    connection.createChannel().use { channel ->
                        var message = "update_ui"

                        channel.queueDelete(name)
                        channel.basicPublish(viewModel._roomName, "", null, message.toByteArray())
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e("Redis", "Error removing player: ${e.message}")
                }
            }
        }

        findNavController().navigate(R.id.action_matchingPageJoineeFragment_to_mainPageFragment)
    }


    fun getBindings(baseUrl: String, username: String, password: String, exchangeName: String) {
        viewModel._players.clear()
        val client = OkHttpClient()
        val url = "https://$baseUrl/api/exchanges/%2F/$exchangeName/bindings/source"

        val request = Request.Builder()
            .url(url)
            .header("Authorization", Credentials.basic(username, password))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code $response")

            val responseBody = response.body?.string() // Get the JSON string content
            if (responseBody != null) {
                try {
                    val jsonArray = JSONArray(responseBody) // Parse it as a JSON array
                    val destinations = mutableListOf<String>()
                    for (i in 0 until jsonArray.length()) {
                        val jsonObject = jsonArray.getJSONObject(i)
                        val destination = jsonObject.getString("destination") // Extract "destination"
                        viewModel._players.add(destination)
                    }

                    println("Destinations: $destinations")
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else {
                println("Response body is null")
            }
        }
    }
}