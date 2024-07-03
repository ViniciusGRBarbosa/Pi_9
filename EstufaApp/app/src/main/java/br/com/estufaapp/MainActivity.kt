package br.com.estufaapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.core.view.WindowCompat
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import br.com.estufaapp.ui.theme.EstufaAppTheme


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Habilitar edge-to-edge (modo tela cheia)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            EstufaAppTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TelaGraficos() // Chame a função que irá conter os gráficos e botões
                }
            }
        }
    }
}

// Função que irá conter seus gráficos e botões (adapte conforme necessário)
@Composable
fun TelaGraficos() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceAround // Distribui os elementos verticalmente
    ) {
        Grafico1()
       // Grafico2()
       // Grafico3()
       // Grafico4()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround // Distribui os botões horizontalmente
        ) {
            Botao(texto = "Lâmpada")
            Botao(texto = "Cooler")
        }
    }
}
@Composable
fun Botao(texto: String) {
    Button(onClick = { /* Ação do botão */ }) {
        Text(texto)
    }
}

@Composable
fun Grafico1() {
    AndroidView(
        factory = { context ->
            LineChart(context).apply {
                // Configuração do seu gráfico de linha aqui (dados, estilo, etc.)
            }
        },
        modifier = Modifier.fillMaxWidth().height(200.dp)
    )
}
// (Opcional) Preview da tela de gráficos para o Android Studio
@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    EstufaAppTheme {
        TelaGraficos()
    }
}
