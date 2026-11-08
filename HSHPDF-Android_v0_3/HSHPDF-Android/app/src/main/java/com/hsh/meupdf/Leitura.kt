// Criador da fonte: Henrique de Souza Henrique
package com.hsh.meupdf

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Opções do modo leitura (guardadas no aparelho). Cada mudança já é salva. */
class OpcoesLeitura(private val cfg: Config) {
    var modo by mutableStateOf(cfg.modoLeitura)
    var orientacao by mutableStateOf(cfg.orientacao)
    var fundo by mutableStateOf(cfg.fundoLeitura)
    var zonas by mutableStateOf(cfg.zonasToque)
    var toqueDuplo by mutableStateOf(cfg.toqueDuploZoom)
    var numero by mutableStateOf(cfg.mostrarNumero)
    var recorte by mutableStateOf(cfg.mostrarNoRecorte)
    var manterTela by mutableStateOf(cfg.manterTela)
    var animar by mutableStateOf(cfg.animarPaginas)
    var cinza by mutableStateOf(cfg.cinza)
    var invertido by mutableStateOf(cfg.invertido)
    var margem by mutableIntStateOf(cfg.margemLateral)
    var brilho by mutableIntStateOf(cfg.brilho)

    fun salva() {
        cfg.modoLeitura = modo; cfg.orientacao = orientacao; cfg.fundoLeitura = fundo
        cfg.zonasToque = zonas; cfg.toqueDuploZoom = toqueDuplo; cfg.mostrarNumero = numero
        cfg.mostrarNoRecorte = recorte; cfg.manterTela = manterTela; cfg.animarPaginas = animar
        cfg.cinza = cinza; cfg.invertido = invertido; cfg.margemLateral = margem; cfg.brilho = brilho
    }
}

/** "Padrão": quadrinhos (imagens) em tira sem espaços; documentos com um respiro entre as páginas. */
fun modoEfetivo(op: OpcoesLeitura, ehHq: Boolean): String =
    if (op.modo != "padrao") op.modo else if (ehHq) "tira" else "lacunas"

fun espacoDoModo(modo: String): Dp = if (modo == "tira") 0.dp else 8.dp

fun Context.activity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

fun orientacaoAndroid(modo: String): Int = when (modo) {
    "auto" -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
    "retrato" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
    "paisagem" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
}

/** Filtro de cor aplicado às páginas: nível de cinza e/ou invertido. */
fun filtroDeCor(op: OpcoesLeitura): ColorFilter? {
    val g = 0.213f
    val h = 0.715f
    val b = 0.072f
    val m = when {
        op.cinza && op.invertido -> floatArrayOf(
            -g, -h, -b, 0f, 255f, -g, -h, -b, 0f, 255f, -g, -h, -b, 0f, 255f, 0f, 0f, 0f, 1f, 0f
        )
        op.cinza -> floatArrayOf(g, h, b, 0f, 0f, g, h, b, 0f, 0f, g, h, b, 0f, 0f, 0f, 0f, 0f, 1f, 0f)
        op.invertido -> floatArrayOf(
            -1f, 0f, 0f, 0f, 255f, 0f, -1f, 0f, 0f, 255f, 0f, 0f, -1f, 0f, 255f, 0f, 0f, 0f, 1f, 0f
        )
        else -> return null
    }
    return ColorFilter.colorMatrix(ColorMatrix(m))
}

/** Cor do fundo atrás das páginas: preto, cinza, branco ou "Auto" (cor das bordas da página atual). */
@Composable
fun corDoFundo(op: OpcoesLeitura, proj: Projeto, indice: Int): Color {
    var auto by remember { mutableStateOf(Color.Black) }
    if (op.fundo == "auto") {
        val pg = proj.estado.paginas.getOrNull(indice)
        LaunchedEffect(pg?.id, indice) {
            val f = pg?.fonte
            auto = if (pg == null) Color.Black else if (f == null) Color.White else try {
                Color(f.corBorda(pg.indice))
            } catch (e: Exception) {
                Color.Black
            }
        }
    }
    return when (op.fundo) {
        "cinza" -> Color(0xFF3A3A3A)
        "branco" -> Color.White
        "auto" -> auto
        else -> Color.Black
    }
}

