package com.givalimer.aajaj

/**
 * All material types. Order matters only for the palette UI.
 *
 * Properties:
 *  - color: base color. Per-pixel jitter is applied at render time for texture.
 *  - density: used to decide who sinks/floats when two fluids meet. Higher = sinks.
 *             Solids use INT_MAX so they never displace. EMPTY uses 0.
 *  - flammable: can catch fire when adjacent to FIRE/LAVA.
 *  - liquid: behaves like a fluid (falls, spreads sideways).
 *  - powder: behaves like sand (falls, piles up diagonally).
 *  - gas: rises and dissipates.
 */
enum class Material(
    val displayName: String,
    val color: Int,
    val density: Int,
    val flammable: Boolean = false,
    val liquid: Boolean = false,
    val powder: Boolean = false,
    val gas: Boolean = false
) {
    EMPTY("Воздух",   0x00000000.toInt(), 0),
    SAND("Песок",     0xFFE0C068.toInt(), 160, powder = true),
    WATER("Вода",     0xFF3A80E0.toInt(), 100, liquid = true),
    STONE("Камень",   0xFF777777.toInt(), Int.MAX_VALUE),
    WOOD("Дерево",    0xFF7A4B22.toInt(), Int.MAX_VALUE, flammable = true),
    FIRE("Огонь",     0xFFFF5020.toInt(), 5, gas = true),
    SMOKE("Дым",      0xFF303030.toInt(), 3, gas = true),
    OIL("Нефть",      0xFF221810.toInt(), 80, flammable = true, liquid = true),
    ACID("Кислота",   0xFF7CFC00.toInt(), 90, liquid = true),
    LAVA("Лава",      0xFFFF4000.toInt(), 180, liquid = true),
    ICE("Лёд",        0xFFB0E0F0.toInt(), Int.MAX_VALUE),
    STEAM("Пар",      0xFFC8D8E8.toInt(), 2, gas = true),
    PLANT("Росток",   0xFF2FA03A.toInt(), Int.MAX_VALUE, flammable = true),
    GUNPOWDER("Порох",0xFF2A2A2A.toInt(), 150, flammable = true, powder = true),
    SUGAR("Сахар",    0xFFF0F0F0.toInt(), 155, powder = true),
    ANT("Муравей",    0xFF3A1A0A.toInt(), 120),
    // Meta-tool: erase. Treated as EMPTY when painted.
    ERASE("Стёрка",   0x00000000.toInt(), 0);
}
