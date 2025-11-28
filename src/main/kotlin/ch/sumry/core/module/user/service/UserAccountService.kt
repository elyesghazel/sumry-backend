package ch.sumry.core.module.user.service

import ch.sumry.core.exception.RequestException
import ch.sumry.core.module.user.dto.accounts.UserAccountCreateDto
import ch.sumry.core.module.user.dto.accounts.UserAccountResponseDto
import ch.sumry.core.module.user.dto.accounts.UserAccountUpdateDto
import ch.sumry.core.module.user.mapper.UserAccountMapper
import ch.sumry.core.module.user.model.UserAccount
import ch.sumry.core.module.user.repo.UserAccountRepository
import ch.sumry.core.module.user.repo.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import java.security.Principal
import java.util.*


@Service
class UserAccountService(private val repo: UserAccountRepository, private val userRepository: UserRepository) {

    fun getAll(): List<UserAccount> = repo.findAll()

    fun getAllFromUser(userId: UUID, principal: Principal): List<UserAccount> {
        // check if userid from principal is matching with userid
        val providedUserId = UUID.fromString(principal.name)

        if (providedUserId != userId) {
            throw RequestException(HttpStatus.FORBIDDEN,"Forbidden")
        }

        return repo.findByUserId(userId)

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

    fun create(account: UserAccountCreateDto, principal: Principal): UserAccountResponseDto {
        val userId: UUID = UUID.fromString(principal.name)
        val entity = UserAccountMapper.fromDtoToEntity(account,
            userRepository.findById(userId).orElseThrow
            { RequestException(HttpStatus.NOT_FOUND,"User with ID $userId not found") }
        )
        // check if exists
        if (repo.existsByAccountNameAndUserId(entity.accountName, entity.user?.id)) {
            throw RequestException(HttpStatus.CONFLICT,"UserAccount with name ${entity.accountName} already exists")
        }
        val savedEntity = repo.save(entity)
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