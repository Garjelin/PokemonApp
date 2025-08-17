package com.example.pokemonapp.domain.repository

import android.util.Log
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.example.pokemonapp.data.local.room.PokemonDao
import com.example.pokemonapp.data.local.room.PokemonEntity
import com.example.pokemonapp.data.remote.api.ApiService
import com.example.pokemonapp.domain.models.Pokemon
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.IOException

class PokemonRepositoryImpl(
    private val apiService: ApiService,
    val pokemonDao: PokemonDao
) : PokemonRepository {

    override fun getPokemons(): Flow<PagingData<Pokemon>> {
        return Pager(
            config = PagingConfig(pageSize = 20, initialLoadSize = 60),
            pagingSourceFactory = { PokemonPagingSource(apiService, pokemonDao) }
        ).flow
    }

    override fun searchPokemons(query: String): Flow<List<Pokemon>> {
        return if (query.startsWith("type:", ignoreCase = true)) {
            val type = query.removePrefix("type:").trim()
            pokemonDao.getByType(type).map { entities -> entities.map { it.toPokemon() } }
        } else {
            pokemonDao.getByName("%$query%").map { entities -> entities.map { it.toPokemon() } }
        }
    }

    override fun filterPokemonsByType(type: String): Flow<List<Pokemon>> {
        return pokemonDao.getByType(type).map { entities ->
            entities.map { it.toPokemon() }
        }
    }

    override suspend fun clearCache() {
        pokemonDao.clearAll()
    }

    override suspend fun getPokemonsFromApi(limit: Int, offset: Int): List<Pokemon> {
        try {
            val response = apiService.getPokemonList(limit, offset)
            val pokemons = response.results.mapIndexed { index, result ->
                val id = offset + index + 1 // Корректный расчёт ID
                Log.d("PokemonRepositoryImpl", "getPokemonsFromApi ${result.name}, id: $id")
                val details = apiService.getPokemonDetails(id)
                Pokemon(
                    id = id,
                    name = result.name,
                    imageUrl = details.sprites.front_default ?: "",
                    hp = details.stats.find { it.stat.name == "hp" }?.base_stat ?: 0,
                    attack = details.stats.find { it.stat.name == "attack" }?.base_stat ?: 0,
                    defense = details.stats.find { it.stat.name == "defense" }?.base_stat ?: 0,
                    types = details.types.map { it.type.name }
                )
            }
            pokemonDao.insertAll(pokemons.map { it.toPokemonEntity() })
            Log.d("PokemonRepositoryImpl", "Saved ${pokemons.size} pokemons to Room")
            return pokemons
        } catch (e: Exception) {
            Log.e("PokemonRepositoryImpl", "Error fetching pokemons from API: ${e.message}")
            throw e
        }
    }
}

class SortedRoomPagingSource(
    private val pokemonDao: PokemonDao,
    private val criteria: String,
    private val ascending: Boolean,
    private val onLoadNext: (Int) -> Unit // Callback для вызова loadNextFromApi
) : PagingSource<Int, Pokemon>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Pokemon> {
        val page = params.key ?: 0
        val limit = params.loadSize
        val offset = if (page == 0 && params.loadSize == 60) 0 else page * 20

        return try {
            Log.d("SortedRoomPagingSource", "Loading page $page, limit $limit, offset $offset, criteria $criteria, ascending $ascending")
            val data = if (ascending) {
                pokemonDao.getSortedPageAsc(criteria, limit, offset)
            } else {
                pokemonDao.getSortedPageDesc(criteria, limit, offset)
            }.map { it.toPokemon() }

            // Если данных меньше, чем запрошено, подгружаем следующую страницу из API
            if (data.size < limit && page >= 0) {
                Log.d("SortedRoomPagingSource", "Data size ${data.size} < limit $limit, triggering loadNextFromApi for page ${page + 1}")
                onLoadNext(page + 1)
            }

            LoadResult.Page(
                data = data,
                prevKey = if (page == 0) null else page - 1,
                nextKey = if (data.isEmpty() || data.size < limit) null else if (page == 0 && params.loadSize == 60) 3 else page + 1
            )
        } catch (e: Exception) {
            Log.e("SortedRoomPagingSource", "Error loading sorted page: ${e.message}")
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, Pokemon>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
        }
    }
}

class PokemonPagingSource(
    private val apiService: ApiService,
    private val pokemonDao: PokemonDao
) : PagingSource<Int, Pokemon>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Pokemon> {
        val page = params.key ?: 0
        val limit = params.loadSize
        val offset = if (page == 0 && params.loadSize == 60) 0 else page * 20 // Учитываем initialLoadSize=60

        return try {
            Log.d("PokemonPagingSource", "Loading page $page, limit $limit, offset $offset")
            // 1. Загружаем данные с API
            val response = apiService.getPokemonList(limit = limit, offset = offset)
            val pokemons = response.results.mapIndexed { index, item ->
                val id = offset + index + 1
                Log.d("PokemonRepositoryImpl", "PokemonPagingSource ${item.name}, id: $id")
                val details = try {
                    apiService.getPokemonDetails(id)
                } catch (e: Exception) {
                    Log.e("PokemonPagingSource", "Failed to fetch details for ${item.name}: ${e.message}")
                    null
                }
                val stats = details?.stats?.associate { it.stat.name to it.base_stat } ?: emptyMap()
                Pokemon(
                    id = id,
                    name = item.name,
                    imageUrl = details?.sprites?.front_default ?: "",
                    types = details?.types?.map { it.type.name } ?: emptyList(),
                    hp = stats["hp"] ?: 0,
                    attack = stats["attack"] ?: 0,
                    defense = stats["defense"] ?: 0
                )
            }

            // 2. Сохраняем в Room
            pokemonDao.insertAll(pokemons.map { PokemonEntity.fromPokemon(it) })
            Log.d("PokemonPagingSource", "Saved ${pokemons.size} pokemons to Room")

            LoadResult.Page(
                data = pokemons,
                prevKey = if (page == 0) null else page - 1,
                nextKey = if (pokemons.isEmpty()) null else if (page == 0 && params.loadSize == 60) 3 else page + 1
            )
        } catch (e: IOException) {
            Log.e("PokemonPagingSource", "Network error: ${e.message}")
            // Fallback на кэш
            val cached = pokemonDao.getAllSync().map { it.toPokemon() }
            val start = offset
            val end = offset + limit
            val pagedData = if (start < cached.size) {
                cached.subList(start, end.coerceAtMost(cached.size))
            } else {
                emptyList()
            }
            LoadResult.Page(
                data = pagedData,
                prevKey = if (page == 0) null else page - 1,
                nextKey = if (pagedData.isEmpty()) null else if (page == 0 && params.loadSize == 60) 3 else page + 1
            )
        } catch (e: Exception) {
            Log.e("PokemonPagingSource", "API error: ${e.message}")
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, Pokemon>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
        }
    }
}
