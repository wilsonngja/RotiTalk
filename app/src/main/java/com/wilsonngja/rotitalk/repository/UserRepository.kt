package com.wilsonngja.rotitalk.repository

import com.wilsonngja.rotitalk.model.Player

class UserRepository {
    fun getUsers() : List<Player> {
        // Mocked data
        return listOf(
            Player("Alice"),
            Player("Bob"),
            Player("Charlie")
        )
    }
}