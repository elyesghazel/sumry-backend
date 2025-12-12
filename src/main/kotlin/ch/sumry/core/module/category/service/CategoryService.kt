package ch.sumry.core.module.category.service

import ch.sumry.core.exception.RequestException
import ch.sumry.core.module.category.model.dto.CategoryCreateDTO
import ch.sumry.core.module.category.model.dto.CategoryResponseDTO
import ch.sumry.core.module.category.repo.CategoryRepository
import ch.sumry.core.module.user.repo.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.Principal
import java.util.UUID

// In CategoryService.kt

@Service
class CategoryService(
    private val categoryRepo: CategoryRepository,
    private val userRepo: UserRepository
) {
    @Transactional
    fun create(dto: CategoryCreateDTO, principal: Principal): CategoryResponseDTO {
        val userId = UUID.fromString(principal.name)

        // 1. Load User Reference and Check Existence
        val userEntity = userRepo.getReferenceById(userId)
        if (!userRepo.existsById(userId)) {
            throw RequestException(HttpStatus.NOT_FOUND, "User not found")
        }

        // 2. Conflict Check: Check if a category with this label already exists for this user
        if (categoryRepo.existsByLabelAndUser_Id(dto.label, userId)) {
            throw RequestException(HttpStatus.CONFLICT, "Category label already exists for this user.")
        }

        // 3. Map and Save
        val category = CategoryMapper.fromDtoToEntity(dto, userEntity)
        val savedCategory = categoryRepo.save(category)

        return CategoryMapper.toDto(savedCategory)
    }
}