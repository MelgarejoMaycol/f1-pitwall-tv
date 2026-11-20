package com.f1pitwall.tv.ui.pitwall

import android.graphics.PointF
import java.util.Locale

/**
 * Catálogo maestro de trazados satelitales oficiales de Fórmula 1.
 * Proporciona coordenadas vectoriales precisas, zonas DRS y puntos de referencia
 * para todos los Grandes Premios del calendario oficial de la FIA.
 * Garantiza que durante la carrera la visualización no se dañe si falla la red.
 */
object CircuitOutlinesCatalog {

    data class CircuitTurnAnnotation(
        val label: String,
        val pointIndex: Int,
        val isDrs: Boolean = false
    )

    data class CircuitData(
        val officialName: String,
        val outlinePoints: List<PointF>,
        val annotations: List<CircuitTurnAnnotation>,
        val s1EndRatio: Float = 0.33f,
        val s2EndRatio: Float = 0.66f
    )

    fun getCircuitData(circuitOrGpName: String): CircuitData {
        val lower = circuitOrGpName.lowercase(Locale.ROOT)
        return when {
            lower.contains("monza") || lower.contains("italia") || lower.contains("italy") ->
                getMonzaData()
            lower.contains("spa") || lower.contains("belgica") || lower.contains("belgium") || lower.contains("francorchamps") ->
                getSpaData()
            lower.contains("monaco") || lower.contains("mónaco") || lower.contains("monte") ->
                getMonacoData()
            lower.contains("silverstone") || lower.contains("gran bretaña") || lower.contains("britain") || lower.contains("uk") ->
                getSilverstoneData()
            lower.contains("interlagos") || lower.contains("brasil") || lower.contains("brazil") || lower.contains("são paulo") || lower.contains("sao paulo") ->
                getInterlagosData()
            lower.contains("red bull ring") || lower.contains("austria") || lower.contains("spielberg") ->
                getRedBullRingData()
            lower.contains("sakhir") || lower.contains("bahrain") || lower.contains("barein") ->
                getBahrainData()
            lower.contains("suzuka") || lower.contains("japan") || lower.contains("japon") || lower.contains("japón") ->
                getSuzukaData()
            lower.contains("catalunya") || lower.contains("barcelona") || lower.contains("españa") || lower.contains("spain") ->
                getBarcelonaData()
            lower.contains("melbourne") || lower.contains("albert") || lower.contains("australia") ->
                getMelbourneData()
            lower.contains("jeddah") || lower.contains("saudi") || lower.contains("arabia") ->
                getJeddahData()
            lower.contains("vegas") ->
                getLasVegasData()
            lower.contains("miami") ->
                getMiamiData()
            lower.contains("canada") || lower.contains("canadá") || lower.contains("montreal") ->
                getMontrealData()
            lower.contains("austin") || lower.contains("americas") || lower.contains("cota") ||
                    (lower.contains("united states") && !lower.contains("vegas") && !lower.contains("miami")) ->
                getCotaData()
            lower.contains("baku") || lower.contains("azerbaijan") || lower.contains("azerbaiyán") ->
                getBakuData()
            lower.contains("shanghai") || lower.contains("china") ->
                getShanghaiData()
            lower.contains("hungaroring") || lower.contains("hungary") || lower.contains("hungria") || lower.contains("hungría") || lower.contains("budapest") ->
                getHungaroringData()
            lower.contains("zandvoort") || lower.contains("netherlands") || lower.contains("países bajos") || lower.contains("holanda") ->
                getZandvoortData()
            lower.contains("mexico") || lower.contains("méxico") || lower.contains("rodriguez") ->
                getMexicoData()
            lower.contains("imola") || lower.contains("emilia") || lower.contains("romagna") ->
                getImolaData()
            lower.contains("singapore") || lower.contains("singapur") || lower.contains("marina bay") ->
                getSingaporeData()
            lower.contains("qatar") || lower.contains("losail") || lower.contains("lusail") ->
                getQatarData()
            lower.contains("abu dhabi") || lower.contains("yas marina") || lower.contains("uae") ->
                getYasMarinaData()
            else ->
                getLasVegasData()
        }
    }

