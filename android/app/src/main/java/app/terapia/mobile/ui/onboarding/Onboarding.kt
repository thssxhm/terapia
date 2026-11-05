package app.terapia.mobile.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Sos
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.terapia.mobile.ui.components.Logo
import kotlinx.coroutines.launch

private data class Page(val icon: ImageVector?, val title: String, val text: String)

private val psyPages = listOf(
    Page(null, "Bem-vindo(a) ao Terapia", "Seu consultório no bolso: prontuário, agenda e comunicação com os pacientes em um só lugar."),
    Page(Icons.Default.PersonAdd, "Cadastre pacientes", "Na aba Pacientes toque em +. Informe nome, idade, telefone, e-mail e os horários fixos. O paciente recebe por e-mail um código de acesso e o link da Play Store — você também pode compartilhar o link pelo WhatsApp."),
    Page(Icons.Default.Description, "Prontuário completo", "Em cada paciente: anamnese, evolução, informações importantes (nomes, datas…), anotações pré e pós sessão e anexos em PDF ou DOC."),
    Page(Icons.Default.CalendarMonth, "Semana e agenda", "Veja todos os pacientes por dia da semana e a agenda geral de atendimentos. Toque em uma sessão para editar data, horário ou status."),
    Page(Icons.Default.NotificationsActive, "Avisos e lembretes", "Em Avisos chegam confirmações, cancelamentos e pedidos de ajuste. Em Lembretes, ligue o sininho dos pacientes cujos atendimentos você quer ser lembrado(a)."),
    Page(Icons.Default.Sos, "Emergências e mensagens", "Quando um paciente enviar uma emergência (amarela = pouco urgente, vermelha = muito urgente) aparece um pop-up. O chat fica no ícone de mensagem de cada paciente."),
    Page(Icons.Default.PictureAsPdf, "Exportar em PDF", "No menu do paciente, toque em \"Exportar PDF\" para baixar todas as informações e anotações."),
)

private val patientPages = listOf(
    Page(null, "Bem-vindo(a) ao Terapia", "Um espaço seguro para acompanhar sua terapia entre as sessões."),
    Page(Icons.Default.EventRepeat, "Suas sessões", "Cada sessão é um bloco com data e horário. Você pode dar um nome a ela, confirmar presença ou pedir alteração de data/cancelamento."),
    Page(Icons.Default.EditNote, "Anotações pré e pós sessão", "Escreva o que quer levar para a sessão e o que ficou depois dela. Essas anotações são só suas."),
    Page(Icons.Default.EmojiEmotions, "Humor do dia", "Todos os dias registre como você está com um dos 5 emojis e, se quiser, uma anotação. No fim da semana você pode enviar o gráfico ao seu psicólogo."),
    Page(Icons.Default.Sos, "Botão de emergência", "Amarelo = pouco urgente. Vermelho = muito urgente. Ele avisa seu psicólogo na hora. Em risco imediato ligue 192 (SAMU) ou 188 (CVV)."),
    Page(Icons.Default.Chat, "Mensagens", "Converse com seu psicólogo pela aba Mensagens. Vocês dois são avisados quando chega mensagem nova."),
)

@Composable
fun OnboardingScreen(psychologist: Boolean, onFinish: () -> Unit) {
    val pages = if (psychologist) psyPages else patientPages
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onFinish) { Text("Pular") }
        }
        HorizontalPager(pager, Modifier.weight(1f)) { i ->
            val page = pages[i]
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (page.icon == null) Logo(150.dp) else Box(
                    Modifier.size(120.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) { Icon(page.icon, null, Modifier.size(60.dp), tint = MaterialTheme.colorScheme.primary) }
                Spacer(Modifier.height(32.dp))
                Text(page.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Text(page.text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.Center) {
            repeat(pages.size) { i ->
                Box(
                    Modifier.padding(4.dp).size(if (i == pager.currentPage) 12.dp else 8.dp).clip(CircleShape)
                        .background(if (i == pager.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = .4f))
                )
            }
        }
        val last = pager.currentPage == pages.lastIndex
        Button(
            onClick = { if (last) onFinish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text(if (last) "Começar" else "Próximo") }
    }
}
