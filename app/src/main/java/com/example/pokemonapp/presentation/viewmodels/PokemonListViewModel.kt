package com.example.pokemonapp.presentation.viewmodels

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.pokemonapp.data.local.room.DatabaseProvider
import com.example.pokemonapp.data.remote.api.ApiClient
import com.example.pokemonapp.data.remote.api.ApiService
import com.example.pokemonapp.domain.models.Pokemon
import com.example.pokemonapp.domain.repository.PokemonRepositoryImpl
import com.example.pokemonapp.domain.usecases.GetPokemonsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PokemonListViewModel(context: Context) : ViewModel() {
    private val _pokemonList = MutableStateFlow<PagingData<Pokemon>>(PagingData.empty())
    val pokemonList: StateFlow<PagingData<Pokemon>> = _pokemonList

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val repository = PokemonRepositoryImpl(
        apiService = ApiClient.retrofit.create(ApiService::class.java),
        pokemonDao = DatabaseProvider.getDatabase(context).pokemonDao()
    )

    private val getPokemonsUseCase = GetPokemonsUseCase(repository)

    init {
        fetchPokemons()
    }

    fun fetchPokemons() {
        viewModelScope.launch {
            getPokemonsUseCase().cachedIn(viewModelScope).collectLatest { pagingData ->
                Log.d("PokemonListViewModel", "Received new PagingData")
                _pokemonList.value = pagingData
            }
        }
    }
}