/**
 * Modo leitura: só as páginas, em tela cheia, 100% ajustadas à largura (ou à tela, nos modos paginados).
 * Sair: arrastar para o lado (tira) ou Voltar. Tocar 3 vezes abre as opções (como no Mihon).
 */
@Composable
fun LeituraImersiva(
    proj: Projeto, op: OpcoesLeitura, lista: LazyListState, ehHq: Boolean,
    onSair: () -> Unit, onPaginas: () -> Unit, onOrientacao: (String) -> Unit
) {
    val paginas = proj.estado.paginas
    val escopo = rememberCoroutineScope()
    val dens = LocalDensity.current
    val ed = remember { EstadoEdicao() }
    val modo = modoEfetivo(op, ehHq)
    val paginado = modo == "esq_dir" || modo == "dir_esq" || modo == "vertical"
    val espaco = espacoDoModo(modo)
    val filtro = filtroDeCor(op)
    var zoom by remember { mutableFloatStateOf(1f) }
    var folha by remember { mutableStateOf(false) }
    var toques by remember { mutableIntStateOf(0) }
    var jobToque by remember { mutableStateOf<Job?>(null) }
    val pager = rememberPagerState(
        initialPage = lista.firstVisibleItemIndex.coerceIn(0, (paginas.size - 1).coerceAtLeast(0)),
        pageCount = { paginas.size }
    )
    val indiceAtual = if (paginado) pager.currentPage else lista.firstVisibleItemIndex
    val corFundo = corDoFundo(op, proj, indiceAtual)

    // ao trocar entre "tira" e "paginado" continua na mesma página
    LaunchedEffect(paginado) {
        if (paginado) pager.scrollToPage(lista.firstVisibleItemIndex.coerceIn(0, (paginas.size - 1).coerceAtLeast(0)))
        else lista.scrollToItem(pager.currentPage)
    }
    LaunchedEffect(pager.currentPage) { zoom = 1f }
    BackHandler(enabled = true) { if (folha) folha = false else onSair() }

    BoxWithConstraints(Modifier.fillMaxSize().background(corFundo)) {
        val larguraTela = constraints.maxWidth
        val alturaTela = constraints.maxHeight

        val acaoToque: (Offset) -> Unit = { pos ->
            if (op.zonas) {
                val terco = larguraTela / 3f
                val dir = when {
                    pos.x < terco -> -1
                    pos.x > 2 * terco -> 1
                    else -> 0
                }
                if (dir != 0) {
                    escopo.launch {
                        if (!paginado) {
                            lista.animateScrollBy(dir * alturaTela * 0.85f)
                        } else {
                            val passo = if (modo == "vertical") dir else if (modo == "dir_esq") -dir else dir
                            val destino = (pager.currentPage + passo).coerceIn(0, (paginas.size - 1).coerceAtLeast(0))
                            if (op.animar) pager.animateScrollToPage(destino) else pager.scrollToPage(destino)
                        }
                    }
                }
            }
        }
        val registraToque: (Offset) -> Unit = { pos ->
            toques++
            jobToque?.cancel()
            jobToque = escopo.launch {
                delay(300)
                val n = toques
                toques = 0
                if (n >= 3) {
                    folha = true
                } else if (n == 2) {
                    if (op.toqueDuplo) {
                        zoom = if (zoom > 1.4f) 1f else 2.5f
                    } else {
                        folha = true
                    }
                } else {
                    acaoToque(pos)
                }
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {  // pinça = zoom
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        do {
                            val ev = awaitPointerEvent(PointerEventPass.Initial)
                            if (ev.changes.size >= 2) {
                                val z = ev.calculateZoom()
                                if (z != 1f) {
                                    zoom = (zoom * z).coerceIn(1f, 5f)
                                    ev.changes.forEach { it.consume() }
                                }
                            }
                        } while (ev.changes.any { it.pressed })
                    }
                }
                .pointerInput(Unit) {
                    awaitEachGesture {  // toques (1, 2 ou 3 seguidos)
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val up = waitForUpOrCancellation() ?: return@awaitEachGesture
                        if ((up.position - down.position).getDistance() < viewConfiguration.touchSlop * 2) registraToque(down.position)
                    }
                }
                .pointerInput(paginado, zoom) {
                    if (!paginado) {  // arrastar para o lado sai do modo leitura (na tira)
                        var total = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { total = 0f },
                            onDragEnd = { if (abs(total) > 220f && zoom <= 1.05f) onSair() },
                            onDragCancel = { total = 0f },
                            onHorizontalDrag = { _, dx -> total += dx }
                        )
                    }
                }
        ) {
            if (!paginado) {
                val larguraBase = (larguraTela * (1f - 2f * op.margem / 100f)).toInt().coerceAtLeast(100)
                val larguraPx = (larguraBase * zoom).toInt()
                val folga = ((larguraTela - larguraPx) / 2).coerceAtLeast(0)
                val rolagemH = rememberScrollState()
                Box(Modifier.fillMaxSize().horizontalScroll(rolagemH)) {
                    LazyColumn(
                        state = lista,
                        modifier = Modifier.padding(start = with(dens) { folga.toDp() }).width(with(dens) { larguraPx.toDp() }).fillMaxHeight()
                    ) {
                        items(count = paginas.size, key = { paginas[it].id }) { i ->
                            PaginaView(
                                proj = proj, idx = i, pagina = paginas[i], larguraPx = larguraPx, ed = ed,
                                achados = emptyList(), atual = null, onTexto = { _, _, _ -> }, onImagemEm = { },
                                espaco = espaco, filtro = filtro
                            )
                        }
                    }
                }
            } else {
                val conteudo: @Composable (Int) -> Unit = { i ->
                    val p = paginas[i]
                    val ajuste = minOf(larguraTela.toFloat(), alturaTela / p.razao)
                    val larguraPx = (ajuste * zoom).toInt().coerceAtLeast(50)
                    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())) {
                        Box(
                            Modifier.defaultMinSize(minWidth = with(dens) { larguraTela.toDp() }, minHeight = with(dens) { alturaTela.toDp() }),
                            contentAlignment = Alignment.Center
                        ) {
                            PaginaView(
                                proj = proj, idx = i, pagina = p, larguraPx = larguraPx, ed = ed,
                                achados = emptyList(), atual = null, onTexto = { _, _, _ -> }, onImagemEm = { },
                                espaco = 0.dp, filtro = filtro
                            )
                        }
                    }
                }
                if (modo == "vertical") {
                    VerticalPager(state = pager, userScrollEnabled = zoom <= 1.05f, modifier = Modifier.fillMaxSize()) { i -> conteudo(i) }
                } else {
                    HorizontalPager(
                        state = pager, reverseLayout = modo == "dir_esq", userScrollEnabled = zoom <= 1.05f,
                        modifier = Modifier.fillMaxSize()
                    ) { i -> conteudo(i) }
                }
            }
        }

        if (op.numero && !folha) {
            Text(
                "${indiceAtual + 1} / ${paginas.size}", color = Color.White, fontSize = 12.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(6.dp).background(Color(0x88000000), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 3.dp)
            )
        }
        if (op.brilho < 100) {  // escurece por cima (não atrapalha os toques)
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = ((100 - op.brilho) / 100f).coerceIn(0f, 0.9f))))
        }
        if (folha) {
            FolhaOpcoes(
                op = op, onFechar = { folha = false }, onSair = { folha = false; onSair() },
                onPaginas = { folha = false; onSair(); onPaginas() }, onOrientacao = onOrientacao,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

// ====================================================== folha de opções (como no Mihon)
@Composable
private fun Chip(rotulo: String, marcado: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (marcado) Color(0xFF8A8A8A) else Color.Transparent,
        border = BorderStroke(1.dp, Color(0xFF6A6A72)),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(rotulo, color = Color.White, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp))
    }
}

