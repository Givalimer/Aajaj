package com.givalimer.aajaj

enum class BlockType(
    val id: Int,
    val solid: Boolean = true,
    val topTex: Int = 0,
    val sideTex: Int = 0,
    val bottomTex: Int = 0
) {
    AIR(0, solid = false),
    GRASS(1, topTex = 0, sideTex = 1, bottomTex = 2),
    DIRT(2, topTex = 2, sideTex = 2, bottomTex = 2),
    STONE(3, topTex = 3, sideTex = 3, bottomTex = 3),
    COBBLESTONE(4, topTex = 4, sideTex = 4, bottomTex = 4),
    WOOD_PLANKS(5, topTex = 5, sideTex = 5, bottomTex = 5),
    LOG(6, topTex = 6, sideTex = 7, bottomTex = 6),
    LEAVES(8, topTex = 8, sideTex = 8, bottomTex = 8),
    SAND(9, topTex = 9, sideTex = 9, bottomTex = 9),
    WATER(10, solid = false, topTex = 10, sideTex = 10, bottomTex = 10),
    BEDROCK(11, topTex = 11, sideTex = 11, bottomTex = 11),
    COAL_ORE(12, topTex = 12, sideTex = 12, bottomTex = 12),
    IRON_ORE(13, topTex = 13, sideTex = 13, bottomTex = 13),
    GLASS(14, topTex = 14, sideTex = 14, bottomTex = 14),
    BRICK(15, topTex = 15, sideTex = 15, bottomTex = 15);

    companion object {
        private val byId = values().associateBy { it.id }
        fun fromId(id: Int): BlockType = byId[id] ?: AIR
    }
}

enum class Face(val dx: Int, val dy: Int, val dz: Int) {
    TOP(0, 1, 0),
    BOTTOM(0, -1, 0),
    NORTH(0, 0, -1),
    SOUTH(0, 0, 1),
    EAST(1, 0, 0),
    WEST(-1, 0, 0)
}
