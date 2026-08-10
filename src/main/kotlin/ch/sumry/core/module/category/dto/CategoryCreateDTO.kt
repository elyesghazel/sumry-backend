package ch.sumry.core.module.category.dto

import ch.sumry.core.types.CategoryType
import jakarta.validation.constraints.NotBlank

data class CategoryCreateDTO(
    @field:NotBlank(message = "Category label is required")
    val label: String,

    val parentGroup: String? = null,

    val color: String? = null,
    val icon: String? = null,

    val categoryType: CategoryType,
)