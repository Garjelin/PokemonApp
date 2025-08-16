package com.example.pokemonapp.presentation.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.example.pokemonapp.presentation.viewmodels.PokemonListViewModel
import java.io.IOException
import coil.compose.AsyncImage
import com.example.pokemonapp.domain.models.Pokemon
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.example.pokemonapp.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PokemonListScreen(viewModel: PokemonListViewModel = viewModel(factory = PokemonListViewModelFactory(LocalContext.current))) {
    val pokemonList = viewModel.pokemonList.collectAsLazyPagingItems()
    val error = viewModel.error.collectAsState().value
    val isRefreshing = viewModel.isRefreshing.collectAsState().value
    val searchQuery = viewModel.searchQuery.collectAsState(initial = "")
    var showFilterSheet = remember { mutableStateOf(false) }

    val swipeRefreshState = rememberSwipeRefreshState(isRefreshing = isRefreshing)

    Scaffold(
        topBar = {
            SearchBar(
                query = searchQuery.value,
                onQueryChange = { viewModel.updateSearchQuery(it) },
                onSearch = { viewModel.updateSearchQuery(it) },
                active = false,
                onActiveChange = { },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                trailingIcon = { // Добавляем кнопку фильтра
                    IconButton(onClick = { showFilterSheet.value = true }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_filter),
                            contentDescription = "Filter",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { }
        }
    ){ paddingValues ->
        SwipeRefresh(
            state = swipeRefreshState,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.padding(paddingValues)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                when (val refreshState = pokemonList.loadState.refresh) {
                    is LoadState.Error -> {
                        ErrorView(
                            error = refreshState.error,
                            modifier = Modifier.fillMaxSize(),
                            onRetry = { viewModel.refresh() }
                        )
                    }
                    is LoadState.Loading -> {
                        LoadingIndicator(Modifier.fillMaxSize())
                    }
                    is LoadState.NotLoading -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            if (error != null) {
                                ErrorText(error)
                            }
                            // Основной контент с возможностью подгрузки
                            Box(modifier = Modifier.weight(1f)) {
                                PokemonGrid(pokemonList)
                                // Индикатор подгрузки внизу списка
                                if (pokemonList.loadState.append is LoadState.Loading) {
                                    LoadingIndicator(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Шторка отображается только при showFilterSheet.value == true
    if (showFilterSheet.value) {
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet.value = false }, // Сворачивание при клике вне
            sheetState = rememberModalBottomSheetState(
                skipPartiallyExpanded = false // Позволяет свайп вниз
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 2 / 3)
                    .padding(16.dp)
            ) {
                Text(
                    text = "Sort by",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                var selectedSortCriteria = remember { mutableStateOf("Number") }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf("Number", "Name", "HP", "Attack", "Defence").forEach { criterion ->
                        FilterTab(
                            text = criterion,
                            isSelected = selectedSortCriteria.value == criterion,
                            onClick = { selectedSortCriteria.value = criterion }
                        )
                    }
                }
                var selectedSortDirection = remember { mutableStateOf("ascending") }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf("ascending", "descending").forEach { direction ->
                        FilterTab(
                            text = direction,
                            isSelected = selectedSortDirection.value == direction,
                            onClick = { selectedSortDirection.value = direction }
                        )
                    }
                }
                Button(
                    onClick = {
                        viewModel.sortPokemons(selectedSortCriteria.value, selectedSortDirection.value)
                        showFilterSheet.value = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    Text("APPLY")
                }
            }
        }
    }
}

@Composable
private fun FilterTab(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(4.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 8.dp else 4.dp
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(8.dp),
            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun PokemonGrid(pokemonList: androidx.paging.compose.LazyPagingItems<Pokemon>) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            count = pokemonList.itemCount,
            key = pokemonList.itemKey { it.id },
            contentType = pokemonList.itemContentType { "pokemon" }
        ) { index ->
            val pokemon = pokemonList[index]
            pokemon?.let {
                PokemonItem(pokemon = it)
            }
        }
    }
}

@Composable
private fun PokemonItem(pokemon: Pokemon) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AsyncImage(
                model = pokemon.imageUrl,
                contentDescription = "${pokemon.name} image",
                modifier = Modifier
                    .size(100.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = pokemon.name.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
    }
}

@Composable
private fun LoadingIndicator(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier
                .size(64.dp) // Увеличим размер до 64.dp для видимости
                .padding(16.dp),
            strokeWidth = 10.dp // Утолщаем линию для лучшей видимости
        )
    }
}

@Composable
private fun ErrorView(error: Throwable, modifier: Modifier = Modifier, onRetry: () -> Unit) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (error is IOException) {
                    "No internet connection"
                } else {
                    "Error: ${error.message}"
                },
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.error
            )
            androidx.compose.material3.Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

@Composable
private fun ErrorText(error: String) {
    Text(
        text = "Error: $error",
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        color = MaterialTheme.colorScheme.error
    )
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