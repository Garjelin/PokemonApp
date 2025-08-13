package com.example.pokemonapp.data.remote.api

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {
    @GET("pokemon")
    suspend fun getPokemonList(
        @Query("limit") limit: Int,
        @Query("offset") offset: Int
    ): PokemonListResponse

    @GET("pokemon/{id}")
    suspend fun getPokemonDetails(@Path("id") id: Int): PokemonDetailsResponse
}

data class PokemonListResponse(
    val results: List<PokemonItem>
)

data class PokemonItem(
    val name: String,
    val url: String // Содержит ID, например, https://pokeapi.co/api/v2/pokemon/1/
)

data class PokemonDetailsResponse(
    val id: Int,
    val name: String,
    val sprites: Sprites,
    val types: List<TypeEntry>
)

data class Sprites(
    val front_default: String // URL изображения
)

data class TypeEntry(
    val type: Type
)

data class Type(
    val name: String
)
