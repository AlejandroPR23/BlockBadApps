package com.example.blockbadapps

data class Badge(
    val id: String,
    val emoji: String,
    val name: String,
    val description: String,
    val requiredDays: Int
)

object BadgeSystem {

    val ALL = listOf(
        Badge("d1",   "🌱", "Primer paso",      "Empezaste. Eso ya es todo.",              1),
        Badge("d7",   "🔥", "Una semana",        "Tu cerebro ya está cambiando.",           7),
        Badge("d14",  "💪", "El valle difícil",  "La segunda semana es la más dura.",      14),
        Badge("d30",  "🥉", "Un mes",            "Ya formaste un nuevo hábito.",            30),
        Badge("d90",  "🥈", "90 días",           "Tu cerebro se ha recableado.",           90),
        Badge("d180", "🥇", "Medio año",         "Eres una persona diferente.",           180),
        Badge("d365", "💎", "Un año",            "Libertad completa.",                    365)
    )

    fun getEarned(days: Int): List<Badge>  = ALL.filter { it.requiredDays <= days }

    fun getNextBadge(days: Int): Badge? = ALL.firstOrNull { it.requiredDays > days }

    /** 0f..1f de progreso hacia la siguiente medalla. */
    fun progressToNext(days: Int): Float {
        val next = getNextBadge(days) ?: return 1f
        val prev = ALL.lastOrNull { it.requiredDays <= days }
        val from = prev?.requiredDays ?: 0
        return (days - from).toFloat() / (next.requiredDays - from).toFloat()
    }

    val MOTIVATIONAL_MESSAGES = listOf(
        "Cada momento que resistes te hace más fuerte.",
        "Tu futuro yo te lo va a agradecer.",
        "El dolor de la disciplina es menor que el del arrepentimiento.",
        "Estás construyendo la mejor versión de ti mismo.",
        "Esto también pasará. Solo respira.",
        "Eres más fuerte que este impulso.",
        "Un día a la vez. Solo hoy.",
        "No eres tus impulsos. Eres tus decisiones.",
        "Cada 'no' de hoy es un 'sí' a tu libertad.",
        "La batalla más importante se gana en la mente."
    )

    fun randomMessage(): String = MOTIVATIONAL_MESSAGES.random()
}