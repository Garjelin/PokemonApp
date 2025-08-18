package com.example.pokemonapp.presentation.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight.Companion.Bold
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
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
fun PokemonListScreen(
    navController: NavHostController,
    viewModel: PokemonListViewModel = viewModel(factory = PokemonListViewModelFactory(LocalContext.current))
) {
    val pokemonList = viewModel.pokemonList.collectAsLazyPagingItems()
    val error = viewModel.error.collectAsState().value
    val isRefreshing = viewModel.isRefreshing.collectAsState().value
    val searchQuery = viewModel.searchQuery.collectAsState(initial = "")
    var showFilterSheet = remember { mutableStateOf(false) }

    val selectedSortCriteria = viewModel.sortCriteria.collectAsState()
    val selectedSortDirection = viewModel.sortDirection.collectAsState()

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
                trailingIcon = {
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
    ) { paddingValues ->
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
                            Box(modifier = Modifier.weight(1f)) {
                                PokemonGrid(pokemonList = pokemonList) { pokemon ->
                                    navController.navigate("pokemon_detail/${pokemon.id}/${pokemon.name}")
                                }
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

    if (showFilterSheet.value) {
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet.value = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 3 / 4)
                    .padding(16.dp)
            ) {
                Button(
                    onClick = {
                        viewModel.sortPokemons(viewModel.sortCriteria.value, viewModel.sortDirection.value)
                        showFilterSheet.value = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Text("APPLY")
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Text(
                        text = "Sort by",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = Bold),
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf("Number", "Name", "HP", "Attack", "Defense").forEach { criterion ->
                        FilterTab(
                            text = criterion,
                            isSelected = selectedSortCriteria.value == criterion,
                            onClick = { viewModel._sortCriteria.value = criterion }
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(text = "Ascending", modifier = Modifier.padding(start = 8.dp, end = 8.dp))
                    Switch(
                        checked = selectedSortDirection.value == "descending",
                        onCheckedChange = { isDescending ->
                            viewModel._sortDirection.value = if (isDescending) "descending" else "ascending"
                        },
                        modifier = Modifier.padding(start = 8.dp, end = 8.dp)
                    )
                    Text(text = "Descending", modifier = Modifier.padding(start = 8.dp, end = 8.dp))
                }

                val selectedTypes = viewModel.selectedTypes.collectAsState()
                TypeFilterChips(
                    selectedTypes = selectedTypes.value,
                    onTypeSelected = { types ->
                        viewModel.updateSelectedTypes(types)
                        showFilterSheet.value = false
                    }
                )
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
fun PokemonGrid(
    pokemonList: LazyPagingItems<Pokemon>,
    onPokemonClick: (Pokemon) -> Unit
) {
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
                PokemonItem(pokemon = it, onClick = { onPokemonClick(it) })
            }
        }
    }
}

@Composable
fun PokemonItem(
    pokemon: Pokemon,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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

@Composable
private fun TypeFilterChips(
    selectedTypes: Set<String>,
    onTypeSelected: (Set<String>) -> Unit
) {
    val allTypes = listOf(
        "Normal", "Fire", "Water", "Electric", "Grass", "Ice", "Fighting", "Poison",
        "Ground", "Flying", "Psychic", "Bug", "Rock", "Ghost", "Dragon", "Dark", "Steel", "Fairy"
    )

    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Text(
                text = "Filter By Type",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = Bold),
                modifier = Modifier.align(Alignment.Center)
            )
        }
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 8.dp)
        ) {
            items(allTypes.size) { index ->
                val type = allTypes[index]
                val isSelected = selectedTypes.contains(type)
                FilterTab(
                    text = type,
                    isSelected = isSelected,
                    onClick = {
                        val newTypes = if (isSelected) {
                            selectedTypes - type
                        } else {
                            selectedTypes + type
                        }
                        onTypeSelected(newTypes)
                    }
                )
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