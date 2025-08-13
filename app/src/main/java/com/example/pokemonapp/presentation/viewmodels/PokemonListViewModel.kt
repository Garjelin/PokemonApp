package com.example.pokemonapp.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pokemonapp.data.remote.api.ApiClient
import com.example.pokemonapp.data.remote.api.ApiService
import com.example.pokemonapp.domain.models.Pokemon
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class PokemonListViewModel : ViewModel() {
    private val _pokemonList = MutableStateFlow<List<Pokemon>>(emptyList())
    val pokemonList: StateFlow<List<Pokemon>> = _pokemonList

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val apiService = ApiClient.retrofit.create(ApiService::class.java)

    init {
        fetchPokemons()
    }

    fun fetchPokemons() {
        viewModelScope.launch {
            try {
                val response = apiService.getPokemonList(limit = 20, offset = 0)
                val pokemons = response.results.mapIndexed { index, item ->
                    val id = index + 1 // Временное решение, позже используем URL
                    val details = try {
                        apiService.getPokemonDetails(id)
                    } catch (e: Exception) {
                        _error.value = "Failed to fetch details for ${item.name}"
                        null
                    }
                    Pokemon(
                        id = id,
                        name = item.name,
                        imageUrl = details?.sprites?.front_default ?: "",
                        types = details?.types?.map { it.type.name } ?: emptyList()
                    )
                }
                _pokemonList.value = pokemons
            } catch (e: Exception) {
                _error.value = "No internet connection or API error: ${e.message}"
            }
        }
    }
}
