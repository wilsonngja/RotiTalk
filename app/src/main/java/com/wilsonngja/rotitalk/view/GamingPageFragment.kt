package com.wilsonngja.rotitalk.view

import android.app.Dialog
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.AdapterDataObserver
import com.firebase.ui.firestore.FirestoreRecyclerOptions
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.DeliverCallback
import com.wilsonngja.rotitalk.R
import com.wilsonngja.rotitalk.adapter.ChatRecyclerAdapter
import com.wilsonngja.rotitalk.databinding.FragmentGamingPageBinding
import com.wilsonngja.rotitalk.model.ChatModel
import com.wilsonngja.rotitalk.viewmodel.GamingPageViewModel
import io.github.cdimascio.dotenv.Dotenv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.IOException
import org.json.JSONArray
import java.io.File


class GamingPageFragment : Fragment() {

    private var _binding : FragmentGamingPageBinding? = null
    private val binding get() = _binding!!
    private val viewModel: GamingPageViewModel by viewModels()
    private var questionIndex = 0
    private var playerIndex = 0
    private lateinit var recyclerView : RecyclerView


    val dotenv = Dotenv.configure()
        .directory("/assets")
        .filename("env")
        .ignoreIfMissing()
        .load()

    val factoryHost = dotenv["HOST"]
    val factoryPort = dotenv["PORT"]?.toInt() ?: 5671  // provides default value if null
    val factoryUsername = dotenv["USERNAME"]
    val factoryPassword = dotenv["PASSWORD"]

    private val factory: ConnectionFactory = ConnectionFactory().apply {
        host = factoryHost
        port = factoryPort
        username = factoryUsername
        password = factoryPassword
        useSslProtocol()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGamingPageBinding.inflate(inflater, container, false)

        val currentDir = System.getProperty("user.dir")
        val absolutePath = File("").absolutePath

        Log.d("Directory_Debug", "Current Directory: $currentDir")
        Log.d("Directory_Debug", "Absolute Path: $absolutePath")

        return binding.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Retrieve players and questions
        viewModel._players = arguments?.getStringArray("players")?.toMutableList() ?: mutableListOf()
//        viewModel._questions = arguments?.getStringArray("questions")?.toMutableList() ?: mutableListOf()
        viewModel._background = arguments?.getIntArray("background")?.toMutableList() ?: mutableListOf()
        viewModel._foreground = arguments?.getIntArray("foreground")?.toMutableList() ?: mutableListOf()
        viewModel._player = arguments?.getString("player").toString()
        viewModel._roomName = arguments?.getString("room").toString()


        val db = Firebase.firestore
        db.collection(viewModel._roomName)
            .document("questions")
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    // Extract the questions as a MutableList<String>
                    val questionsList = document.get("questions") as? List<String>
                    if (questionsList != null) {
                        viewModel._questions = questionsList.toMutableList()
                        Log.d("Firestore", viewModel._questions[0])
                        binding.textViewQuestions.text = viewModel._questions[0]
                    } else {
                        Log.e("FirestoreError", "Questions field is null or not a list")
                    }
                } else {
                    Log.e("FirestoreError", "Document does not exist")
                }
            }
            .addOnFailureListener { exception ->
                Log.e("FirestoreError", "Error fetching questions", exception)
            }





        binding.textViewName.text = viewModel._players[0]
        binding.textViewName.setTextColor(viewModel._foreground[0])

        updateButtonVisibility()

        val drawable = ContextCompat.getDrawable(requireContext(), R.drawable.player1_background)!!
        // Ensure the drawable is a GradientDrawable
        (drawable as? GradientDrawable)?.setColor(viewModel._background[0])
        binding.textViewName.background = drawable

        recyclerView = view.findViewById(R.id.recyclerViewMessage)
