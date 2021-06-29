package com.example.android.codelabs.paging.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.android.codelabs.paging.model.Repo

@Dao
interface DAO {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(repos: List<Repo>)

    @Query("SELECT * FROM repos WHERE name LIKE :queryString or description LIKE :queryString ORDER BY stars DESC, name ASC")
    fun resposByName(queryString: String) : PagingSource<Int, Repo>

    @Query("DELETE from  repos")
    suspend fun clearRepos()

}