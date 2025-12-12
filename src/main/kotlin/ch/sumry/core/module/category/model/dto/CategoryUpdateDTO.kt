package ch.sumry.core.module.category.model.dto

import ch.sumry.core.types.CategoryType

data class CategoryUpdateDTO(
    val label: String? = null,
    val color: String? = null,
    val icon: String? = null,
    val categoryType: CategoryType? = null
)