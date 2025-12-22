package ch.sumry.core.module.transaction.service

import ch.sumry.core.exception.RequestException
import ch.sumry.core.module.category.repo.CategoryRepository
import ch.sumry.core.module.transaction.dto.TransactionCreateDTO
import ch.sumry.core.module.transaction.dto.TransactionResponseDTO
import ch.sumry.core.module.transaction.dto.TransactionUpdateDTO
import ch.sumry.core.module.transaction.mapper.TransactionMapper
import ch.sumry.core.module.transaction.repo.TransactionRepository
import ch.sumry.core.module.user.repo.UserAccountRepository
import ch.sumry.core.module.user.repo.UserRepository
import ch.sumry.core.types.CategoryType
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

    fun getByUserIdAdmin(userId: UUID, principal: Principal): List<TransactionResponseDTO> {

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
        val categoryEntity = categoryRepository.findByLabelAndUser_Id(dto.categoryLabel, authenticatedUserId).orElseThrow {
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

        val newBalance = if (categoryEntity.categoryType == CategoryType.INCOME) {
            currentBalance + transactionAmount
        } else {
            currentBalance - transactionAmount
        }

        transaction.balanceBefore = currentBalance
        transaction.balanceAfter = newBalance

        // D. Den Account aktualisieren (mit dem berechneten newBalance!)
        userAccountEntity.currentBalance = newBalance

        // 5. Speichern
        userAccountRepository.save(userAccountEntity)
        transactionRepository.save(transaction)

        return TransactionMapper.toDto(transaction)
    }

    @Transactional
    fun delete(id: UUID, principal: Principal) {
        val userId = UUID.fromString(principal.name)

        // 1. load
        val transaction = transactionRepository.findById(id).orElseThrow {
            RequestException(HttpStatus.NOT_FOUND, "Transaction not found")
        }

        // 2. principal ownership
        if (transaction.userAccount?.user?.id != userId) {
            throw RequestException(HttpStatus.FORBIDDEN, "Access denied")
        }

        val account = transaction.userAccount!!
        val amount = transaction.amount ?: 0.0
        val type = transaction.category?.categoryType ?: CategoryType.EXPENSE

        // check type
        val currentBalance = account.currentBalance ?: 0.0

        val newBalance = if (type == CategoryType.INCOME) {
            currentBalance - amount
        } else {
            currentBalance + amount
        }

        account.currentBalance = newBalance

        // 4. save & delete
        userAccountRepository.save(account) // update account balance
        transactionRepository.delete(transaction) // delete transac
    }

    @Transactional
    fun update(id: UUID, dto: TransactionUpdateDTO, principal: Principal): TransactionResponseDTO {
        val userId = UUID.fromString(principal.name)

        // 1. Load the existing transaction
        val transaction = transactionRepository.findById(id).orElseThrow {
            RequestException(HttpStatus.NOT_FOUND, "Transaction not found")
        }

        // 2. Security Check (Ownership of the CURRENT transaction)
        if (transaction.userAccount?.user?.id != userId) {
            throw RequestException(HttpStatus.FORBIDDEN, "Access denied")
        }

        // load new useraccount
        val targetAccountId: UUID? = if (dto.userAccountId != null) UUID.fromString(dto.userAccountId) else transaction.userAccount!!.id

        val targetAccount = userAccountRepository.findById(targetAccountId!!).orElseThrow {
            throw RequestException(HttpStatus.NOT_FOUND, "UserAccount not found")
        }

        // principal ownership
        if (targetAccount.user?.id != userId) {
            throw RequestException(HttpStatus.FORBIDDEN, "Access denied to target account")
        }

        // revert old transaction / effect
        val oldAccount = transaction.userAccount!!
        val oldAmount = transaction.amount ?: 0.0
        val oldType = transaction.category?.categoryType ?: CategoryType.EXPENSE

        // if expense take away, if income add back
        val revertedBalance = if (oldType == CategoryType.INCOME) {
            (oldAccount.currentBalance ?: 0.0) - oldAmount
        } else {
            (oldAccount.currentBalance ?: 0.0) + oldAmount
        }

        oldAccount.currentBalance = revertedBalance
        userAccountRepository.save(oldAccount) // Save the "clean" state of the old account

        // update

        // Update simple fields
        if (dto.description != null) transaction.description = dto.description
        if (dto.date != null) transaction.date = dto.date
        if (dto.amount != null) transaction.amount = dto.amount

        // Update Category (if changed)
        if (dto.categoryLabel != null) {
            val newCategory = categoryRepository.findByLabelAndUser_IdAndIsActiveTrue(dto.categoryLabel, userId)
                .orElseThrow { RequestException(HttpStatus.NOT_FOUND, "Category '${dto.categoryLabel}' not found") }
            transaction.category = newCategory
        }

        // Update Account link
        transaction.userAccount = targetAccount

        // apply new effect

        // If oldAccount == targetAccount, use revertedBalance
        val balanceBase = if (oldAccount.id == targetAccount.id) revertedBalance else (targetAccount.currentBalance ?: 0.0)

        val newAmount = transaction.amount ?: 0.0
        val newType = transaction.category?.categoryType ?: CategoryType.EXPENSE

        val finalBalance = if (newType == CategoryType.INCOME) {
            balanceBase + newAmount
        } else {
            balanceBase - newAmount
        }

        // Update the Target Account
        targetAccount.currentBalance = finalBalance

        // Update audit snapshots for the Transaction
        transaction.balanceBefore = balanceBase
        transaction.balanceAfter = finalBalance

        // ==================================================================================
        // STEP D: Save
        // ==================================================================================
        userAccountRepository.save(targetAccount) // Save the new balance
        val savedTransaction = transactionRepository.save(transaction)

        return TransactionMapper.toDto(savedTransaction)
    }


}