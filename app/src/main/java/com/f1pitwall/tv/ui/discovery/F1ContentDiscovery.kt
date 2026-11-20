package com.f1pitwall.tv.ui.discovery

import android.util.Log

/**
 * Motor de descubrimiento automatizado para eventos y canales de F1 en sitios web deportivos.
 *
 * Se comunica con la aplicación Android TV a través de F1Bridge (JavascriptInterface):
 *  - F1Bridge.onF1Events(json)
 *  - F1Bridge.onChannels(json)
 *  - F1Bridge.onLog(msg)
 */
object F1ContentDiscovery {

    private const val TAG = "F1Discovery"

    // ----------------------------------------------------------------
    // JS: Escanear el directorio principal esperando carga AJAX
    // ----------------------------------------------------------------
    fun buildDirectoryScannerJs(): String = """
        (function() {
            var maxAttempts = 35; // 35 * 300ms = 10.5s max
            var attempts = 0;
            
            function checkRows() {
                attempts++;
                var loader = document.getElementById('seLoader');
                var rows = document.querySelectorAll('#GelecekMaclar .se-row a.se-row-link');
                var isLoaded = rows.length > 0 || (loader && loader.style.display === 'none');
                
                if (window.F1Bridge) {
                    window.F1Bridge.onLog('Intento ' + attempts + ': rows=' + rows.length);
                }
                
                if (isLoaded || attempts >= maxAttempts) {
                    scanF1Events();
                } else {
                    setTimeout(checkRows, 300);
                }
            }
            
            function scanF1Events() {
                var rows = document.querySelectorAll('#GelecekMaclar .se-row');
                var events = [];
                var seenUrls = {};
                
                rows.forEach(function(row) {
                    var a = row.querySelector('a.se-row-link');
                    if (!a) return;
                    var href = a.href || '';
                    if (!href || href === '#' || href.indexOf('#sec-') !== -1 || href.startsWith('javascript:')) return;
                    if (seenUrls[href]) return;
                    
                    var league = (row.querySelector('.se-league-txt')?.textContent || '').trim();
                    var teams = (row.querySelector('.se-teams-txt')?.textContent || '').trim();
                    var fullText = (league + ' ' + teams).toLowerCase();
                    
                    // Comprobar palabras clave de F1
                    var isF1 = fullText.includes('formula 1') ||
                               fullText.includes('fórmula 1') ||
                               fullText.includes('formula-1') ||
                               fullText.includes('formula1') ||
                               fullText.includes('f1') ||
                               fullText.includes('grand prix') ||
                               fullText.includes('gran premio') ||
                               fullText.includes('las vegas') ||
                               fullText.includes('motorsport') ||
                               fullText.includes('racing');
                               
                    if (!isF1) return;
                    
                    var score = 50;
                    var session = 'Evento';
                    
                    if (fullText.includes('practice 1') || fullText.includes('práctica 1') || fullText.includes('fp1')) {
                        score = 100;
                        session = 'Practice 1';
                    } else if (fullText.includes('practice 2') || fullText.includes('práctica 2') || fullText.includes('fp2')) {
                        score = 90;
                        session = 'Practice 2';
                    } else if (fullText.includes('practice 3') || fullText.includes('práctica 3') || fullText.includes('fp3')) {
                        score = 80;
                        session = 'Practice 3';
                    } else if (fullText.includes('qualifying') || fullText.includes('clasificación') || fullText.includes('clasificacion')) {
                        score = 70;
                        session = 'Qualifying';
                    } else if (fullText.includes('sprint')) {
                        score = 65;
                        session = 'Sprint';
                    } else if (fullText.includes('race') || fullText.includes('carrera')) {
                        score = 60;
                        session = 'Race';
                    }
                    
                    if (fullText.includes('las vegas')) score += 10;
                    
                    seenUrls[href] = true;
                    events.push({
                        title: teams || league,
                        league: league.replace(/:$/, ''),
                        url: href,
                        score: score,
                        session: session
                    });
                });
                
                // Ordenar por relevancia descendente (Practice 1 primero)
                events.sort(function(a, b) { return b.score - a.score; });
                
                if (window.F1Bridge) {
                    window.F1Bridge.onF1Events(JSON.stringify(events));
                }
            }
            
            checkRows();
        })();
    """.trimIndent()

