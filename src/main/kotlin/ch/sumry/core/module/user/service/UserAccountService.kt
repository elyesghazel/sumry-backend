package ch.sumry.core.module.user.service

import ch.sumry.core.exception.RequestException
import ch.sumry.core.module.user.dto.accounts.UserAccountCreateDto
import ch.sumry.core.module.user.dto.accounts.UserAccountResponseDto
import ch.sumry.core.module.user.dto.accounts.UserAccountUpdateDto
import ch.sumry.core.module.user.mapper.UserAccountMapper
import ch.sumry.core.module.user.model.UserAccount
import ch.sumry.core.module.user.repo.UserAccountRepository
import ch.sumry.core.module.user.repo.UserRepository
import jakarta.transaction.Transactional
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import java.security.Principal
import java.util.*


@Service
class UserAccountService(private val repo: UserAccountRepository, private val userRepository: UserRepository) {

    @PreAuthorize("hasRole('ADMIN')")
    fun getAll(): List<UserAccount> = repo.findAll()

    fun getAllFromUser(userId: UUID, principal: Principal): List<UserAccount> {
        // check if userid from principal is matching with userid
        val providedUserId = UUID.fromString(principal.name)

        if (providedUserId != userId) {
            throw RequestException(HttpStatus.FORBIDDEN,"Forbidden")
        }

        return repo.findByUserId(userId)

    }
    fun getAllFromPrincipal(principal: Principal): List<UserAccountResponseDto> {
        val userId = UUID.fromString(principal.name)
        return UserAccountMapper.toDtoList(repo.findByUserId(userId))
    }

    fun findById(accountId: UUID, principal: Principal): UserAccountResponseDto {
        val entity = repo.findById(accountId).orElseThrow {
            NoSuchElementException("User with ID $accountId not found")
        }

        // check if userid from principal is matching with userid
        val providedUserId = UUID.fromString(principal.name)
        if (providedUserId != entity.user?.id) {
            throw RequestException(HttpStatus.FORBIDDEN,"Forbidden")
        }
        return UserAccountMapper.toDto(entity)
    }

    @Transactional
    fun create(account: UserAccountCreateDto, principal: Principal): UserAccountResponseDto {
        val userId: UUID = UUID.fromString(principal.name)

        // 🚨 FIX 1: Use getReferenceById to create a proxy reference.
        // This avoids unnecessary loading of the full User data and prevents the StaleObjectStateException.
        val userReference = userRepository.getReferenceById(userId)

        // FIX 2: Check for existence explicitly, as getReferenceById doesn't throw if the user doesn't exist
        // until the proxy is accessed later.
        if (!userRepository.existsById(userId)) {
            throw RequestException(HttpStatus.NOT_FOUND, "User with ID $userId not found")
        }


        // check if exists
        if (repo.existsByAccountNameAndUserId(account.accountName, userReference.id)) {
            throw RequestException(HttpStatus.CONFLICT,"UserAccount with name ${account.accountName} already exists")
        }

        val entity = UserAccountMapper.fromDtoToEntity(account, userReference) // Pass the proxy reference

        val savedEntity = repo.save(entity)
        // The rest of the saving process should now be clean, as only the new UserAccount is persisted.
        return UserAccountMapper.toDto(savedEntity)
    }

    fun update(accountId: UUID, dto: UserAccountUpdateDto, principal: Principal): UserAccountResponseDto {
        val entity = repo.findById(accountId).orElseThrow {
            RequestException(HttpStatus.NOT_FOUND,"UserAccount with ID $accountId not found")
        }

        val userId = UUID.fromString(principal.name)

        if (entity.user?.id != userId ) {
            throw RequestException(HttpStatus.FORBIDDEN,"Forbidden")
        }

        entity.accountName = dto.accountName ?: entity.accountName
        entity.currentBalance = dto.currentBalance ?:  entity.currentBalance

        val updatedEntity = repo.save(entity)
        return UserAccountMapper.toDto(updatedEntity)
    }
    fun updateBalance(accountId: UUID, dto: UserAccountUpdateDto, principal: Principal): UserAccountResponseDto {
        val entity = repo.findById(accountId).orElseThrow {
            RequestException(HttpStatus.NOT_FOUND,"UserAccount with ID $accountId not found")
        }

        val userId = UUID.fromString(principal.name)

        if (entity.user?.id != userId ) {
            throw RequestException(HttpStatus.FORBIDDEN,"Forbidden")
        }

        entity.currentBalance = dto.currentBalance ?: entity.currentBalance

        val updatedEntity = repo.save(entity)
        return UserAccountMapper.toDto(updatedEntity)
    }

    fun delete(accountId: UUID, principal: Principal): Boolean {
        val entity = repo.findById(accountId).orElseThrow {
            NoSuchElementException("UserAccount with ID $accountId not found")
        }
        val userId = UUID.fromString(principal.name)

        if (entity.user?.id != userId) {
            throw RequestException(HttpStatus.FORBIDDEN,"Forbidden")
        }
        repo.delete(entity)
        return true
    }
}