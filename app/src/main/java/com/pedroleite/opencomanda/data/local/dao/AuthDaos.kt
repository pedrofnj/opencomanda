package com.pedroleite.opencomanda.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.pedroleite.opencomanda.data.local.entity.AuditLogEntity
import com.pedroleite.opencomanda.data.local.entity.RoleEntity
import com.pedroleite.opencomanda.data.local.entity.RolePermissionEntity
import com.pedroleite.opencomanda.data.local.entity.SecuritySettingsEntity
import com.pedroleite.opencomanda.data.local.entity.UserEntity
import com.pedroleite.opencomanda.domain.auth.SystemRole
import kotlinx.coroutines.flow.Flow

@Dao
interface RoleDao {
    @Insert
    suspend fun insert(role: RoleEntity): Long

    @Update
    suspend fun update(role: RoleEntity)

    @Query("DELETE FROM roles WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM roles WHERE id = :id")
    suspend fun getById(id: Long): RoleEntity?

    @Query("SELECT * FROM roles WHERE systemRole = :systemRole LIMIT 1")
    suspend fun getBySystemRole(systemRole: SystemRole): RoleEntity?

    @Query("SELECT * FROM roles ORDER BY systemRole IS NULL, id ASC")
    fun observeAll(): Flow<List<RoleEntity>>

    @Query("SELECT * FROM roles ORDER BY systemRole IS NULL, id ASC")
    suspend fun getAll(): List<RoleEntity>

    @Query("SELECT permission FROM role_permissions WHERE roleId = :roleId")
    suspend fun getPermissionKeys(roleId: Long): List<String>

    @Query("SELECT * FROM role_permissions")
    fun observeAllPermissions(): Flow<List<RolePermissionEntity>>

    @Insert
    suspend fun insertPermissions(permissions: List<RolePermissionEntity>)

    @Query("DELETE FROM role_permissions WHERE roleId = :roleId")
    suspend fun clearPermissions(roleId: Long)

    @Query("SELECT COUNT(*) FROM users WHERE roleId = :roleId")
    suspend fun countUsers(roleId: Long): Int
}

@Dao
interface UserDao {
    @Insert
    suspend fun insert(user: UserEntity): Long

    @Update
    suspend fun update(user: UserEntity)

    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun getById(id: Long): UserEntity?

    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun getByUsername(username: String): UserEntity?

    @Query("SELECT COUNT(*) FROM users")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM users")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM users ORDER BY isOwner DESC, displayName COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE active = 1 ORDER BY displayName COLLATE NOCASE ASC")
    fun observeActive(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE isOwner = 1 LIMIT 1")
    suspend fun getOwner(): UserEntity?
}

@Dao
interface SecuritySettingsDao {
    @Insert
    suspend fun insert(settings: SecuritySettingsEntity)

    @Update
    suspend fun update(settings: SecuritySettingsEntity)

    @Query("SELECT * FROM security_settings WHERE id = ${SecuritySettingsEntity.SINGLETON_ID}")
    suspend fun get(): SecuritySettingsEntity?

    @Query("SELECT * FROM security_settings WHERE id = ${SecuritySettingsEntity.SINGLETON_ID}")
    fun observe(): Flow<SecuritySettingsEntity?>
}

@Dao
interface AuditLogDao {
    @Insert
    suspend fun insert(entry: AuditLogEntity): Long

    @Query("SELECT * FROM audit_log ORDER BY createdAt DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_log ORDER BY createdAt DESC, id DESC")
    suspend fun getAll(): List<AuditLogEntity>
}