@Composable
private fun Titulo(texto: String) {
    Text(texto, color = Color(0xFFE6E6EA), fontSize = 15.sp, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
}

@Composable
private fun LinhaCheck(rotulo: String, marcado: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!marcado) }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = marcado, onCheckedChange = { onChange(it) })
        Text(rotulo, color = Color.White, fontSize = 15.sp, modifier = Modifier.padding(start = 8.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FolhaOpcoes(
    op: OpcoesLeitura, onFechar: () -> Unit, onSair: () -> Unit, onPaginas: () -> Unit,
    onOrientacao: (String) -> Unit, modifier: Modifier = Modifier
) {
    var aba by remember { mutableIntStateOf(0) }
    Box(modifier.fillMaxSize().background(Color(0x66000000)).clickable { onFechar() }) {
        Surface(
            color = Color(0xFF151517), shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = 620.dp)
                .pointerInput(Unit) { detectTapGestures { } }
        ) {
            Column {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onSair) { Text("Sair do modo leitura", color = Color(0xFFFF8A80)) }
                    TextButton(onClick = onPaginas) { Text("Páginas", color = Color.White) }
                    TextButton(onClick = onFechar) { Text("Fechar", color = Color.White) }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    listOf("Modo de leitura", "Geral", "Filtro").forEachIndexed { i, rot ->
                        TextButton(onClick = { aba = i }, modifier = Modifier.weight(1f)) {
                            Text(rot, color = if (aba == i) Color.White else Color(0xFF9A9AA2), fontSize = 16.sp)
                        }
                    }
                }
                Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp)) {
                    when (aba) {
                        0 -> {
                            Titulo("Modo de leitura")
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                for ((rot, v) in listOf(
                                    "Padrão" to "padrao", "Tira longa (sem espaços)" to "tira", "Tira longa com lacunas" to "lacunas",
                                    "Paginado (esquerda para direita)" to "esq_dir", "Paginado (direita para esquerda)" to "dir_esq", "Paginado (vertical)" to "vertical"
                                )) Chip(rot, op.modo == v) { op.modo = v; op.salva() }
                            }
                            Titulo("Orientação")
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                for ((rot, v) in listOf("Padrão" to "padrao", "Automática" to "auto", "Retrato" to "retrato", "Paisagem" to "paisagem")) {
                                    Chip(rot, op.orientacao == v) { op.orientacao = v; op.salva(); onOrientacao(v) }
                                }
                            }
                            Titulo("Zonas de toque")
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Chip("Ativadas (esquerda/direita)", op.zonas) { op.zonas = true; op.salva() }
                                Chip("Desativadas", !op.zonas) { op.zonas = false; op.salva() }
                            }
                            Titulo("Margem lateral: ${op.margem}%")
                            Slider(value = op.margem.toFloat(), onValueChange = { op.margem = it.toInt() }, onValueChangeFinished = { op.salva() }, valueRange = 0f..25f)
                            LinhaCheck("Toque duplo para dar zoom", op.toqueDuplo) { op.toqueDuplo = it; op.salva() }
                        }
                        1 -> {
                            Titulo("Cor de fundo")
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                for ((rot, v) in listOf("Preto" to "preto", "Cinza" to "cinza", "Branco" to "branco", "Auto" to "auto")) {
                                    Chip(rot, op.fundo == v) { op.fundo = v; op.salva() }
                                }
                            }
                            Spacer(Modifier.padding(top = 10.dp))
                            LinhaCheck("Mostrar o número da página", op.numero) { op.numero = it; op.salva() }
                            LinhaCheck("Mostrar conteúdo na área de recorte da tela", op.recorte) { op.recorte = it; op.salva() }
                            LinhaCheck("Manter a tela ligada", op.manterTela) { op.manterTela = it; op.salva() }
                            LinhaCheck("Animar as transições de página", op.animar) { op.animar = it; op.salva() }
                        }
                        else -> {
                            Titulo("Brilho: ${op.brilho}%")
                            Slider(value = op.brilho.toFloat(), onValueChange = { op.brilho = it.toInt() }, onValueChangeFinished = { op.salva() }, valueRange = 10f..100f)
                            LinhaCheck("Nível de cinza", op.cinza) { op.cinza = it; op.salva() }
                            LinhaCheck("Invertido", op.invertido) { op.invertido = it; op.salva() }
                        }
                    }
                    Spacer(Modifier.padding(bottom = 24.dp))
                }
            }
        }
    }
}