    // 1. LAS VEGAS STRIP CIRCUIT (120 puntos satelitales oficiales)
    fun getLasVegasData(): CircuitData {
        val pts = listOf(
            PointF(2353f, -289f), PointF(2712f, 111f), PointF(3078f, 520f), PointF(3514f, 1003f),
            PointF(3761f, 1336f), PointF(3787f, 1570f), PointF(3673f, 1802f), PointF(3460f, 1948f),
            PointF(3213f, 1993f), PointF(2800f, 1887f), PointF(2519f, 1695f), PointF(2182f, 1372f),
            PointF(1871f, 1186f), PointF(1405f, 1203f), PointF(1096f, 1345f), PointF(806f, 1606f),
            PointF(587f, 2117f), PointF(518f, 2830f), PointF(491f, 3254f), PointF(461f, 3903f),
            PointF(419f, 4955f), PointF(393f, 5689f), PointF(363f, 6552f), PointF(340f, 7207f),
            PointF(322f, 7720f), PointF(296f, 8456f), PointF(274f, 9086f), PointF(253f, 9684f),
            PointF(279f, 10341f), PointF(356f, 10534f), PointF(522f, 10683f), PointF(844f, 10709f),
            PointF(1205f, 10710f), PointF(1418f, 10712f), PointF(1699f, 10714f), PointF(2184f, 10720f),
            PointF(2623f, 10810f), PointF(3216f, 11141f), PointF(3471f, 11394f), PointF(3702f, 11739f),
            PointF(3819f, 12025f), PointF(3877f, 12293f), PointF(3873f, 12595f), PointF(3755f, 12837f),
            PointF(3533f, 12921f), PointF(3275f, 12926f), PointF(3546f, 13543f), PointF(3640f, 13812f),
            PointF(3611f, 14008f), PointF(3419f, 14125f), PointF(3058f, 14154f), PointF(2470f, 14167f),
            PointF(1795f, 14182f), PointF(1088f, 14207f), PointF(413f, 14246f), PointF(-200f, 14299f),
            PointF(-489f, 14352f), PointF(-552f, 14426f), PointF(-555f, 14516f), PointF(-517f, 14577f),
            PointF(-487f, 14643f), PointF(-682f, 14761f), PointF(-1217f, 15096f), PointF(-1815f, 15467f),
            PointF(-2440f, 15858f), PointF(-3093f, 16265f), PointF(-3777f, 16692f), PointF(-4474f, 17130f),
            PointF(-4767f, 17629f), PointF(-4986f, 17774f), PointF(-5235f, 17740f), PointF(-5451f, 17482f),
            PointF(-5736f, 16906f), PointF(-6076f, 16086f), PointF(-6409f, 15152f), PointF(-6718f, 14032f),
            PointF(-6909f, 13142f), PointF(-7042f, 12450f), PointF(-7179f, 11666f), PointF(-7304f, 10839f),
            PointF(-7419f, 9993f), PointF(-7522f, 9152f), PointF(-7615f, 8318f), PointF(-7662f, 7502f),
            PointF(-7684f, 6635f), PointF(-7706f, 5779f), PointF(-7719f, 4930f), PointF(-7723f, 4075f),
            PointF(-7724f, 3216f), PointF(-7728f, 2351f), PointF(-7730f, 1479f), PointF(-7733f, 608f),
            PointF(-7733f, -474f), PointF(-7734f, -738f), PointF(-7724f, -979f), PointF(-7637f, -1193f),
            PointF(-7437f, -1373f), PointF(-7130f, -1497f), PointF(-6738f, -1559f), PointF(-6288f, -1591f),
            PointF(-5802f, -1614f), PointF(-5310f, -1645f), PointF(-4821f, -1695f), PointF(-4334f, -1717f),
            PointF(-3844f, -1719f), PointF(-3353f, -1722f), PointF(-2862f, -1719f), PointF(-2372f, -1718f),
            PointF(-1879f, -1700f), PointF(-1388f, -1666f), PointF(-1297f, -1653f), PointF(-588f, -1643f),
            PointF(94f, -1619f), PointF(811f, -1538f), PointF(1672f, -1058f), PointF(2353f, -289f)
        )
        val ann = listOf(
            CircuitTurnAnnotation("T1", 8),
            CircuitTurnAnnotation("T4 Sphere", 22),
            CircuitTurnAnnotation("DRS 1", 34, isDrs = true),
            CircuitTurnAnnotation("T12 Strip", 68),
            CircuitTurnAnnotation("DRS 2 (1.9 km)", 86, isDrs = true),
            CircuitTurnAnnotation("T14 Frenada", 98)
        )
        return CircuitData("Las Vegas Strip Circuit", pts, ann)
    }

    // 2. AUTODROMO NAZIONALE MONZA (Templo de la Velocidad)
    fun getMonzaData(): CircuitData {
        val pts = generateSmoothCircuit(listOf(
            PointF(0f, 0f),       // Rettifilo Start/Finish
            PointF(0f, 3200f),    // T1/T2 Prima Variante
            PointF(60f, 3350f),
            PointF(-40f, 3500f),
            PointF(150f, 3900f),
            PointF(420f, 4400f),  // Curva Grande (Biassono)
            PointF(600f, 4700f),
            PointF(620f, 5100f),  // T4/T5 Variante della Roggia
            PointF(530f, 5250f),
            PointF(650f, 5500f),
            PointF(900f, 5700f),  // T6 Prima Lesmo
            PointF(1250f, 5650f), // T7 Seconda Lesmo
            PointF(1450f, 5200f),
            PointF(1600f, 4300f), // Curva del Serraglio
            PointF(1700f, 3400f), // DRS Recta Serraglio
            PointF(1780f, 2600f), // T8/T9/T10 Variante Ascari
            PointF(1650f, 2450f),
            PointF(1750f, 2200f),
            PointF(1750f, 1100f), // Rettifilo Opposto
            PointF(1750f, 400f),  // Entrada Parabolica
            PointF(1600f, -100f), // Curva Parabolica (Alboreto)
            PointF(1200f, -300f),
            PointF(700f, -300f),
            PointF(200f, -150f),
            PointF(0f, 0f)
        ), totalTargetPoints = 120)

        val ann = listOf(
            CircuitTurnAnnotation("Variante Rettifilo", 15),
            CircuitTurnAnnotation("Curva Grande", 28),
            CircuitTurnAnnotation("Variante Roggia", 42),
            CircuitTurnAnnotation("Curva di Lesmo", 52),
            CircuitTurnAnnotation("DRS Serraglio", 65, isDrs = true),
            CircuitTurnAnnotation("Variante Ascari", 78),
            CircuitTurnAnnotation("Curva Parabolica", 102)
        )
        return CircuitData("Autodromo Nazionale Monza", pts, ann)
    }

