package com.example.pokemonapp.presentation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.pokemonapp.domain.models.Pokemon
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight.Companion.Bold
import coil.compose.AsyncImage

@Composable
fun PokemonDetailScreen(
    pokemon: Pokemon,
    onBackClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Кнопка "назад"
        Row(
            modifier = Modifier
                .align(Alignment.Start)
                .padding(bottom = 16.dp)
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Изображение
        AsyncImage(
            model = pokemon.imageUrl,
            contentDescription = "${pokemon.name} image",
            modifier = Modifier
                .size(200.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Column(
            horizontalAlignment = Alignment.Start
        ) {
            // Название
            Text(
                text = pokemon.name.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ID
            Text(text = "ID: ${pokemon.id}", style = MaterialTheme.typography.bodyLarge)

            // Типы
            Text(
                text = "Types: ${pokemon.types.joinToString(", ")}",
                style = MaterialTheme.typography.bodyLarge
            )

            // Характеристики
            Text(text = "HP: ${pokemon.hp}", style = MaterialTheme.typography.bodyLarge)
            Text(text = "Attack: ${pokemon.attack}", style = MaterialTheme.typography.bodyLarge)
            Text(text = "Defense: ${pokemon.defense}", style = MaterialTheme.typography.bodyLarge)
        }
    }
}