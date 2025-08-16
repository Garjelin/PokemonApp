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
    val apiService: ApiService,
    private val pokemonDao: PokemonDao
) : PokemonRepository {

    override fun getPokemons(): Flow<PagingData<Pokemon>> {
        return Pager(
            config = PagingConfig(
                pageSize = 20,
                initialLoadSize = 60,
                enablePlaceholders = false
            ),
            pagingSourceFactory = {
                PokemonPagingSource(apiService, pokemonDao)
            }
        ).flow
    }

    override fun searchPokemons(query: String): Flow<List<Pokemon>> {
        return pokemonDao.getByName("%$query%").map { entities ->
            entities.map { it.toPokemon() }
        }
    }

    override fun filterPokemonsByType(type: String): Flow<List<Pokemon>> {
        return pokemonDao.getByType(type).map { entities ->
            entities.map { it.toPokemon() }
        }
    }

    fun getSortedPokemons(
        criteria: String,
        ascending: Boolean
    ): Flow<PagingData<Pokemon>> {
        return Pager(
            config = PagingConfig(
                pageSize = 20,
                initialLoadSize = 60,
                enablePlaceholders = false
            ),
            pagingSourceFactory = {
                PokemonSortedPagingSource(apiService, pokemonDao, criteria, ascending)
            }
        ).flow
    }
}

class PokemonSortedPagingSource(
    private val apiService: ApiService,
    private val pokemonDao: PokemonDao,
    private val criteria: String,
    private val ascending: Boolean
) : PagingSource<Int, Pokemon>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Pokemon> {
        val page = params.key ?: 0
        val limit = params.loadSize
        val offset = page * limit

        // PokeAPI ограничивает offset 100, поэтому используем другой подход
        if (offset >= 100) {
            return LoadResult.Page(
                data = emptyList(),
                prevKey = page - 1,
                nextKey = null
            )
        }

        return try {
            Log.d("PokemonSortedPaging", "Loading page $page, limit $limit, offset $offset")

            // 1. Загружаем данные с API
            val response = apiService.getPokemonList(limit = limit, offset = offset)
            val pokemons = response.results.mapIndexed { index, result ->
                val id = offset + index + 1
                Log.d("PokemonPaging", "Fetching details for ${result.name}, id: $id")
                val details = apiService.getPokemonDetails(id)
                Pokemon(
                    id = id,
                    name = result.name,
                    imageUrl = details.sprites.front_default,
                    types = details.types.map { it.type.name },
                    hp = details.stats.find { it.stat.name == "hp" }?.base_stat ?: 0,
                    attack = details.stats.find { it.stat.name == "attack" }?.base_stat ?: 0,
                    defense = details.stats.find { it.stat.name == "defense" }?.base_stat ?: 0
                )
            }

            // 2. Сохраняем в базу
            pokemonDao.insertAll(pokemons.map { PokemonEntity.fromPokemon(it) })

            // 3. Получаем ВСЕ данные из кэша для сортировки
            val allPokemons = pokemonDao.getAllSync().map { it.toPokemon() }

            // 4. Сортируем все данные
            val sorted = when (criteria) {
                "Number" -> allPokemons.sortedBy { it.id }
                "Name" -> allPokemons.sortedBy { it.name }
                "HP" -> allPokemons.sortedBy { it.hp }
                "Attack" -> allPokemons.sortedBy { it.attack }
                "Defence" -> allPokemons.sortedBy { it.defense }
                else -> allPokemons
            }.let { if (!ascending) it.reversed() else it }

            // 5. Пагинация по отсортированным данным
            val start = page * limit
            val end = (page + 1) * limit
            val pagedData = if (start < sorted.size) {
                sorted.subList(start, end.coerceAtMost(sorted.size))
            } else {
                emptyList()
            }

            LoadResult.Page(
                data = pagedData,
                prevKey = if (page == 0) null else page - 1,
                nextKey = if (end >= sorted.size || offset + limit >= 100) null else page + 1
            )

        } catch (e: Exception) {
            // Fallback на кэш при ошибке
            val cached = pokemonDao.getAllSync().map { it.toPokemon() }
            val sortedCached = when (criteria) {
                "Number" -> cached.sortedBy { it.id }
                "Name" -> cached.sortedBy { it.name }
                "HP" -> cached.sortedBy { it.hp }
                "Attack" -> cached.sortedBy { it.attack }
                "Defence" -> cached.sortedBy { it.defense }
                else -> cached // или можно бросить исключение, если критерий неожиданный
            }.let { if (!ascending) it.reversed() else it }

            val start = page * limit
            val end = (page + 1) * limit
            val pagedData = if (start < sortedCached.size) {
                sortedCached.subList(start, end.coerceAtMost(sortedCached.size))
            } else {
                emptyList()
            }

            LoadResult.Page(
                data = pagedData,
                prevKey = if (page == 0) null else page - 1,
                nextKey = if (end >= sortedCached.size) null else page + 1
            )
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
            val response = apiService.getPokemonList(limit = limit, offset = offset)
            val pokemons = response.results.mapIndexed { index, item ->
                val id = offset + index + 1
                Log.d("PokemonPagingSource", "Fetching details for pokemon: ${item.name}, id: $id")
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
            // Сохраняем в Room
            pokemonDao.insertAll(pokemons.map { PokemonEntity.fromPokemon(it) })
            Log.d("PokemonPagingSource", "Saved ${pokemons.size} pokemons to Room")
            LoadResult.Page(
                data = pokemons,
                prevKey = if (page == 0) null else page - 1,
                nextKey = if (pokemons.isEmpty()) null else if (page == 0 && params.loadSize == 60) 3 else page + 1
            )
        } catch (e: IOException) {
            Log.e("PokemonPagingSource", "Network error: ${e.message}")
            val cached = pokemonDao.getAllSync().map { it.toPokemon() }
            LoadResult.Page(
                data = cached,
                prevKey = null,
                nextKey = null
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
