package ch.sumry.core.module.category.model.dto

import ch.sumry.core.types.CategoryType
import java.util.*

data class CategoryResponseDTO(
    val id: UUID,
    val label: String,
    val color: String?,
    val icon: String?,
    val categoryType: CategoryType,

    // The UUID of the owner (for security and client-side grouping)
    val userId: UUID?
)