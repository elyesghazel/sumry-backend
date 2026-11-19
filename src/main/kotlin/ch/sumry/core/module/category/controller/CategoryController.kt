package ch.sumry.core.module.category.controller

import ch.sumry.core.module.category.dto.*
import ch.sumry.core.module.category.service.CategoryService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.security.Principal


@RestController
@RequestMapping("/api/category")
class CategoryController (private val categoryService: CategoryService) {

    @GetMapping("/user")
    fun getAllFromUser(principal: Principal): List<CategoryResponseDTO> =
        categoryService.getAllFromUser(principal)

    @PostMapping("/")
    fun createCategory(@Valid @RequestBody dto: CategoryCreateDTO, principal: Principal): ResponseEntity<CategoryResponseDTO?> {
        val createdCategory = categoryService.create(dto, principal)

        return ResponseEntity.status(201).body(createdCategory);
    }


    @PostMapping("/label")
    fun findByLabelAndUserID(@Valid @RequestBody dto: CategoryFindDTO, principal: Principal): ResponseEntity<CategoryResponseDTO> {
        val category = categoryService.findByLabelAndUserId(dto, principal)

        return ResponseEntity.status(200).body(category)
    }

    @PutMapping("/label")
    fun updateByLabel(@Valid @RequestBody dto: CategoryUpdateDTO, principal: Principal): ResponseEntity<CategoryResponseDTO?> {
        val updatedCategory = categoryService.update(dto, principal)

        return ResponseEntity.status(200).body(updatedCategory)
    }

    @DeleteMapping("/label")
    fun deleteByLabel(@Valid @RequestBody dto: CategoryDeleteDTO, principal: Principal): ResponseEntity<Unit> {
        categoryService.delete(dto.label, principal)

        return ResponseEntity.noContent().build()
    }


}