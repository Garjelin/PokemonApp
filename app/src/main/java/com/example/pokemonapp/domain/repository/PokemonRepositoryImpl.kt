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
                Pokemon(
                    id = id,
                    name = item.name,
                    imageUrl = details?.sprites?.front_default ?: "",
                    types = details?.types?.map { it.type.name } ?: emptyList()
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
            // Загружаем из Room
            val cached = pokemonDao.getAllSync()
            if (cached.isNotEmpty()) {
                Log.d("PokemonPagingSource", "Loaded ${cached.size} pokemons from Room")
                LoadResult.Page(
                    data = cached.map { it.toPokemon() },
                    prevKey = null,
                    nextKey = null // Ограничиваем пагинацию в оффлайн
                )
            } else {
                Log.w("PokemonPagingSource", "Room cache is empty")
                LoadResult.Error(e)
            }
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
