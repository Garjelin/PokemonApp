package com.example.pokemonapp.presentation.viewmodels

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.LoadState
import androidx.paging.Pager
import androidx.paging.PagingData
import androidx.paging.PagingState
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.IOException

class PokemonListViewModel(context: Context) : ViewModel() {
    private val _pokemonList = MutableStateFlow<PagingData<Pokemon>>(PagingData.empty())
    val pokemonList: StateFlow<PagingData<Pokemon>> = _pokemonList

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    val _sortCriteria = MutableStateFlow("Number")
    val sortCriteria: StateFlow<String> = _sortCriteria

    val _sortDirection = MutableStateFlow("ascending")
    val sortDirection: StateFlow<String> = _sortDirection

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val repository = PokemonRepositoryImpl(
        apiService = ApiClient.retrofit.create(ApiService::class.java),
        pokemonDao = DatabaseProvider.getDatabase(context).pokemonDao()
    )

    private val getPokemonsUseCase = GetPokemonsUseCase(repository)

    private var fetchJob: Job? = null

    init {
        fetchPokemons()
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        fetchPokemons()
    }

    private fun fetchPokemons() {
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            try {
                val flow = if (_searchQuery.value.isNotEmpty()) {
                    // Преобразуем результат поиска в PagingData
                    val searchResult = repository.searchPokemons(_searchQuery.value)
                    Pager(config = androidx.paging.PagingConfig(pageSize = 20)) {
                        object : androidx.paging.PagingSource<Int, Pokemon>() {
                            override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Pokemon> {
                                val page = params.key ?: 0
                                val start = page * params.loadSize
                                val end = (page + 1) * params.loadSize
                                val data = searchResult.first().subList(
                                    start.coerceAtLeast(0),
                                    end.coerceAtMost(searchResult.first().size)
                                )
                                return LoadResult.Page(
                                    data = data,
                                    prevKey = if (page <= 0) null else page - 1,
                                    nextKey = if (end >= searchResult.first().size) null else page + 1
                                )
                            }

                            override fun getRefreshKey(state: PagingState<Int, Pokemon>): Int? = null
                        }
                    }.flow.cachedIn(viewModelScope)
                } else {
//                    getPokemonsUseCase()
                    repository.getSortedPokemons(_sortCriteria.value, _sortDirection.value == "ascending")
                        .cachedIn(viewModelScope)
                }
                flow.collectLatest { pagingData ->
                    Log.d("PokemonListViewModel", "Received new PagingData")
                    _pokemonList.value = pagingData
                    _error.value = null
                    _isRefreshing.value = false
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
            _sortCriteria.value = "Number"
            _sortDirection.value = "ascending"
            _pokemonList.value = PagingData.empty() // Очистка текущих данных
            fetchPokemons()
        }
    }

    fun sortPokemons(criteria: String, direction: String) {
        val ascending = direction == "ascending"
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            try {
//                getPokemonsUseCase()
                val flow = repository.getSortedPokemons(criteria, ascending)
                flow.cachedIn(viewModelScope).collectLatest { pagingData ->
                    _pokemonList.value = pagingData
                    _error.value = null
                    _isRefreshing.value = false
                }
            } catch (e: Exception) {
                _error.value = "Failed to sort pokemons: ${e.message}"
                _isRefreshing.value = false
            }
        }
    }
}