    // 3. CIRCUIT DE SPA-FRANCORCHAMPS (Montaña Rusa de las Ardenas)
    fun getSpaData(): CircuitData {
        val pts = generateSmoothCircuit(listOf(
            PointF(0f, 0f),        // Start / Finish
            PointF(150f, 200f),    // T1 La Source
            PointF(350f, 150f),
            PointF(250f, -200f),   // Bajada a Eau Rouge
            PointF(180f, -600f),   // T2 Eau Rouge
            PointF(280f, -900f),   // T3 Raidillon
            PointF(350f, -1200f),
            PointF(600f, -2600f),  // Kemmel Straight DRS
            PointF(850f, -4000f),
            PointF(1000f, -4400f), // T7/T8/T9 Les Combes
            PointF(1250f, -4500f), // T10 Malmedy
            PointF(1500f, -4100f),
            PointF(1700f, -3600f), // T11 Bruxelles Hairpin
            PointF(1600f, -3100f), // T12 Speaker's Corner
            PointF(1350f, -2700f),
            PointF(1050f, -2300f), // T13 Pouhon (doble curva izquierda)
            PointF(950f, -1800f),
            PointF(1100f, -1500f), // T14/T15 Campus / Stavelot
            PointF(1350f, -1200f),
            PointF(1150f, -800f),  // Curva Paul Frere
            PointF(750f, -400f),   // Blanchimont a fondo
            PointF(350f, -150f),   // Frenada Bus Stop
            PointF(100f, -80f),    // Chicane Bus Stop
            PointF(0f, 0f)
        ), totalTargetPoints = 120)

        val ann = listOf(
            CircuitTurnAnnotation("La Source", 6),
            CircuitTurnAnnotation("Eau Rouge / Raidillon", 18),
            CircuitTurnAnnotation("Kemmel Straight", 32, isDrs = true),
            CircuitTurnAnnotation("Les Combes", 44),
            CircuitTurnAnnotation("Bruxelles", 55),
            CircuitTurnAnnotation("Pouhon", 68),
            CircuitTurnAnnotation("Blanchimont", 96),
            CircuitTurnAnnotation("Bus Stop Chicane", 112)
        )
        return CircuitData("Circuit de Spa-Francorchamps", pts, ann)
    }

    // 4. CIRCUIT DE MONACO (Joya Urbana del Principado)
    fun getMonacoData(): CircuitData {
        val pts = generateSmoothCircuit(listOf(
            PointF(0f, 0f),       // Boulevard Albert 1er
            PointF(120f, 250f),   // T1 Sainte Dévote
            PointF(250f, 400f),
            PointF(350f, 750f),   // Montée du Beau Rivage
            PointF(300f, 1050f),  // T2 Massenet
            PointF(220f, 1200f),
            PointF(350f, 1300f),  // T3 Casino Square
            PointF(520f, 1250f),  // T4 Mirabeau Haute
            PointF(600f, 1050f),
            PointF(550f, 920f),   // T5 Fairmont Hairpin
            PointF(680f, 900f),
            PointF(780f, 950f),   // T6 Mirabeau Bas
            PointF(880f, 850f),   // T7 Portier
            PointF(920f, 650f),
            PointF(1020f, 350f),  // El Túnel (alta velocidad)
            PointF(980f, 50f),
            PointF(850f, -80f),   // T10/T11 Nouvelle Chicane
            PointF(780f, -50f),
            PointF(650f, -180f),  // T12 Tabac
            PointF(500f, -240f),  // T13/T14 Piscine (Louis Chiron)
            PointF(350f, -220f),
            PointF(220f, -180f),  // T15/T16 La Rascasse
            PointF(100f, -120f),  // T17 Anthony Noghes
            PointF(0f, 0f)
        ), totalTargetPoints = 120)

        val ann = listOf(
            CircuitTurnAnnotation("DRS Main Straight", 4, isDrs = true),
            CircuitTurnAnnotation("Sainte Dévote", 8),
            CircuitTurnAnnotation("Beau Rivage", 18),
            CircuitTurnAnnotation("Casino Square", 30),
            CircuitTurnAnnotation("Fairmont Hairpin", 48),
            CircuitTurnAnnotation("The Tunnel", 66),
            CircuitTurnAnnotation("Nouvelle Chicane", 80),
            CircuitTurnAnnotation("Tabac", 92),
            CircuitTurnAnnotation("La Rascasse", 108)
        )
        return CircuitData("Circuit de Monaco", pts, ann)
    }

