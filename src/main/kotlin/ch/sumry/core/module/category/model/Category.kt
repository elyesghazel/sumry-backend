package ch.sumry.core.module.category.model

import ch.sumry.core.module.user.model.User
import ch.sumry.core.types.CategoryType
import jakarta.persistence.*
import java.util.*

@Entity
@Table(name = "categories")
class Category (
    @Id
    @GeneratedValue
    val id: UUID? = null,

    var label: String? = null,
    var color: String? = null,
    var icon: String? = null,

    @Enumerated(EnumType.STRING)
    var categoryType: CategoryType? = null,

    @ManyToOne
    @JoinColumn(name = "user_id")
    val user: User? = null,

    var isActive: Boolean? = true

    )