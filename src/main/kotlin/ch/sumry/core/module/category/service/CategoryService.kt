package ch.sumry.core.module.category.service

import ch.sumry.core.exception.RequestException
import ch.sumry.core.module.category.dto.CategoryCreateDTO
import ch.sumry.core.module.category.dto.CategoryFindDTO
import ch.sumry.core.module.category.dto.CategoryResponseDTO
import ch.sumry.core.module.category.dto.CategoryUpdateDTO
import ch.sumry.core.module.category.mapper.CategoryMapper
import ch.sumry.core.module.category.model.Category
import ch.sumry.core.module.category.repo.CategoryRepository
import ch.sumry.core.module.user.repo.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.Principal
import java.util.*

// In CategoryService.kt

@Service
class CategoryService(
    private val categoryRepo: CategoryRepository,
    private val userRepo: UserRepository
) {


    fun getCategory(label: String, principal: Principal): Category {
        val userId = UUID.fromString(principal.name)

        if (!userRepo.existsById(userId)) {
            throw RequestException(HttpStatus.NOT_FOUND, "User not found")
        }

        val category = categoryRepo.findByLabelAndUser_Id(label, userId)
            .orElseThrow { RequestException(HttpStatus.NOT_FOUND, "Category not found") }

        return category
    }

    fun getAll() = categoryRepo.findAll()

    fun getAllFromUser(principal: Principal): List<CategoryResponseDTO> {
        val userId = UUID.fromString(principal.name)

        if (!userRepo.existsById(userId)) {
            throw RequestException(HttpStatus.NOT_FOUND, "User not found")
        }

        val categories = categoryRepo.findAll().filter { it.user?.id == userId }

        return CategoryMapper.toDtoList(categories)
    }

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

    fun update(dto: CategoryUpdateDTO, principal: Principal): CategoryResponseDTO {
        val userId = UUID.fromString(principal.name)
        if (!userRepo.existsById(userId)) {
            throw RequestException(HttpStatus.NOT_FOUND, "User not found")
        }

        val categoryLabel = dto.label ?: throw RequestException(HttpStatus.BAD_REQUEST, "Category label must be provided for update")
        val category = categoryRepo.findByLabelAndUser_Id(categoryLabel, userId).orElseThrow { RequestException(HttpStatus.NOT_FOUND, "Category not found") }

        category.color = dto.color ?: category.color
        category.icon = dto.icon ?: category.icon
        category.label = dto.newLabel ?: category.label

        category.categoryType = dto.categoryType ?: category.categoryType

        val updatedCategory = categoryRepo.save(category)
        return CategoryMapper.toDto(updatedCategory)

    }

    fun delete(label: String, principal: Principal) {
        val category = getCategory(label, principal)

        category.isActive?.let {
            if (!it) {

                throw RequestException(HttpStatus.NOT_FOUND, "Category already deleted or not found.")
            }
        }

        category.isActive = false
        categoryRepo.save(category)
    }


    fun findByLabelAndUserId(dto: CategoryFindDTO, principal: Principal): CategoryResponseDTO {
        val category = getCategory(dto.label, principal)


        return CategoryMapper.toDto(category)
    }
}