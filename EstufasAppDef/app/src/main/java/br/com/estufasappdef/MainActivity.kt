package br.com.estufasappdef

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.KeyboardType
import br.com.estufasappdef.ui.theme.EstufasAppDefTheme
import com.google.firebase.FirebaseApp
import com.google.firebase.database.*
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        FirebaseApp.initializeApp(this)

        setContent {
            EstufasAppDefTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Scaffold { innerPadding ->
                        Column(modifier = Modifier.padding(innerPadding)) {
                            Greeting("Android")
                        }
                    }
                }
            }
        }
    }
}

// Classe auxiliar para dados do gráfico
data class ChartData(val title: String, val data: List<Float>)

@Composable
fun Greeting(name: String) {
    var chartData by remember { mutableStateOf(listOf<ChartData>()) }
    var desiredTemperature by remember { mutableStateOf("") }
    var confirmedTemperature by remember { mutableStateOf<Float?>(null) }
    val database = Firebase.database
    val sensorDataRef = database.getReference("sensor_data")
    val lampadaRef = database.getReference("lampada")
    val coolerRef = database.getReference("cooler")

    // Listener para mudanças nos dados do sensor
    LaunchedEffect(Unit) {
        sensorDataRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val newChartData = snapshot.children.mapNotNull { sensorSnapshot ->
                    val title = sensorSnapshot.key?.let { key ->
                        when (key) {
                            "temperature" -> "Temperatura"
                            "soil_moisture" -> "U0,midade do Solo"
                            "light_level" -> "Luminosidade"
                            "humidity" -> "Umidade Ambiente"
                            else -> null
                        }
                    } ?: return@mapNotNull null
                    val data = sensorSnapshot.children.mapNotNull { it.getValue(Float::class.java) }
                    ChartData(title, data)
                }
                chartData = newChartData

                // Lógica para controlar a lâmpada e o cooler
                val lastTemperature = newChartData.firstOrNull { it.title == "Temperatura" }?.data?.lastOrNull()

                if (lastTemperature != null && confirmedTemperature != null) {
                    // Lógica da lâmpada
                    if (lastTemperature < confirmedTemperature!!) {
                        lampadaRef.setValue(1)
                        Log.d("ToggleLampada", "Lâmpada ligada")
                    } else {
                        lampadaRef.setValue(0)
                        Log.d("ToggleLampada", "Lâmpada desligada")
                    }

                    // Lógica do cooler
                    if (lastTemperature > confirmedTemperature!!) {
                        coolerRef.setValue(1) // Ligar cooler se a temperatura estiver alta
                        Log.d("ToggleCooler", "Cooler ligado")
                    } else {
                        coolerRef.setValue(0) // Desligar cooler se a temperatura estiver normal
                        Log.d("ToggleCooler", "Cooler desligado")
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // Lidar com erros de leitura do Firebase (opcional)
            }
        })
    }

    Column {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(chartData) { data ->
                LineChart(data = data)
            }
        }

        // Campo de texto para temperatura desejada
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Temperatura Desejada", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = desiredTemperature,
                onValueChange = { newTemperature ->
                    desiredTemperature = newTemperature
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                label = { Text("Digite a temperatura") }
            )
            Button(onClick = {
                confirmedTemperature = desiredTemperature.toFloatOrNull()
            }) {
                Text("Confirmar")
            }
        }
    }
}
@Composable
fun LineChart(data: ChartData) {
    val pointSize = 8.dp
    val lineWidth = 2.dp
    val maxHeight = 150.dp
    val titlePaddingTop = 16.dp

    val yAxisScale = if (data.title.contains("Temperatura", ignoreCase = true)) {
        15f to 50f
    } else {
        0f to 100f
    }

    val yAxisUnit = if (data.title.contains("Temperatura", ignoreCase = true)) {
        "°C"
    } else {
        "%"
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .border(1.dp, Color.Gray)
            .padding(8.dp)
    ) {
        // Título centralizado e com espaçamento superior
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = titlePaddingTop),
            contentAlignment = Alignment.Center
        ) {
            Text(data.title, style = MaterialTheme.typography.titleMedium)
        }

        // Container with horizontal scrolling
        Box(
            modifier = Modifier
                .horizontalScroll(scrollState)
                .fillMaxWidth()
        ) {
            Canvas(
                modifier = Modifier
                    .width((data.data.size * 30).dp)
                    .height(maxHeight)
                    .padding(horizontal = 16.dp)
            ) {
                val (minValue, maxValue) = yAxisScale
                val range = maxValue - minValue

                // Desenhar o fundo quadriculado e o eixo Y
                val labels = (0..4).map { minValue + it * (range / 4) }

                labels.forEachIndexed { i, label ->
                    val y = size.height - (i * (size.height / 4))
                    drawLine(
                        Color.LightGray,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx()
                    )

                    // Rótulos do eixo Y
                    drawContext.canvas.nativeCanvas.drawText(
                        label.toInt().toString(),
                        0f,
                        y + 10f, // Ajustado para melhor posicionamento
                        android.graphics.Paint().apply {
                            textSize = 24f // Aumentado o tamanho do texto do eixo Y
                            color = android.graphics.Color.BLACK
                        }
                    )
                }

                // Unidade de medida do eixo Y no topo
                drawContext.canvas.nativeCanvas.drawText(
                    yAxisUnit,
                    0f,
                    0f,  // Ajustado para melhor posicionamento
                    android.graphics.Paint().apply {
                        textSize = 24f // Aumentado o tamanho do texto da unidade de medida do eixo Y
                        color = android.graphics.Color.BLACK
                    }
                )

                // Desenhar o gráfico de linha
                val path = Path()
                val pointSpacing = size.width / (data.data.size - 1)

                data.data.forEachIndexed { index, value ->
                    val y = size.height - ((value - minValue) / range) * size.height
                    val x = index * pointSpacing

                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)

                    drawCircle(
                        color = Color.Blue,
                        center = Offset(x, y),
                        radius = pointSize.toPx() / 2
                    )

                    // Rótulo da unidade de medida acompanhando o ponto
                    drawContext.canvas.nativeCanvas.drawText(
                        value.toString(),
                        x,
                        y - 10f, // Ajustado para posicionamento acima do ponto
                        android.graphics.Paint().apply {
                            textSize = 18f // Aumentado o tamanho dos valores que acompanham o gráfico
                            color = android.graphics.Color.BLACK
                        }
                    )
                }

                drawPath(
                    path = path,
                    color = Color.Blue,
                    style = Stroke(width = lineWidth.toPx())
                )
            }
        }

        // Eixo X representando o tempo em minutos (intervalo de 2 minutos)
        Box(
            modifier = Modifier
                .horizontalScroll(scrollState)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .width((data.data.size * 30).dp)
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.Bottom
            ) {
                repeat(data.data.size) { index ->
                    val timeInMinutes = index * 2
                    Box(
                        modifier = Modifier
                            .width(30.dp)
                            .height(20.dp)
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "$timeInMinutes",
                            fontSize = 10.sp, // Diminuído o tamanho dos números do eixo X
                            color = Color.Black
                        )
                    }
                }

                // Unidade de medida do eixo X com legenda
                Box(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .background(Color.White),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Text(
                        "min",
                        fontSize = 10.sp, // Diminuído o tamanho do texto da unidade de medida do eixo X
                        color = Color.Black
                    )
                }
            }
        }

        // Scroll to the end when data updates
        LaunchedEffect(data.data.size) {
            scrollState.scrollTo(scrollState.maxValue)
        }
    }
}


@Composable
fun GreetingPreview() {
    EstufasAppDefTheme {
        Greeting("Android")
    }
}
