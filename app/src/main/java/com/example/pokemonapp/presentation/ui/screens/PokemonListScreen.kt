package com.example.pokemonapp.presentation.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pokemonapp.presentation.viewmodels.PokemonListViewModel

@Composable
fun PokemonListScreen(viewModel: PokemonListViewModel = viewModel()) {
    val pokemonList = viewModel.pokemonList.collectAsState().value
    val error = viewModel.error.collectAsState().value

    Column(modifier = Modifier.fillMaxSize()) {
        if (error != null) {
            Text(text = "Error: $error")
        } else {
            LazyColumn {
                items(pokemonList.size) { index ->
                    val pokemon = pokemonList[index]
                    Text(text = "${pokemon.id}: ${pokemon.name} (Types: ${pokemon.types.joinToString()})")
                }
            }
        }
    }
}