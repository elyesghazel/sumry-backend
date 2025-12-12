package ch.sumry.core.module.category.dto

import ch.sumry.core.types.CategoryType
import jakarta.validation.constraints.NotBlank

data class CategoryUpdateDTO(
    @field:NotBlank(message = "Label is required to update")
    val label: String? = null,


    val color: String? = null,
    val icon: String? = null,
    val categoryType: CategoryType? = null,
    val newLabel: String? = null
)