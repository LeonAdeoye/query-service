package com.queryservice.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface QueryRepository : JpaRepository<QueryEntity, String> {
    fun findByName(name: String): Optional<QueryEntity>
}

