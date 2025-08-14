package com.example.pokemonapp.domain.repository

import androidx.paging.PagingData
import com.example.pokemonapp.domain.models.Pokemon
import kotlinx.coroutines.flow.Flow

interface PokemonRepository {
    fun getPokemons(): Flow<PagingData<Pokemon>>
    fun searchPokemons(query: String): Flow<List<Pokemon>>
    fun filterPokemonsByType(type: String): Flow<List<Pokemon>>
}