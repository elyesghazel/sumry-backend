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
import java.util.*


@Service
class UserAccountService(private val repo: UserAccountRepository, private val userRepository: UserRepository) {

    fun getAll(): List<UserAccount> = repo.findAll()

    fun getAllFromUser(userId: UUID): List<UserAccount> =
        repo.findByUserId(userId)

    fun findById(accountId: UUID): UserAccountResponseDto {
        val entity = repo.findById(accountId).orElseThrow {
            RequestException(HttpStatus.NOT_FOUND,"UserAccount with ID $accountId not found")
        }
        return UserAccountMapper.toDto(entity)
    }

    fun create(account: UserAccountCreateDto): UserAccountResponseDto {
        val entity = UserAccountMapper.fromDtoToEntity(account,
            userRepository.findById(account.userId).orElseThrow
            { RequestException(HttpStatus.NOT_FOUND,"User with ID ${account.userId} not found") }
        )
        // check if exists
        if (repo.existsByAccountNameAndUserId(entity.accountName, entity.user?.id)) {
            throw RequestException(HttpStatus.CONFLICT,"UserAccount with name ${entity.accountName} already exists")
        }
        val savedEntity = repo.save(entity)
        return UserAccountMapper.toDto(savedEntity)
    }

    fun update(accountId: UUID, dto: UserAccountUpdateDto): UserAccountResponseDto {
        val entity = repo.findById(accountId).orElseThrow {
            RequestException(HttpStatus.NOT_FOUND,"UserAccount with ID $accountId not found")
        }

        entity.accountName = dto.accountName ?: entity.accountName
        entity.currentBalance = dto.currentBalance ?:  entity.currentBalance

        val updatedEntity = repo.save(entity)
        return UserAccountMapper.toDto(updatedEntity)
    }
    fun updateBalance(accountId: UUID, dto: UserAccountUpdateDto): UserAccountResponseDto {
        val entity = repo.findById(accountId).orElseThrow {
            RequestException(HttpStatus.NOT_FOUND,"UserAccount with ID $accountId not found")
        }

        entity.currentBalance = dto.currentBalance ?: entity.currentBalance

        val updatedEntity = repo.save(entity)
        return UserAccountMapper.toDto(updatedEntity)
    }

    fun delete(accountId: UUID): Boolean {
        val entity = repo.findById(accountId).orElseThrow {
            RequestException(HttpStatus.NOT_FOUND,"UserAccount with ID $accountId not found")
        }
        repo.delete(entity)
        return true
    }
}