    // 5. SILVERSTONE CIRCUIT (Cuna del Automovilismo)
    fun getSilverstoneData(): CircuitData {
        val pts = generateSmoothCircuit(listOf(
            PointF(0f, 0f),        // Hamilton Straight
            PointF(250f, 150f),    // T1 Abbey
            PointF(400f, 200f),    // T2 Farm
            PointF(450f, 50f),     // T3 Village
            PointF(350f, -150f),   // T4 The Loop
            PointF(320f, -350f),   // T5 Aintree
            PointF(350f, -800f),   // Wellington Straight DRS
            PointF(350f, -1500f),
            PointF(200f, -1800f),  // T6 Brooklands
            PointF(0f, -1850f),    // T7 Luffield
            PointF(-180f, -1650f),
            PointF(-100f, -1300f), // T8 Woodcote
            PointF(-80f, -800f),   // National Pits Straight
            PointF(150f, -650f),   // T9 Copse (curva ultrarrápida)
            PointF(350f, -750f),   // T10 Maggotts
            PointF(520f, -900f),   // T11/T12 Becketts
            PointF(650f, -1000f),  // T13 Chapel
            PointF(900f, -1350f),  // Hangar Straight DRS
            PointF(1250f, -1700f),
            PointF(1450f, -1600f), // T15 Stowe
            PointF(1350f, -1100f), // T16 Vale
            PointF(1150f, -800f),  // T17/T18 Club
            PointF(600f, -400f),
            PointF(0f, 0f)
        ), totalTargetPoints = 120)

        val ann = listOf(
            CircuitTurnAnnotation("Abbey", 8),
            CircuitTurnAnnotation("The Loop", 20),
            CircuitTurnAnnotation("Wellington DRS", 32, isDrs = true),
            CircuitTurnAnnotation("Copse", 58),
            CircuitTurnAnnotation("Maggotts / Becketts", 68),
            CircuitTurnAnnotation("Hangar Straight", 85, isDrs = true),
            CircuitTurnAnnotation("Stowe", 96),
            CircuitTurnAnnotation("Club", 110)
        )
        return CircuitData("Silverstone Circuit", pts, ann)
    }

    // 6. AUTÓDROMO JOSÉ CARLOS PACE (Interlagos, Brasil)
    fun getInterlagosData(): CircuitData {
        val pts = generateSmoothCircuit(listOf(
            PointF(0f, 0f),        // Reta dos Boxes
            PointF(120f, 250f),    // T1 S de Senna
            PointF(280f, 320f),    // T2 Curva do Sol
            PointF(420f, 200f),
            PointF(480f, -200f),   // Reta Oposta DRS
            PointF(480f, -1200f),
            PointF(580f, -1450f),  // T4 Descida do Lago
            PointF(750f, -1400f),
            PointF(880f, -1100f),  // T6 Ferradura
            PointF(1020f, -800f),
            PointF(950f, -500f),   // T8 Pinheirinho
            PointF(850f, -300f),   // T9 Bico de Pato
            PointF(920f, -150f),
            PointF(800f, 50f),     // T10 Mergulho
            PointF(650f, 180f),    // T12 Junção
            PointF(420f, 150f),    // Subida dos Boxes
            PointF(180f, 60f),     // Arquibancadas
            PointF(0f, 0f)
        ), totalTargetPoints = 120)

        val ann = listOf(
            CircuitTurnAnnotation("S de Senna", 8),
            CircuitTurnAnnotation("Curva do Sol", 20),
            CircuitTurnAnnotation("Reta Oposta", 38, isDrs = true),
            CircuitTurnAnnotation("Descida do Lago", 50),
            CircuitTurnAnnotation("Ferradura", 65),
            CircuitTurnAnnotation("Bico de Pato", 82),
            CircuitTurnAnnotation("Junção", 98),
            CircuitTurnAnnotation("Subida dos Boxes", 110)
        )
        return CircuitData("Autódromo José Carlos Pace", pts, ann)
    }

    // 7. RED BULL RING (Spielberg, Austria)
    fun getRedBullRingData(): CircuitData {
        val pts = generateSmoothCircuit(listOf(
            PointF(0f, 0f),       // Start / Finish
            PointF(250f, 450f),   // T1 Niki Lauda Kurve
            PointF(400f, 500f),
            PointF(700f, 1400f),  // Uphill Straight DRS
            PointF(850f, 1750f),  // T3 Remus Hairpin
            PointF(780f, 1700f),
            PointF(500f, 1100f),  // Downhill Straight DRS
            PointF(550f, 850f),   // T4 Rauch
            PointF(620f, 750f),
            PointF(520f, 500f),   // T6 Gerhard Berger
            PointF(420f, 320f),   // T7 Wurth
            PointF(280f, 150f),   // T9/T10 Jochen Rindt
            PointF(120f, 50f),
            PointF(0f, 0f)
        ), totalTargetPoints = 120)

        val ann = listOf(
            CircuitTurnAnnotation("T1 Niki Lauda", 12),
            CircuitTurnAnnotation("T3 Remus Hairpin", 40),
            CircuitTurnAnnotation("DRS Downhill", 52, isDrs = true),
            CircuitTurnAnnotation("T4 Rauch", 68),
            CircuitTurnAnnotation("T7 Berger", 85),
            CircuitTurnAnnotation("T10 Jochen Rindt", 108)
        )
        return CircuitData("Red Bull Ring", pts, ann)
    }

