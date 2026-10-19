package dev.garden.reader.features

object ReleaseVersion {
    fun newer(remote: String, current: String): Boolean {
        fun parse(value: String): List<Int>? {
            val parts = Regex("^v?(\\d+)\\.(\\d+)\\.(\\d+)$").matchEntire(value.trim())?.groupValues?.drop(1) ?: return null
            return parts.map { it.toIntOrNull() ?: return null }
        }
        val a = parse(remote) ?: return false
        val b = parse(current) ?: return false
        return a.zip(b).firstOrNull { it.first != it.second }?.let { it.first > it.second } ?: false
    }
}
