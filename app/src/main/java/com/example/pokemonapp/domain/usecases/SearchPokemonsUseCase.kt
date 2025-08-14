package com.example.pokemonapp.domain.usecases

import com.example.pokemonapp.domain.models.Pokemon
import com.example.pokemonapp.domain.repository.PokemonRepository
import kotlinx.coroutines.flow.Flow

class SearchPokemonsUseCase(private val repository: PokemonRepository) {
    operator fun invoke(query: String): Flow<List<Pokemon>> {
        return repository.searchPokemons(query)
    }
}