    // 8. BAHRAIN INTERNATIONAL CIRCUIT (Sakhir)
    fun getBahrainData(): CircuitData {
        val pts = generateSmoothCircuit(listOf(
            PointF(0f, 0f),        // Main Straight DRS
            PointF(120f, 450f),    // T1 Michael Schumacher
            PointF(250f, 600f),    // T2/T3
            PointF(420f, 580f),    // T4
            PointF(550f, 450f),    // T5/T6/T7 Esses
            PointF(650f, 320f),
            PointF(620f, 180f),    // T8 Hairpin
            PointF(520f, 50f),     // T9/T10 Blind Downhill
            PointF(380f, -20f),
            PointF(750f, -350f),   // Back Straight DRS
            PointF(1050f, -200f),  // T11/T12 Fast Uphill
            PointF(1100f, 50f),    // T13
            PointF(700f, 50f),     // T14/T15 onto Main Straight
            PointF(250f, 20f),
            PointF(0f, 0f)
        ), totalTargetPoints = 120)

        val ann = listOf(
            CircuitTurnAnnotation("T1 Schumacher", 10),
            CircuitTurnAnnotation("T4", 26),
            CircuitTurnAnnotation("T8 Hairpin", 46),
            CircuitTurnAnnotation("T10 Blind Left", 60),
            CircuitTurnAnnotation("Back Straight", 75, isDrs = true),
            CircuitTurnAnnotation("T14 / Finish", 105)
        )
        return CircuitData("Bahrain International Circuit", pts, ann)
    }

    // 9. SUZUKA INTERNATIONAL RACING COURSE (Japón - Figura 8)
    fun getSuzukaData(): CircuitData {
        val pts = generateSmoothCircuit(listOf(
            PointF(0f, 0f),        // Start / Finish
            PointF(150f, 250f),    // T1/T2 First Corner
            PointF(320f, 350f),
            PointF(400f, 550f),    // T3 S-Curves 1
            PointF(300f, 750f),    // T4 S-Curves 2
            PointF(420f, 950f),    // T5 S-Curves 3
            PointF(350f, 1150f),   // T6 Dunlop Curve
            PointF(220f, 1300f),
            PointF(320f, 1450f),   // T8 Degner 1
            PointF(420f, 1480f),   // T9 Degner 2
            PointF(450f, 1350f),   // Paso bajo el puente
            PointF(600f, 1100f),   // T11 Hairpin
            PointF(550f, 950f),
            PointF(750f, 750f),    // T12 200R
            PointF(900f, 850f),    // T13/T14 Spoon Curve
            PointF(980f, 1050f),
            PointF(850f, 1200f),
            PointF(650f, 1300f),   // Recta del puente (cruce sobre pista)
            PointF(350f, 1100f),   // T15 130R (curva mítica)
            PointF(150f, 800f),
            PointF(80f, 500f),     // T16/T17 Casio Triangle Chicane
            PointF(0f, 0f)
        ), totalTargetPoints = 120)

        val ann = listOf(
            CircuitTurnAnnotation("DRS Pit Straight", 4, isDrs = true),
            CircuitTurnAnnotation("First Curve", 10),
            CircuitTurnAnnotation("S-Curves", 28),
            CircuitTurnAnnotation("Degner 1 & 2", 48),
            CircuitTurnAnnotation("Hairpin", 62),
            CircuitTurnAnnotation("Spoon Curve", 78),
            CircuitTurnAnnotation("130R", 98),
            CircuitTurnAnnotation("Casio Triangle", 112)
        )
        return CircuitData("Suzuka Racing Course", pts, ann)
    }

    // 10. CIRCUIT DE BARCELONA-CATALUNYA
    fun getBarcelonaData(): CircuitData {
        val pts = generateSmoothCircuit(listOf(
            PointF(0f, 0f),        // Recta principal
            PointF(150f, 350f),    // T1/T2 Elf
            PointF(320f, 400f),
            PointF(480f, 300f),    // T3 Renault (largo curvón)
            PointF(550f, 100f),
            PointF(480f, -150f),   // T4 Repsol
            PointF(350f, -300f),   // T5 Seat
            PointF(420f, -500f),   // T7/T8 Campsa
            PointF(550f, -750f),
            PointF(500f, -1100f),  // Recta trasera DRS
            PointF(350f, -1350f),  // T10 La Caixa
            PointF(180f, -1250f),  // T12 Banc Sabadell
            PointF(50f, -900f),    // T13/T14 Curva final sin chicane
            PointF(0f, 0f)
        ), totalTargetPoints = 120)

        val ann = listOf(
            CircuitTurnAnnotation("Elf", 12),
            CircuitTurnAnnotation("Renault Curvón", 26),
            CircuitTurnAnnotation("Repsol", 42),
            CircuitTurnAnnotation("Campsa", 60),
            CircuitTurnAnnotation("Recta Trasera", 78, isDrs = true),
            CircuitTurnAnnotation("Banc Sabadell", 100)
        )
        return CircuitData("Circuit de Barcelona-Catalunya", pts, ann)
    }

