package com.example.pokemonapp.presentation.viewmodels

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import com.example.pokemonapp.data.local.room.DatabaseProvider
import com.example.pokemonapp.data.remote.api.ApiClient
import com.example.pokemonapp.data.remote.api.ApiService
import com.example.pokemonapp.domain.models.Pokemon
import com.example.pokemonapp.domain.repository.PokemonRepositoryImpl
import com.example.pokemonapp.domain.usecases.GetPokemonsUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.IOException

class PokemonListViewModel(context: Context) : ViewModel() {
    private val _pokemonList = MutableStateFlow<PagingData<Pokemon>>(PagingData.empty())
    val pokemonList: StateFlow<PagingData<Pokemon>> = _pokemonList

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val repository = PokemonRepositoryImpl(
        apiService = ApiClient.retrofit.create(ApiService::class.java),
        pokemonDao = DatabaseProvider.getDatabase(context).pokemonDao()
    )

    private val getPokemonsUseCase = GetPokemonsUseCase(repository)

    private var fetchJob: Job? = null

    init {
        fetchPokemons()
    }

    private fun fetchPokemons() {
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            try {
                getPokemonsUseCase().cachedIn(viewModelScope).collectLatest { pagingData ->
                    Log.d("PokemonListViewModel", "Received new PagingData")
                    _pokemonList.value = pagingData
                    _error.value = null
                    _isRefreshing.value = false // Сбрасываем после получения данных
                }
            } catch (e: Exception) {
                Log.e("PokemonListViewModel", "Error fetching pokemons: ${e.message}")
                _error.value = "Failed to load pokemons: ${e.message}"
                _isRefreshing.value = false
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _pokemonList.value = PagingData.empty() // Очистка текущих данных
            fetchPokemons()
        }
    }
}