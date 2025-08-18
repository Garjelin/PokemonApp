package com.example.pokemonapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.paging.compose.collectAsLazyPagingItems
import com.example.pokemonapp.presentation.ui.screens.PokemonDetailScreen
import com.example.pokemonapp.presentation.ui.screens.PokemonListScreen
import com.example.pokemonapp.presentation.ui.screens.PokemonListViewModelFactory
import com.example.pokemonapp.presentation.ui.theme.PokemonAppTheme
import com.example.pokemonapp.presentation.viewmodels.PokemonListViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PokemonAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val viewModel: PokemonListViewModel = viewModel(factory = PokemonListViewModelFactory(this))
                    NavHost(navController = navController, startDestination = "pokemon_list") {
                        composable("pokemon_list") {
                            PokemonListScreen(navController = navController)
                        }
                        composable(
                            "pokemon_detail/{pokemonId}/{pokemonName}",
                            arguments = listOf(
                                navArgument("pokemonId") { type = NavType.IntType },
                                navArgument("pokemonName") { type = NavType.StringType }
                            )
                        ) { backStackEntry ->
                            val pokemonId = backStackEntry.arguments?.getInt("pokemonId") ?: 0
                            val pokemonName = backStackEntry.arguments?.getString("pokemonName") ?: ""
                            // Получаем данные покемона из ViewModel
                            val pokemonList = viewModel.pokemonList.collectAsLazyPagingItems().itemSnapshotList
                            val pokemon = pokemonList.find { it?.id == pokemonId }
                            pokemon?.let {
                                PokemonDetailScreen(
                                    pokemon = it,
                                    onBackClick = { navController.popBackStack() }
                                )
                            } ?: Text("Pokemon not found", style = MaterialTheme.typography.headlineMedium)
                        }
                    }
                }
            }
        }
    }
}