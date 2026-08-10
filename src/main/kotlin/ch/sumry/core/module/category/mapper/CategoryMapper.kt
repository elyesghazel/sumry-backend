package ch.sumry.core.module.category.mapper

import ch.sumry.core.module.category.dto.CategoryCreateDTO
import ch.sumry.core.module.category.dto.CategoryResponseDTO
import ch.sumry.core.module.category.model.Category
import ch.sumry.core.module.user.model.User

object CategoryMapper {


    fun toDto(entity: Category): CategoryResponseDTO =
        CategoryResponseDTO(
            id = entity.id,
            label = entity.label,
            parentGroup = entity.parentGroup,
            color = entity.color,
            icon = entity.icon,
            categoryType = entity.categoryType,
            userId = entity.user?.id,
            isActive = entity.isActive
        )

    fun toDtoList(entities: List<Category>): List<CategoryResponseDTO> =
        entities.map { toDto(it) }

    fun fromDtoToEntity(dto: CategoryCreateDTO, userEntity: User): Category =
        Category(
            label = dto.label,
            parentGroup = dto.parentGroup,
            color = dto.color,
            icon = dto.icon,
            categoryType = dto.categoryType,
            user = userEntity
        )
}