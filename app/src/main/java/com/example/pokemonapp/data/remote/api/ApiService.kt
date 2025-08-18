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
    val url: String
)

data class PokemonDetailsResponse(
    val id: Int,
    val name: String,
    val sprites: Sprites,
    val types: List<TypeEntry>,
    val stats: List<StatEntry>
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

data class StatEntry(
    val base_stat: Int,
    val stat: Stat
)

data class Stat(
    val name: String
)
