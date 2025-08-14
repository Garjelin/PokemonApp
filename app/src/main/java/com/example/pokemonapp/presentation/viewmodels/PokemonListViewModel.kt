package com.example.pokemonapp.presentation.viewmodels

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pokemonapp.data.local.room.DatabaseProvider
import com.example.pokemonapp.data.local.room.PokemonEntity
import com.example.pokemonapp.data.remote.api.ApiClient
import com.example.pokemonapp.data.remote.api.ApiService
import com.example.pokemonapp.domain.models.Pokemon
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.IOException

class PokemonListViewModel(context: Context) : ViewModel() {
    private val _pokemonList = MutableStateFlow<List<Pokemon>>(emptyList())
    val pokemonList: StateFlow<List<Pokemon>> = _pokemonList

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val apiService = ApiClient.retrofit.create(ApiService::class.java)
    private val pokemonDao = DatabaseProvider.getDatabase(context).pokemonDao()

    init {
        fetchPokemons()
    }

    fun fetchPokemons() {
        viewModelScope.launch {
            try {
                Log.d("PokemonListViewModel", "Fetching pokemon list from API...")
                val response = apiService.getPokemonList(limit = 20, offset = 0)
                Log.d("PokemonListViewModel", "Received ${response.results.size} pokemons from API")
                val pokemons = response.results.mapIndexed { index, элемент ->
                    val id = index + 1
                    Log.d("PokemonListViewModel", "Fetching details for pokemon: ${элемент.name}")
                    val details = try {
                        apiService.getPokemonDetails(id)
                    } catch (e: Exception) {
                        Log.e("PokemonListViewModel", "Failed to fetch details for ${элемент.name}: ${e.message}")
                        _error.value = "Failed to fetch details for ${элемент.name}"
                        null
                    }
                    Pokemon(
                        id = id,
                        name = элемент.name,
                        imageUrl = details?.sprites?.front_default ?: "",
                        types = details?.types?.map { it.type.name } ?: emptyList()
                    )
                }
                Log.d("PokemonListViewModel", "Saving ${pokemons.size} pokemons to Room...")
                pokemonDao.insertAll(pokemons.map { PokemonEntity.fromPokemon(it) })
                Log.d("PokemonListViewModel", "Pokemons saved to Room")
                _pokemonList.value = pokemons
                Log.d("PokemonListViewModel", "Pokemon list updated: ${pokemons.size} items")
            } catch (e: IOException) {
                Log.e("PokemonListViewModel", "Network error: ${e.message}")
                _error.value = "No internet connection. Loading from cache..."
                pokemonDao.getAll().collectLatest { entities ->
                    Log.d("PokemonListViewModel", "Loaded ${entities.size} pokemons from Room")
                    _pokemonList.value = entities.map { it.toPokemon() }
                    if (entities.isEmpty()) {
                        Log.w("PokemonListViewModel", "Room cache is empty")
                        _error.value = "No data available offline"
                    } else {
                        _error.value = null // Сбрасываем ошибку, если данные есть
                    }
                }
            } catch (e: Exception) {
                Log.e("PokemonListViewModel", "API error: ${e.message}")
                _error.value = "API error: ${e.message}"
            }
        }
    }
}