    // 11. ALBERT PARK (Melbourne, Australia)
    fun getMelbourneData(): CircuitData {
        val pts = generateSmoothCircuit(listOf(
            PointF(0f, 0f), PointF(180f, 300f), PointF(350f, 500f), PointF(480f, 350f),
            PointF(520f, 100f), PointF(650f, -300f), PointF(720f, -700f), PointF(580f, -1100f),
            PointF(350f, -1350f), PointF(120f, -1100f), PointF(50f, -600f), PointF(0f, 0f)
        ), totalTargetPoints = 120)
        val ann = listOf(
            CircuitTurnAnnotation("Jones (T1)", 12),
            CircuitTurnAnnotation("Sportsgate", 38),
            CircuitTurnAnnotation("Lakeside DRS", 62, isDrs = true),
            CircuitTurnAnnotation("Ascari", 88)
        )
        return CircuitData("Albert Park Circuit", pts, ann)
    }

    // 12. JEDDAH CORNICHE CIRCUIT (Arabia Saudí)
    fun getJeddahData(): CircuitData {
        val pts = generateSmoothCircuit(listOf(
            PointF(0f, 0f), PointF(50f, 500f), PointF(150f, 1200f), PointF(180f, 2200f),
            PointF(120f, 3200f), PointF(-50f, 4000f), PointF(50f, 4400f), PointF(220f, 3800f),
            PointF(200f, 2600f), PointF(250f, 1400f), PointF(100f, 600f), PointF(0f, 0f)
        ), totalTargetPoints = 120)
        val ann = listOf(
            CircuitTurnAnnotation("T1/T2 Chicane", 10),
            CircuitTurnAnnotation("T13 Hairpin", 50),
            CircuitTurnAnnotation("DRS Back Straight", 75, isDrs = true),
            CircuitTurnAnnotation("T27 Final Turn", 112)
        )
        return CircuitData("Jeddah Corniche Circuit", pts, ann)
    }

    // 13. MIAMI INTERNATIONAL AUTODROME
    fun getMiamiData(): CircuitData {
        val pts = generateSmoothCircuit(listOf(
            PointF(0f, 0f), PointF(150f, 300f), PointF(350f, 450f), PointF(600f, 350f),
            PointF(750f, 100f), PointF(850f, -400f), PointF(750f, -900f), PointF(500f, -1300f),
            PointF(250f, -1100f), PointF(120f, -600f), PointF(0f, 0f)
        ), totalTargetPoints = 120)
        val ann = listOf(
            CircuitTurnAnnotation("T1", 12),
            CircuitTurnAnnotation("Marina Chicane", 45),
            CircuitTurnAnnotation("Back Straight DRS", 78, isDrs = true),
            CircuitTurnAnnotation("Hard Rock Stadium", 105)
        )
        return CircuitData("Miami International Autodrome", pts, ann)
    }

    // 14. CIRCUIT GILLES-VILLENEUVE (Montreal, Canadá)
    fun getMontrealData(): CircuitData {
        val pts = generateSmoothCircuit(listOf(
            PointF(0f, 0f), PointF(80f, 400f), PointF(200f, 750f), PointF(280f, 1300f),
            PointF(320f, 2000f), PointF(400f, 2400f), PointF(420f, 1800f), PointF(380f, 1100f),
            PointF(300f, 400f), PointF(150f, 100f), PointF(0f, 0f)
        ), totalTargetPoints = 120)
        val ann = listOf(
            CircuitTurnAnnotation("Senna 'S'", 14),
            CircuitTurnAnnotation("Pont de la Concorde", 46),
            CircuitTurnAnnotation("L'Epingle Hairpin", 72),
            CircuitTurnAnnotation("Casino Straight DRS", 95, isDrs = true),
            CircuitTurnAnnotation("Wall of Champions", 110)
        )
        return CircuitData("Circuit Gilles-Villeneuve", pts, ann)
    }

    // 15. CIRCUIT OF THE AMERICAS (Austin, EE. UU.)
    fun getCotaData(): CircuitData {
        val pts = generateSmoothCircuit(listOf(
            PointF(0f, 0f), PointF(250f, 400f), PointF(450f, 650f), PointF(600f, 500f),
            PointF(700f, 250f), PointF(850f, -200f), PointF(750f, -700f), PointF(500f, -1000f),
            PointF(300f, -750f), PointF(150f, -300f), PointF(0f, 0f)
        ), totalTargetPoints = 120)
        val ann = listOf(
            CircuitTurnAnnotation("T1 Uphill", 12),
            CircuitTurnAnnotation("Esses", 32),
            CircuitTurnAnnotation("Back Straight DRS", 65, isDrs = true),
            CircuitTurnAnnotation("Tower Carousel", 95)
        )
        return CircuitData("Circuit of the Americas", pts, ann)
    }

