package com.example.pokemonapp.domain.usecases

import com.example.pokemonapp.domain.models.Pokemon
import com.example.pokemonapp.domain.repository.PokemonRepository
import kotlinx.coroutines.flow.Flow

class FilterPokemonsUseCase(private val repository: PokemonRepository) {
    operator fun invoke(type: String): Flow<List<Pokemon>> {
        return repository.filterPokemonsByType(type)
    }
}