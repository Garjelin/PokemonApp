package com.example.pokemonapp.presentation.viewmodels

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.LoadState
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.cachedIn
import androidx.paging.map
import com.example.pokemonapp.data.local.room.DatabaseProvider
import com.example.pokemonapp.data.remote.api.ApiClient
import com.example.pokemonapp.data.remote.api.ApiService
import com.example.pokemonapp.domain.models.Pokemon
import com.example.pokemonapp.domain.repository.PokemonRepositoryImpl
import com.example.pokemonapp.domain.repository.SortedRoomPagingSource
import com.example.pokemonapp.domain.usecases.GetPokemonsUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    private val _selectedTypes = MutableStateFlow<Set<String>>(emptySet())
    val selectedTypes: StateFlow<Set<String>> = _selectedTypes

    private val repository = PokemonRepositoryImpl(
        apiService = ApiClient.retrofit.create(ApiService::class.java),
        pokemonDao = DatabaseProvider.getDatabase(context).pokemonDao()
    )

    private val getPokemonsUseCase = GetPokemonsUseCase(repository)

    private var fetchJob: Job? = null
    private var isSorted = false // Флаг для отслеживания, применена ли сортировка
    private var lastLoadedOffset = 0 // Последний загруженный offset
    private val loadMutex = Mutex() // Для синхронизации вызовов loadNextFromApi

    init {
        fetchPokemons()
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        isSorted = false // Сбрасываем сортировку при новом поиске
        lastLoadedOffset = 0 // Сбрасываем offset для нового списка
        fetchPokemons()
    }

    fun updateSelectedTypes(types: Set<String>) {
        _selectedTypes.value = types
        isSorted = false // Сбрасываем сортировку при фильтрации
        lastLoadedOffset = 0 // Сбрасываем offset для нового списка
        fetchPokemons()
    }

    private fun fetchPokemons() {
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            try {
                val flow = if (_searchQuery.value.isNotEmpty()) {
                    // Логика поиска остается без изменений
                    val searchResult = repository.searchPokemons(_searchQuery.value)
                    Pager(config = PagingConfig(pageSize = 20)) {
                        object : PagingSource<Int, Pokemon>() {
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
                } else if (isSorted) {
                    // Логика сортировки остается без изменений
                    Pager(config = PagingConfig(pageSize = 20, initialLoadSize = 60)) {
                        SortedRoomPagingSource(
                            pokemonDao = repository.pokemonDao,
                            criteria = _sortCriteria.value,
                            ascending = _sortDirection.value == "ascending",
                            onLoadNext = { page -> launch { loadNextFromApi(page) } }
                        )
                    }.flow.cachedIn(viewModelScope)
                } else if (_selectedTypes.value.isNotEmpty()) {
                    // Новая логика фильтрации по типу
                    val filterFlow = _selectedTypes.value.map { type ->
                        repository.filterPokemonsByType(type)
                    }.reduce { acc, flow ->
                        acc.combine(flow) { list1, list2 ->
                            (list1 + list2).distinctBy { it.id }
                        }
                    }
                    Pager(config = PagingConfig(pageSize = 20)) {
                        object : PagingSource<Int, Pokemon>() {
                            override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Pokemon> {
                                val page = params.key ?: 0
                                val start = page * params.loadSize
                                val end = (page + 1) * params.loadSize
                                val data = filterFlow.first().subList(
                                    start.coerceAtLeast(0),
                                    end.coerceAtMost(filterFlow.first().size)
                                )
                                return LoadResult.Page(
                                    data = data,
                                    prevKey = if (page <= 0) null else page - 1,
                                    nextKey = if (end >= filterFlow.first().size) null else page + 1
                                )
                            }
                            override fun getRefreshKey(state: PagingState<Int, Pokemon>): Int? = null
                        }
                    }.flow.cachedIn(viewModelScope)
                } else {
                    // Загружаем все покемоны без фильтров
                    getPokemonsUseCase().cachedIn(viewModelScope)
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

    private suspend fun loadNextFromApi(currentPage: Int) {
        loadMutex.withLock {
            // Корректный расчёт offset: после 60 идёт 80, 100 и т.д.
            val offset = if (lastLoadedOffset == 0) 0 else lastLoadedOffset
            val limit = if (lastLoadedOffset == 0) 60 else 20
            Log.d("PokemonListViewModel", "Loading from API: page $currentPage, limit $limit, offset $offset")
            try {
                val pokemons = repository.getPokemonsFromApi(limit, offset)
                if (pokemons.isNotEmpty()) {
                    lastLoadedOffset = offset + pokemons.size // Обновляем offset
                    Log.d("PokemonListViewModel", "Updated lastLoadedOffset to $lastLoadedOffset")
                    if (isSorted) {
                        // Перезапускаем fetchPokemons для обновления отсортированного списка
                        fetchPokemons()
                    }
                }
            } catch (e: Exception) {
                Log.e("PokemonListViewModel", "Error loading next page from API: ${e.message}")
                // Не устанавливаем ошибку в UI, так как это фоновая операция
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _sortCriteria.value = "Number"
            _sortDirection.value = "ascending"
            _searchQuery.value = "" // Сбрасываем поиск
            _pokemonList.value = PagingData.empty() // Очистка текущих данных
            isSorted = false // Сбрасываем сортировку
            lastLoadedOffset = 0 // Сбрасываем offset
            // Очищаем базу данных
            repository.clearCache()
            fetchPokemons()
        }
    }

    fun sortPokemons(criteria: String, direction: String) {
        _sortCriteria.value = criteria
        _sortDirection.value = direction
        viewModelScope.launch {
            try {
                // Валидация критерия сортировки
                require(criteria in listOf("Number", "Name", "HP", "Attack", "Defense")) {
                    "Invalid sort criteria: $criteria"
                }
                isSorted = true
                fetchPokemons() // Запускаем подгрузку с сортировкой
                _error.value = null
            } catch (e: Exception) {
                Log.e("PokemonListViewModel", "Error sorting pokemons: ${e.message}")
                _error.value = "Failed to sort pokemons: ${e.message}"
            }
        }
    }

    fun isSorted(): Boolean = isSorted
}