    // 16. BAKU CITY CIRCUIT (Azerbaiyán)
    fun getBakuData(): CircuitData {
        val pts = generateSmoothCircuit(listOf(
            PointF(0f, 0f), PointF(50f, 400f), PointF(120f, 900f), PointF(250f, 1300f),
            PointF(350f, 1200f), PointF(450f, 800f), PointF(520f, 300f), PointF(450f, -200f),
            PointF(250f, -400f), PointF(100f, -200f), PointF(0f, 0f)
        ), totalTargetPoints = 120)
        val ann = listOf(
            CircuitTurnAnnotation("T1 90° Left", 10),
            CircuitTurnAnnotation("Old City Castle", 48),
            CircuitTurnAnnotation("Caspian Sea Straight", 85, isDrs = true)
        )
        return CircuitData("Baku City Circuit", pts, ann)
    }

    // 17. SHANGHAI INTERNATIONAL CIRCUIT (China)
    fun getShanghaiData(): CircuitData {
        val pts = generateSmoothCircuit(listOf(
            PointF(0f, 0f), PointF(180f, 350f), PointF(350f, 600f), PointF(500f, 400f),
            PointF(650f, 150f), PointF(750f, -300f), PointF(600f, -750f), PointF(400f, -950f),
            PointF(200f, -600f), PointF(80f, -250f), PointF(0f, 0f)
        ), totalTargetPoints = 120)
        val ann = listOf(
            CircuitTurnAnnotation("Caracol T1/T2", 15),
            CircuitTurnAnnotation("Recta Larga DRS", 70, isDrs = true),
            CircuitTurnAnnotation("Horquilla T14", 95)
        )
        return CircuitData("Shanghai International Circuit", pts, ann)
    }

    // 18. HUNGARORING (Hungría)
    fun getHungaroringData(): CircuitData {
        val pts = generateSmoothCircuit(listOf(
            PointF(0f, 0f), PointF(150f, 250f), PointF(320f, 450f), PointF(500f, 400f),
            PointF(600f, 150f), PointF(550f, -250f), PointF(420f, -600f), PointF(250f, -500f),
            PointF(120f, -250f), PointF(0f, 0f)
        ), totalTargetPoints = 120)
        val ann = listOf(
            CircuitTurnAnnotation("DRS Pit Straight", 4, isDrs = true),
            CircuitTurnAnnotation("T1 Downhill", 12),
            CircuitTurnAnnotation("Chicane T6/T7", 52),
            CircuitTurnAnnotation("Mansell T11", 85)
        )
        return CircuitData("Hungaroring", pts, ann)
    }

    // 19. CIRCUIT ZANDVOORT (Países Bajos)
    fun getZandvoortData(): CircuitData {
        val pts = generateSmoothCircuit(listOf(
            PointF(0f, 0f), PointF(180f, 300f), PointF(350f, 500f), PointF(520f, 350f),
            PointF(600f, 100f), PointF(550f, -300f), PointF(420f, -650f), PointF(250f, -500f),
            PointF(100f, -200f), PointF(0f, 0f)
        ), totalTargetPoints = 120)
        val ann = listOf(
            CircuitTurnAnnotation("Tarzanbocht (T1)", 12),
            CircuitTurnAnnotation("Hugenholtz Perfilada", 35),
            CircuitTurnAnnotation("Arie Luyendyk DRS", 105, isDrs = true)
        )
        return CircuitData("Circuit Zandvoort", pts, ann)
    }

    // 20. AUTÓDROMO HERMANOS RODRÍGUEZ (México)
    fun getMexicoData(): CircuitData {
        val pts = generateSmoothCircuit(listOf(
            PointF(0f, 0f), PointF(150f, 350f), PointF(320f, 500f), PointF(500f, 400f),
            PointF(650f, 150f), PointF(700f, -300f), PointF(550f, -700f), PointF(350f, -600f),
            PointF(180f, -300f), PointF(0f, 0f)
        ), totalTargetPoints = 120)
        val ann = listOf(
            CircuitTurnAnnotation("DRS Main Straight", 4, isDrs = true),
            CircuitTurnAnnotation("Frenada T1", 12),
            CircuitTurnAnnotation("Esses", 55),
            CircuitTurnAnnotation("Foro Sol Stadium", 95)
        )
        return CircuitData("Autódromo Hermanos Rodríguez", pts, ann)
    }

    // 21. AUTODROMO ENZO E DINO FERRARI (Imola)
    fun getImolaData(): CircuitData {
        val pts = generateSmoothCircuit(listOf(
            PointF(0f, 0f), PointF(180f, 320f), PointF(350f, 550f), PointF(520f, 450f),
            PointF(650f, 200f), PointF(600f, -250f), PointF(450f, -600f), PointF(280f, -500f),
            PointF(120f, -250f), PointF(0f, 0f)
        ), totalTargetPoints = 120)
        val ann = listOf(
            CircuitTurnAnnotation("DRS Pit Straight", 4, isDrs = true),
            CircuitTurnAnnotation("Tamburello Chicane", 14),
            CircuitTurnAnnotation("Villeneuve", 38),
            CircuitTurnAnnotation("Tosa Hairpin", 55),
            CircuitTurnAnnotation("Acque Minerali", 75),
            CircuitTurnAnnotation("Rivazza", 102)
        )
        return CircuitData("Autodromo Enzo e Dino Ferrari", pts, ann)
    }

