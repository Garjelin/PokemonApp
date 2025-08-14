package com.example.pokemonapp.presentation.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.example.pokemonapp.presentation.viewmodels.PokemonListViewModel

@Composable
fun PokemonListScreen(viewModel: PokemonListViewModel = viewModel(factory = PokemonListViewModelFactory(LocalContext.current))) {
    val pokemonList = viewModel.pokemonList.collectAsLazyPagingItems()
    val error = viewModel.error.collectAsState().value

    Column(modifier = Modifier.fillMaxSize()) {
        if (error != null) {
            Text(text = "Error: $error")
        }
        LazyColumn {
            items(
                count = pokemonList.itemCount,
                key = pokemonList.itemKey { it.id },
                contentType = pokemonList.itemContentType { "pokemon" }
            ) { index ->
                val pokemon = pokemonList[index]
                pokemon?.let {
                    Text(text = "${it.id}: ${it.name} (Types: ${it.types.joinToString()})")
                }
            }
        }
    }
}

class PokemonListViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PokemonListViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PokemonListViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
