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
    val color: String? = null,
    val icon: String? = null,

    @Enumerated(EnumType.STRING)
    val categoryType: CategoryType? = null,

    @ManyToOne
    @JoinColumn(name = "user_id")
    val user: User? = null,

)