    // ----------------------------------------------------------------
    // JS: Escanear la página del evento buscando canales y limpiando la UI
    // ----------------------------------------------------------------
    fun buildEventPageScannerJs(): String = """
        (function() {
            var maxAttempts = 30; // 30 * 300ms = 9s max
            var attempts = 0;
            
            injectCleanStyle();
            
            function injectCleanStyle() {
                if (document.getElementById('tv-clean-style')) return;
                var style = document.createElement('style');
                style.id = 'tv-clean-style';
                style.textContent = `
                    header, footer, nav, .se-header, .se-footer, #streamTabs, .se-match-head, .se-back-btn, #matchBadge {
                        display: none !important;
                    }
                    body, html, .se-wrap, .se-container, .se-hero, .se-match-card, .se-player, .se-player-wrap {
                        margin: 0 !important; padding: 0 !important;
                        width: 100% !important; height: 100% !important;
                        max-width: 100% !important; border: none !important;
                        background: #000 !important; overflow: hidden !important;
                    }
                    .se-player-wrap {
                        padding-bottom: 0 !important;
                    }
                    /* Only size the actual event player iframe. Styling every iframe
                       also fullscreens ad/hidden iframes, which can cover live video. */
                    .se-player, .se-player-wrap {
                        position: relative !important;
                    }
                    .se-player-wrap iframe, .se-player iframe {
                        position: absolute !important;
                        top: 0 !important; left: 0 !important;
                        width: 100% !important; height: 100% !important;
                        border: none !important;
                        z-index: 2 !important;
                    }
                `;
                document.head.appendChild(style);
            }
            
            function checkChannels() {
                attempts++;
                injectCleanStyle();
                
                var tabButtons = document.querySelectorAll('#streamTabs .se-tab-btn');
                var titleEl = document.getElementById('matchTitle');
                var title = titleEl ? titleEl.textContent.trim() : document.title;
                
                if (window.F1Bridge) {
                    window.F1Bridge.onLog('EventPage intento ' + attempts + ': tabs=' + tabButtons.length);
                }
                
                if (tabButtons.length > 0 || attempts >= maxAttempts) {
                    var channels = [];
                    tabButtons.forEach(function(btn, i) {
                        var name = btn.textContent.trim();
                        channels.push({
                            name: name || ('Canal ' + (i + 1)),
                            index: i,
                            type: 'TAB',
                            url: ''
                        });
                    });
                    
                    if (channels.length === 0) {
                        var iframes = document.querySelectorAll('iframe');
                        iframes.forEach(function(f, i) {
                            if (f.src && f.src !== 'about:blank') {
                                channels.push({
                                    name: 'Señal ' + (i + 1),
                                    index: i,
                                    type: 'IFRAME',
                                    url: f.src
                                });
                            }
                        });
                    }
                    
                    if (window.F1Bridge) {
                        window.F1Bridge.onChannels(JSON.stringify({
                            title: title,
                            channels: channels
                        }));
                    }
                } else {
                    setTimeout(checkChannels, 300);
                }
            }
            
            checkChannels();
        })();
    """.trimIndent()

    // ----------------------------------------------------------------
    // JS: Cambiar al canal con el índice especificado
    // ----------------------------------------------------------------
    fun buildSwitchChannelJs(channelIndex: Int): String = """
        (function() {
            var tabs = document.querySelectorAll('#streamTabs .se-tab-btn');
            if (tabs.length > $channelIndex) {
                tabs[$channelIndex].click();
            }
            if (window.currentMatch && window.currentMatch.channels && window.currentMatch.channels.length > $channelIndex) {
                if (typeof updatePlayer === 'function') {
                    updatePlayer(window.currentMatch.channels[$channelIndex]);
                    return 'updated_player_directly';
                }
            }
            return tabs.length > $channelIndex ? 'switched_tab_clicked' : 'channel_not_found';
        })();
    """.trimIndent()

