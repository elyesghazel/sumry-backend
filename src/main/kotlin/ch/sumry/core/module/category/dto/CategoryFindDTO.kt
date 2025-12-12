package ch.sumry.core.module.category.dto

import jakarta.validation.constraints.NotBlank

data class CategoryFindDTO(
    @field:NotBlank(message = "Label is required")
    val label: String,
)