//        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        setupChatRecyclerView()

        binding.imageViewPaperPlane.setOnClickListener {
            val db = Firebase.firestore

            val playerName = viewModel._player ?: "Unknown"
            val messageText = binding.textFieldEnterText.text.toString().trim()

            if (messageText.isEmpty()) {
                Log.d("Firebase", "Message is empty")
                return@setOnClickListener
            }

            val chat = hashMapOf(
                "name" to playerName,
                "timestamp" to System.currentTimeMillis(),
                "message" to messageText
            )

            db.collection(viewModel._roomName)
                .document("chat")
                .set(chat)
                .addOnSuccessListener { documentReference ->
                    binding.textFieldEnterText.text.clear() // Clear the input field after successful send
                }
                .addOnFailureListener { exception ->
                    Log.e("Firebase", "Error adding document", exception)
                    Toast.makeText(context, "Failed to send message. Try again.", Toast.LENGTH_SHORT).show()
                }
        }


        binding.textViewEndGame.setOnClickListener {
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

                lifecycleScope.launch(Dispatchers.IO) {
                    factory.newConnection().use { connection ->
                        connection.createChannel().use { channel ->
                            var message = "update_players"
                            channel.basicPublish(viewModel._roomName, "", null, message.toByteArray())
                        }
                    }
                }
            }



            dialog.window?.setGravity(Gravity.CENTER)
            dialog.show()


        }

        binding.imageViewCross.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                factory.newConnection().use { connection ->
                    connection.createChannel().use { channel ->
                        var message = "skip_question"
                        channel.basicPublish(viewModel._roomName, "", null, message.toByteArray())
                    }
                }
            }

        }

        binding.imageViewTick.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                factory.newConnection().use { connection ->
                    connection.createChannel().use { channel ->
                        var message = "question_complete"
                        channel.basicPublish(viewModel._roomName, "", null, message.toByteArray())
                    }
                }
            }
        }



        monitorMessage()


    }

    private fun shutdownRoom() {
        // Issues: Queue not deleted.

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
                        Log.d("GamePage", "viewModel: ${viewModel._player}")
                        channel.queueDelete(viewModel._player)
                        channel.basicPublish(viewModel._roomName, "", null, message.toByteArray())

                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e("RabbitMQ", "Error removing player: ${e.message}")
                }
            }
        }

        findNavController().navigate(R.id.action_gamingPageFragment_to_mainPageFragment)
    }

    private fun skipQuestion() {
        binding.textViewQuestions.text = viewModel._questions[++questionIndex % viewModel._questions.size]
        updateButtonVisibility()
    }

    private fun turnComplete() {
        val drawable = ContextCompat.getDrawable(requireContext(), R.drawable.player1_background)!!
        binding.textViewQuestions.text = viewModel._questions[++questionIndex % viewModel._questions.size]

        binding.textViewName.text = viewModel._players[++playerIndex % viewModel._players.size]
        binding.textViewName.setTextColor(viewModel._foreground[++playerIndex % viewModel._players.size])

        (drawable as? GradientDrawable)?.setColor(viewModel._background[++playerIndex % viewModel._players.size])

        binding.textViewName.background = drawable

        updateButtonVisibility()
    }

    private fun monitorMessage() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                factory.newConnection().use { connection ->
                    connection.createChannel().use { channel ->

                        // Set up DeliverCallback to listen for incoming messages
                        val deliverCallback = DeliverCallback { _, delivery ->
                            val message = String(delivery.body, Charsets.UTF_8)
                            Log.d("RabbitMQ", "Message received: $message")

                            if (message == "skip_question") {
                                Log.d("RabbitMQ", "went inside skip_question")
                                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                                    skipQuestion()
                                }
                            } else if (message == "question_complete") {
                                Log.d("RabbitMQ", "went inside question_complete")
                                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                                    turnComplete() // Run UI updates on the main thread
                                }
                            } else if (message == "update_players") {
                                getBindings(factory.host, factory.username, factory.password, viewModel._roomName)

                                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                                    binding.textViewQuestions.text = viewModel._questions[questionIndex]
                                    binding.textViewName.text = viewModel._players[playerIndex]
                                    binding.textViewName.setTextColor(viewModel._foreground[playerIndex])
                                }

                            }
                        }

                        // Start consuming messages
                        channel.basicConsume(viewModel._player, true, deliverCallback) { _ ->
                            Log.d("RabbitMQ", "Consumer cancelled for queue: ${viewModel._player}")
                        }

                        Log.d("RabbitMQ", "Consumer started for queue: ${viewModel._player}")

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

                    if (jsonArray.length() < 2) {
                        deleteEntireCollection(
                            viewModel._roomName,
                            batchSize = 10
                        ) { success, exception ->
                            if (success) {
                                Log.d("Firestore", "Collection deleted successfully.")
                            } else {
                                Log.e("Firestore", "Failed to delete collection.", exception)
                            }
                        }
                        closeRoom()
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

    private fun closeRoom() {
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
                        for (i in 0 until viewModel._players.size) {
                            channel.queueDelete(viewModel._players[i])
                        }
                        channel.exchangeDelete(viewModel._roomName)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e("RabbitMQ", "Error removing player: ${e.message}")
                }
            }
        }

        findNavController().navigate(R.id.action_gamingPageFragment_to_mainPageFragment)
    }

    private fun updateButtonVisibility() {
        if (binding.textViewName.text == viewModel._player) {
            binding.imageViewCross.visibility = View.GONE
            binding.imageViewTick.visibility = View.GONE
        } else {
            binding.imageViewCross.visibility = View.VISIBLE
            binding.imageViewTick.visibility = View.VISIBLE
        }
    }

    private fun setupChatRecyclerView() {
        val query = FirebaseFirestore.getInstance().collection(viewModel._roomName)
//        val query = FirebaseUtils.getChatroomMessageReference("name")
            .orderBy("timestamp", Query.Direction.ASCENDING)

        // Use the query to set up the RecyclerView adapter
        val options = FirestoreRecyclerOptions.Builder<ChatModel>()
            .setQuery(query, ChatModel::class.java)
            .build()

        query.get()
            .addOnSuccessListener { documents ->
                for (document in documents) {
                    Log.d("FirestoreDebug", "Document data: ${document.data}")
                }
            }
            .addOnFailureListener { e ->
                Log.e("FirestoreDebug", "Error fetching documents", e)
            }

        val adapter = ChatRecyclerAdapter(options, requireContext(), viewModel)
        val manager = LinearLayoutManager(requireContext())
        manager.stackFromEnd = true
        recyclerView.adapter = adapter
        adapter.startListening()

        adapter.registerAdapterDataObserver(object : AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                super.onItemRangeInserted(positionStart, itemCount)
                if (adapter.itemCount > 0) {
                    // Scroll to the last item (bottom of the RecyclerView)
                    recyclerView.smoothScrollToPosition(adapter.itemCount - 1)
                }
            }
        })

    }


    fun deleteEntireCollection(collectionName: String, batchSize: Int = 10, onComplete: (Boolean, Exception?) -> Unit) {
        val db = FirebaseFirestore.getInstance()
        val collectionRef = db.collection(collectionName)

        fun deleteBatch() {
            collectionRef.limit(batchSize.toLong()).get().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val documents = task.result?.documents
                    if (documents != null && documents.isNotEmpty()) {
                        // Delete each document in the batch
                        for (document in documents) {
                            document.reference.delete()
                        }
                        // Call deleteBatch recursively until the collection is empty
                        deleteBatch()
                    } else {
                        // Collection is fully deleted
                        onComplete(true, null)
                    }
                } else {
                    // Handle failure
                    onComplete(false, task.exception)
                }
            }
        }

        deleteBatch()
    }

}