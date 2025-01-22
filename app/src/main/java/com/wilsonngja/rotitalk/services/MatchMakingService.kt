package com.wilsonngja.rotitalk.services

import android.util.Log
import com.rabbitmq.client.ConnectionFactory
import com.wilsonngja.rotitalk.utils.ColorPair
import com.wilsonngja.rotitalk.utils.ColourPair
import com.wilsonngja.rotitalk.viewmodel.MatchingPageViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Base64
import java.util.concurrent.TimeUnit
import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.graphics.PorterDuff
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.rabbitmq.client.AMQP
import com.rabbitmq.client.DeliverCallback
import com.wilsonngja.rotitalk.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okhttp3.Credentials
import okio.IOException
import org.json.JSONArray
import io.github.cdimascio.dotenv.Dotenv



class MatchMakingService(private val context: Context) {
    val dotenv = Dotenv.configure()
        .directory("/assets")
        .filename("env")
        .ignoreIfMissing()
        .load()

    // In MatchMakingService.kt
    private val factory = ConnectionFactory().apply {
        host = dotenv["HOST"] ?: throw IllegalStateException("HOST not found in env file")
        port = dotenv["PORT"]?.toInt() ?: 5671
        username = dotenv["USERNAME"] ?: throw IllegalStateException("USERNAME not found in env file")
        password = dotenv["PASSWORD"] ?: throw IllegalStateException("PASSWORD not found in env file")

        // For SSL/TLS connection
        useSslProtocol()
    }




    val playersName = mutableListOf("txtViewPlayer1Name", "txtViewPlayer2Name", "txtViewPlayer3Name", "txtViewPlayer4Name", "txtViewPlayer5Name")
    val playersBackground = mutableListOf("player1Background", "player2Background", "player3Background", "player4Background", "player5Background")
    val playersAbbrev = mutableListOf("txtViewPlayer1Abbrev", "txtViewPlayer2Abbrev", "txtViewPlayer3Abbrev", "txtViewPlayer4Abbrev", "txtViewPlayer5Abbrev")

    suspend fun roomExists(exchangeName: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                factory.newConnection().use { connection ->
                    connection.createChannel().use { channel ->
                        if (exchangeName.isEmpty()) {
                            Log.e("RabbitMQ", "Exchange name is empty")
                            return@use false
                        }


                        val apiUrl = "https://${factory.host}:15671/api/exchanges/%2F/$exchangeName"

                        val authHeader = "Basic " + Base64.getEncoder()
                            .encodeToString("${factory.username}:${factory.password}".toByteArray())

                        val client = OkHttpClient.Builder()
                            .connectTimeout(10, TimeUnit.SECONDS)
                            .readTimeout(30, TimeUnit.SECONDS)
                            .build()

                        val request = Request.Builder()
                            .url(apiUrl)
                            .addHeader("Authorization", authHeader)
                            .build()

                        val response = client.newCall(request).execute()
                        response.isSuccessful
                    }
                }
            } catch (e: Exception) {
                Log.e("MatchMakingService", "Error checking room existence", e)
                false
            }
        }
    }

    suspend fun createRoom(exchangeName: String) {
        withContext(Dispatchers.IO) {
            try {
                factory.newConnection().use { connection ->
                    connection.createChannel().use { channel ->
                        if (exchangeName.isEmpty()) {
                            Log.e("RabbitMQ", "Exchange name is empty")
                            throw IllegalArgumentException("Exchange name is empty")
                        }
                        channel.exchangeDeclare(exchangeName, "fanout", true)
                    }
                }
            } catch (e: Exception) {
                Log.e("MatchMakingService", "Error creating room", e)
                throw e
            }
        }
    }

    suspend fun deleteExchange(exchangeName: String) {
        withContext(Dispatchers.IO) {
            try {
                factory.newConnection().use { connection ->
                    connection.createChannel().use { channel ->
                        if (exchangeName.isEmpty()) {
                            Log.e("RabbitMQ", "Exchange name is empty")
                            throw IllegalArgumentException("Exchange name is empty")
                        }

                        // Delete the exchange
                        channel.exchangeDelete(exchangeName)
                        Log.d("RabbitMQ", "Deleted exchange: $exchangeName")
                    }
                }
            } catch (e: Exception) {
                Log.e("MatchMakingService", "Error deleting exchange", e)
                throw e
            }
        }
    }

    fun updatePlayersUI(view: View, viewModel: MatchingPageViewModel) {
        // Ensure UI updates run on the main thread
        resetPlayersUI(view, viewModel)
        Log.d("RabbitMQ", "MatchMakingService: ${viewModel._players.toString()}")
        view.post {
            for (i in 0 until viewModel._players.size) {
                val colorPair = ColourPair.getRandomPair()

                val playerName = playersName[i]
                val playerNameId = context.resources.getIdentifier(playerName, "id", context.packageName)
                val textViewName: TextView? = view.findViewById(playerNameId)
                textViewName?.text = viewModel._players[i]
                textViewName?.setTextColor(colorPair.foreground)
                viewModel._players_foreground_color.add(colorPair.foreground)

                val playerBackground = playersBackground[i]
                val playerBackgroundId = context.resources.getIdentifier(playerBackground, "id", context.packageName)
                val frameLayoutBackground: FrameLayout? = view.findViewById(playerBackgroundId)
                val drawable = ContextCompat.getDrawable(context, R.drawable.player1_background)
                drawable?.setColorFilter(colorPair.background, PorterDuff.Mode.SRC_IN)
                frameLayoutBackground?.background = drawable
                viewModel._players_background_color.add(colorPair.background)

                val playerAbbrev = playersAbbrev[i]
                val playerAbbrevId = context.resources.getIdentifier(playerAbbrev, "id", context.packageName)
                val textViewAbbrev: TextView? = view.findViewById(playerAbbrevId)
                textViewAbbrev?.text = viewModel._players[i][0].toString()
                textViewAbbrev?.setTextColor(colorPair.foreground)
            }
        }
    }

    fun resetPlayersUI(view: View, viewModel: MatchingPageViewModel) {
        view.post {
            for (i in 0 until playersName.size) {


                val playerName = playersName[i]
                val playerNameId = context.resources.getIdentifier(playerName, "id", context.packageName)
                val textViewName: TextView? = view.findViewById(playerNameId)
                textViewName?.text = ""
                val color = ContextCompat.getColor(context, R.color.invalid_primary)
                textViewName?.setTextColor(color)

                val playerBackground = playersBackground[i]
                val playerBackgroundId = context.resources.getIdentifier(playerBackground, "id", context.packageName)
                val frameLayoutBackground: FrameLayout? = view.findViewById(playerBackgroundId)
                val drawable = ContextCompat.getDrawable(context, R.drawable.player1_background)
                val backgroundColor = ContextCompat.getColor(context, R.color.invalid_secondary)
                drawable?.setColorFilter(backgroundColor, PorterDuff.Mode.SRC_IN)
                frameLayoutBackground?.background = drawable

                val playerAbbrev = playersAbbrev[i]
                val playerAbbrevId = context.resources.getIdentifier(playerAbbrev, "id", context.packageName)
                val textViewAbbrev: TextView? = view.findViewById(playerAbbrevId)
                textViewAbbrev?.text = "?"
                val abbrevColor = ContextCompat.getColor(context, R.color.invalid_primary)
                textViewName?.setTextColor(abbrevColor)
            }
        }
    }
}
