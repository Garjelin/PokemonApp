package com.example.pokemonapp.data.local.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PokemonDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(pokemons: List<PokemonEntity>)

    @Query("SELECT * FROM pokemon")
    fun getAll(): Flow<List<PokemonEntity>>

    @Query("SELECT * FROM pokemon")
    suspend fun getAllSync(): List<PokemonEntity>

    @Query("SELECT * FROM pokemon WHERE name LIKE :query")
    fun getByName(query: String): Flow<List<PokemonEntity>>

    @Query("SELECT * FROM pokemon WHERE types LIKE '%' || :type || '%'")
    fun getByType(type: String): Flow<List<PokemonEntity>>

    @Query("DELETE FROM pokemon")
    suspend fun clearAll()

    @Query("SELECT * FROM pokemon ORDER BY CASE :criteria " +
            "WHEN 'Number' THEN id " +
            "WHEN 'Name' THEN name " +
            "WHEN 'HP' THEN hp " +
            "WHEN 'Attack' THEN attack " +
            "WHEN 'Defense' THEN defense " +
            "ELSE id END ASC LIMIT :limit OFFSET :offset")
    suspend fun getSortedPageAsc(criteria: String, limit: Int, offset: Int): List<PokemonEntity>

    @Query("SELECT * FROM pokemon ORDER BY CASE :criteria " +
            "WHEN 'Number' THEN id " +
            "WHEN 'Name' THEN name " +
            "WHEN 'HP' THEN hp " +
            "WHEN 'Attack' THEN attack " +
            "WHEN 'Defense' THEN defense " +
            "ELSE id END DESC LIMIT :limit OFFSET :offset")
    suspend fun getSortedPageDesc(criteria: String, limit: Int, offset: Int): List<PokemonEntity>
}
