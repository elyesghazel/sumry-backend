package ch.sumry.core.module.category.repo

import ch.sumry.core.module.category.model.Category
import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface CategoryRepository: JpaRepository<Category, UUID> {

    fun findCategoryByLabel(label: String): Optional<Category>
    fun existsByLabelAndUser_Id(label: String, userId: UUID): Boolean

    fun findByLabelAndUser_Id(label: String, userId: UUID): Optional<Category>

    fun findByLabelAndUser_IdAndIsActiveTrue(label: String, userId: UUID): Optional<Category>
}