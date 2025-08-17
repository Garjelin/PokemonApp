package com.example.pokemonapp.domain.models

import com.example.pokemonapp.data.local.room.PokemonEntity
import kotlin.String

data class Pokemon(
    val id: Int,
    val name: String,
    val imageUrl: String,
    val types: List<String> = emptyList(),
    val hp: Int = 0,
    val attack: Int = 0,
    val defense: Int = 0
) {
    fun toPokemonEntity(): PokemonEntity {
        return PokemonEntity(
            id = id,
            name = name,
            imageUrl = imageUrl,
            types = types.joinToString(","), // Преобразуем List<String> в строку для Room
            hp = hp,
            attack = attack,
            defense = defense
        )
    }
}