package com.example.baism_cwiczenie_3

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.baism_cwiczenie_3.ui.theme.BAISM_Cwiczenie_3Theme
import kotlinx.coroutines.launch
import org.json.JSONException
import java.io.IOException

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BAISM_Cwiczenie_3Theme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    CharacterScreen(Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
private fun CharacterScreen(modifier: Modifier = Modifier) {
    var idInput by rememberSaveable { mutableStateOf("") }
    var characters by remember { mutableStateOf(emptyList<RickAndMortyCharacter>()) }
    var nextPage by remember { mutableStateOf<String?>(null) }
    var selectedCharacter by remember { mutableStateOf<RickAndMortyCharacter?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val id = idInput.toIntOrNull()?.takeIf { it > 0 }

    fun load(request: suspend () -> Unit) {
        focusManager.clearFocus()
        loading = true
        error = null
        scope.launch {
            try {
                request()
            } catch (_: CharacterNotFoundException) {
                error = "Nie znaleziono postaci o podanym ID."
            } catch (exception: IOException) {
                Log.w("RickAndMortyApi", "Błąd pobierania. Następna strona: $nextPage", exception)
                error = "Nie udało się pobrać danych. Spróbuj ponownie."
            } catch (_: JSONException) {
                error = "API zwróciło nieprawidłowe dane."
            } finally {
                loading = false
            }
        }
    }

    Column(
        modifier = modifier.fillMaxSize().imePadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Rick i Morty", style = MaterialTheme.typography.headlineSmall)

        Button(
            onClick = {
                load {
                    selectedCharacter = null
                    if (nextPage == null) {
                        characters = emptyList()
                        nextPage = RickAndMortyApi.CHARACTERS_URL
                    }
                    while (true) {
                        val url = nextPage ?: break
                        val page = RickAndMortyApi.getCharacterPage(url)
                        characters = characters + page.characters
                        nextPage = page.nextPage
                    }
                }
            },
            enabled = !loading
        ) {
            Text(if (nextPage != null && !loading) "Wznów pobieranie listy" else "Pobierz wszystkie postacie")
        }

        OutlinedTextField(
            value = idInput,
            onValueChange = { idInput = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("ID postaci") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            isError = idInput.isNotEmpty() && id == null,
            supportingText = if (idInput.isNotEmpty() && id == null) {
                { Text("Wpisz dodatnią liczbę całkowitą.") }
            } else null
        )

        Button(
            onClick = {
                id?.let { characterId ->
                    load { selectedCharacter = RickAndMortyApi.getCharacter(characterId) }
                }
            },
            enabled = !loading && id != null
        ) {
            Text("Pobierz postać po ID")
        }

        if (loading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        val character = selectedCharacter
        if (character == null && characters.isNotEmpty()) {
            Text("Pobrane postacie: ${characters.size}")
        }
        key(character?.id) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (character == null) {
                    items(characters, key = { it.id }) {
                        Text("${it.id}. ${it.name}")
                    }
                } else {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(character.name, style = MaterialTheme.typography.titleLarge)
                            Text("ID: ${character.id}")
                            Text("Status: ${character.status}")
                            Text("Gatunek: ${character.species}")
                            Text("Płeć: ${character.gender}")
                            Text("Pochodzenie: ${character.origin}")
                            Text("Lokalizacja: ${character.location}")
                        }
                    }
                }
            }
        }
    }
}
