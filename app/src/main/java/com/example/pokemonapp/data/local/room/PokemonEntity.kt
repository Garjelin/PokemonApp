package com.example.pokemonapp.data.local.room

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.pokemonapp.domain.models.Pokemon

@Entity(tableName = "pokemon")
data class PokemonEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val imageUrl: String,
    val types: String, // Храним как строку, разделенную запятыми, например: "grass,poison"
    val hp: Int,
    val attack: Int,
    val defense: Int
) {
    // Конвертация в domain модель
    fun toPokemon() = Pokemon(
        id = id,
        name = name,
        imageUrl = imageUrl,
        types = types.split(",").filter { it.isNotEmpty() },
        hp = hp,
        attack = attack,
        defense = defense
    )

    companion object {
        // Конвертация из domain модели в Entity
        fun fromPokemon(pokemon: Pokemon) = PokemonEntity(
            id = pokemon.id,
            name = pokemon.name,
            imageUrl = pokemon.imageUrl,
            types = pokemon.types.joinToString(","),
            hp = pokemon.hp,
            attack = pokemon.attack,
            defense = pokemon.defense
        )
    }
}