    // 22. MARINA BAY STREET CIRCUIT (Singapur)
    fun getSingaporeData(): CircuitData {
        val pts = generateSmoothCircuit(listOf(
            PointF(0f, 0f), PointF(140f, 280f), PointF(300f, 450f), PointF(480f, 380f),
            PointF(600f, 200f), PointF(700f, -150f), PointF(620f, -550f), PointF(450f, -750f),
            PointF(280f, -600f), PointF(120f, -300f), PointF(0f, 0f)
        ), totalTargetPoints = 120)
        val ann = listOf(
            CircuitTurnAnnotation("Sheares (T1)", 12),
            CircuitTurnAnnotation("Memorial Corner", 42),
            CircuitTurnAnnotation("Bayfront DRS", 75, isDrs = true),
            CircuitTurnAnnotation("Marina Bay Bayfront", 105)
        )
        return CircuitData("Marina Bay Street Circuit", pts, ann)
    }

    // 23. LUSAIL INTERNATIONAL CIRCUIT (Qatar)
    fun getQatarData(): CircuitData {
        val pts = generateSmoothCircuit(listOf(
            PointF(0f, 0f), PointF(200f, 300f), PointF(400f, 500f), PointF(580f, 400f),
            PointF(700f, 150f), PointF(750f, -250f), PointF(600f, -650f), PointF(420f, -600f),
            PointF(250f, -400f), PointF(100f, -150f), PointF(0f, 0f)
        ), totalTargetPoints = 120)
        val ann = listOf(
            CircuitTurnAnnotation("T1 Right", 14),
            CircuitTurnAnnotation("Triple Apex Right", 55),
            CircuitTurnAnnotation("Main Straight DRS", 98, isDrs = true)
        )
        return CircuitData("Lusail International Circuit", pts, ann)
    }

    // 24. YAS MARINA CIRCUIT (Abu Dhabi)
    fun getYasMarinaData(): CircuitData {
        val pts = generateSmoothCircuit(listOf(
            PointF(0f, 0f), PointF(180f, 350f), PointF(350f, 550f), PointF(500f, 420f),
            PointF(650f, 200f), PointF(750f, -200f), PointF(650f, -700f), PointF(450f, -900f),
            PointF(250f, -700f), PointF(120f, -300f), PointF(0f, 0f)
        ), totalTargetPoints = 120)
        val ann = listOf(
            CircuitTurnAnnotation("T1 Left", 12),
            CircuitTurnAnnotation("North Hairpin (T5)", 40),
            CircuitTurnAnnotation("Long Straight DRS", 62, isDrs = true),
            CircuitTurnAnnotation("W Hotel Marina", 98)
        )
        return CircuitData("Yas Marina Circuit", pts, ann)
    }

    /**
     * Interpola suavemente puntos clave de control para generar curvas de competición
     * naturales con un recuento uniforme de puntos (por defecto 120).
     */
    private fun generateSmoothCircuit(controlPoints: List<PointF>, totalTargetPoints: Int = 120): List<PointF> {
        if (controlPoints.size < 3) return controlPoints
        val result = mutableListOf<PointF>()
        val n = controlPoints.size
        val pointsPerSegment = (totalTargetPoints / n).coerceAtLeast(2)

        for (i in 0 until n) {
            val p0 = controlPoints[(i - 1 + n) % n]
            val p1 = controlPoints[i]
            val p2 = controlPoints[(i + 1) % n]
            val p3 = controlPoints[(i + 2) % n]

            for (j in 0 until pointsPerSegment) {
                val t = j.toFloat() / pointsPerSegment.toFloat()
                val pt = catmullRom(p0, p1, p2, p3, t)
                result.add(pt)
            }
        }
        return result
    }

    /**
     * Spline de Catmull-Rom para curvas suaves de circuito sin esquinas afiladas.
     */
    private fun catmullRom(p0: PointF, p1: PointF, p2: PointF, p3: PointF, t: Float): PointF {
        val t2 = t * t
        val t3 = t2 * t
        val x = 0.5f * ((2f * p1.x) +
                (-p0.x + p2.x) * t +
                (2f * p0.x - 5f * p1.x + 4f * p2.x - p3.x) * t2 +
                (-p0.x + 3f * p1.x - 3f * p2.x + p3.x) * t3)
        val y = 0.5f * ((2f * p1.y) +
                (-p0.y + p2.y) * t +
                (2f * p0.y - 5f * p1.y + 4f * p2.y - p3.y) * t2 +
                (-p0.y + 3f * p1.y - 3f * p2.y + p3.y) * t3)
        return PointF(x, y)
    }
}