    // ----------------------------------------------------------------
    // Parser: Lista de eventos de F1 encontrados
    // ----------------------------------------------------------------
    fun parseF1Events(json: String): List<DiscoveredEvent> {
        return try {
            val unquoted = cleanJsonOutput(json)
            val list = mutableListOf<DiscoveredEvent>()
            val items = splitJsonArray(unquoted)
            for (item in items) {
                val title = extractString(item, "title") ?: ""
                val league = extractString(item, "league") ?: ""
                val rawUrl = extractString(item, "url") ?: ""
                val score = extractInt(item, "score") ?: 0
                val session = extractString(item, "session") ?: ""
                
                // Normalizar dominio a golygoals.com si viene como vivogoal.space u otro mirror,
                // asegurando cumplimiento estricto con la directiva CSP frame-ancestors de los proveedores de video.
                val url = rawUrl.replace("vivogoal.space", "golygoals.com")
                if (url.isNotBlank()) {
                    list.add(DiscoveredEvent(title, league, url, score, session))
                }
            }
            list
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing F1 events: ${e.message}")
            emptyList()
        }
    }

    // ----------------------------------------------------------------
    // Parser: Canales disponibles en la página del evento
    // ----------------------------------------------------------------
    fun parseEventPageChannels(json: String): Pair<String, List<DiscoveredChannel>> {
        return try {
            val unquoted = cleanJsonOutput(json)
            val title = extractString(unquoted, "title") ?: "Transmisión F1"
            val channelsBlock = extractSubObjectOrArray(unquoted, "channels") ?: "[]"
            val channelItems = splitJsonArray(channelsBlock)
            val channels = channelItems.mapIndexed { i, item ->
                val name = extractString(item, "name") ?: "Canal ${i + 1}"
                val index = extractInt(item, "index") ?: i
                val type = extractString(item, "type") ?: "TAB"
                val url = extractString(item, "url") ?: ""
                DiscoveredChannel(name, index, type, url)
            }
            Pair(title, channels)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing channels: ${e.message}")
            Pair("Transmisión F1", emptyList())
        }
    }

    // ---- Utilidades de parsing JSON simples y seguras ----

    private fun cleanJsonOutput(raw: String): String {
        var str = raw.trim()
        if (str.startsWith("\"") && str.endsWith("\"") && str.length >= 2) {
            str = str.substring(1, str.length - 1)
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\t", "\t")
        }
        return str
    }

    private fun splitJsonArray(json: String): List<String> {
        val result = mutableListOf<String>()
        var depth = 0
        var start = -1
        for (i in json.indices) {
            when (json[i]) {
                '{' -> {
                    if (depth == 0) start = i
                    depth++
                }
                '}' -> {
                    depth--
                    if (depth == 0 && start != -1) {
                        result.add(json.substring(start, i + 1))
                        start = -1
                    }
                }
            }
        }
        return result
    }

    private fun extractString(json: String, key: String): String? {
        val regex = Regex(""""$key"\s*:\s*"((?:[^"\\]|\\.)*)"""")
        return regex.find(json)?.groupValues?.getOrNull(1)
            ?.replace("\\\"", "\"")
            ?.replace("\\/", "/")
            ?.replace("\\\\", "\\")
    }

    private fun extractInt(json: String, key: String): Int? {
        val regex = Regex(""""$key"\s*:\s*(\d+)""")
        return regex.find(json)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun extractSubObjectOrArray(json: String, key: String): String? {
        val prefix = Regex(""""$key"\s*:\s*(\[|\{)""").find(json) ?: return null
        val startChar = prefix.groupValues[1][0]
        val endChar = if (startChar == '[') ']' else '}'
        val startIdx = prefix.range.last
        var depth = 0
        for (i in startIdx until json.length) {
            if (json[i] == startChar) depth++
            else if (json[i] == endChar) {
                depth--
                if (depth == 0) return json.substring(startIdx, i + 1)
            }
        }
        return null
    }
}
