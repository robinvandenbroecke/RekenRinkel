import com.rekenrinkel.domain.content.ContentRepository

fun main() {
    val allConfigs = ContentRepository.getAllConfigs()
    val allSkillIds = allConfigs.map { it.skillId }.toSet()
    
    println("Checking prerequisites...")
    println("Total skills: ${allSkillIds.size}")
    println()
    
    val problems = mutableListOf<String>()
    
    allConfigs.forEach { config ->
        config.prerequisites.forEach { prereqId ->
            if (prereqId !in allSkillIds) {
                problems.add("Skill ${config.skillId} has non-existent prerequisite: $prereqId")
            }
        }
    }
    
    if (problems.isEmpty()) {
        println("✓ All prerequisites refer to existing skills")
    } else {
        println("✗ Found ${problems.size} problems:")
        problems.forEach { println("  - $it") }
    }
    
    // Also check for circular dependencies
    println()
    println("Checking for skill IDs that might be missing...")
    val allReferencedIds = allConfigs.flatMap { it.prerequisites }.toSet()
    val missingIds = allReferencedIds - allSkillIds
    if (missingIds.isNotEmpty()) {
        println("Missing skill IDs that are referenced as prerequisites:")
        missingIds.forEach { println("  - $it") }
    } else {
        println("✓ All referenced skill IDs exist")
    }
}
