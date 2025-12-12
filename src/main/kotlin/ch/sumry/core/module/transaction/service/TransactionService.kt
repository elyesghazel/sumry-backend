package ch.sumry.core.module.transaction.service

import ch.sumry.core.exception.RequestException
import ch.sumry.core.module.category.repo.CategoryRepository
import ch.sumry.core.module.transaction.dto.TransactionCreateDTO
import ch.sumry.core.module.transaction.dto.TransactionResponseDTO
import ch.sumry.core.module.transaction.mapper.TransactionMapper
import ch.sumry.core.module.transaction.repo.TransactionRepository
import ch.sumry.core.module.user.repo.UserAccountRepository
import ch.sumry.core.module.user.repo.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.Principal
import java.util.*

@Service
class TransactionService(
    private val transactionRepository: TransactionRepository,
    private val userAccountRepository: UserAccountRepository,
    private val userRepository: UserRepository,
    private val categoryRepository: CategoryRepository) {

    fun getByUserId(userId: UUID, principal: Principal): List<TransactionResponseDTO> {

        val providedUserId = UUID.fromString(principal.name)

        if (providedUserId != userId) {
            throw RequestException(HttpStatus.FORBIDDEN,"Forbidden")
        }

        val transactions = transactionRepository.findByUserAccount_User_Id(userId)

        return TransactionMapper.toDtoList(transactions)
    }

    @Transactional
    fun logTransaction(dto: TransactionCreateDTO, principal: Principal): TransactionResponseDTO {
        val authenticatedUserId = UUID.fromString(principal.name)
        val userAccountId = UUID.fromString(dto.userAccountId)

        // 1. Load User Account and check Ownership
        val userAccountEntity = userAccountRepository.findById(userAccountId).orElseThrow {
            RequestException(HttpStatus.NOT_FOUND, "Account not found")
        }

        // Check 1: Security (Ownership)
        if (userAccountEntity.user?.id != authenticatedUserId) {
            throw RequestException(HttpStatus.FORBIDDEN, "Forbidden: Account access denied")
        }

        // 2. Load Category Entity
        val categoryEntity = categoryRepository.findCategoryByLabel(dto.categoryLabel).orElseThrow {
            RequestException(HttpStatus.NOT_FOUND, "Category not found")
        }

        // 3. Map DTO to Entity using the loaded objects
        val transaction = TransactionMapper.fromDtoToEntity(
            dto,
            categoryEntity,
            userAccountEntity
        )

        // 4. Update Account Balance (Needs safe calculation)
        val currentBalance = userAccountEntity.currentBalance ?: 0.0
        val transactionAmount = transaction.amount ?: 0.0

        userAccountEntity.currentBalance = currentBalance.plus(transactionAmount)

        userAccountRepository.save(userAccountEntity) // Update account first (within the transaction)

        // 5. Save the Transaction
        transactionRepository.save(transaction)
        // 6. Return DTO
        return TransactionMapper.toDto(transaction)
    }
}