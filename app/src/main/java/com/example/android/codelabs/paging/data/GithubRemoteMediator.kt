package com.example.android.codelabs.paging.data

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.withTransaction
import com.example.android.codelabs.paging.api.GithubService
import com.example.android.codelabs.paging.api.IN_QUALIFIER
import com.example.android.codelabs.paging.db.RemoteKeys
import com.example.android.codelabs.paging.db.RepoDatabase
import com.example.android.codelabs.paging.model.Repo
import retrofit2.HttpException
import java.io.IOException


private const val GITHUB_STARTING_PAGE_INDEX = 1
@OptIn(ExperimentalPagingApi::class)
class GithubRemoteMediator(
        private val query : String,
        private val service: GithubService,
        private val repoDatabase: RepoDatabase
) : RemoteMediator<Int, Repo>(){

    /*
    * PagingState - this gives us information about the pages that were loaded before,
    * the most recently accessed index in the list, and the PagingConfig we defined when initializing the paging stream.
    * */

    /*LoadType - this tells us whether we need to load data at the end (LoadType.APPEND)
    or at the beginning of the data (LoadType.PREPEND) that we previously loaded,
    or if this the first time we're loading data (LoadType.REFRESH).*/
    override suspend fun load(loadType: LoadType, state: PagingState<Int, Repo>): MediatorResult {
        val page = when(loadType){
            LoadType.APPEND -> {
                // If remoteKeys is null, that means the refresh result is not in the database yet.
                // We can return Success with endOfPaginationReached = false because Paging
                // will call this method again if RemoteKeys becomes non-null.
                // If remoteKeys is NOT NULL but its nextKey is null, that means we've reached
                // the end of pagination for append.
                val remoteKeys = getRemoteKeyForLastItem(state)
                val nextKey = remoteKeys?.nextKey
                if (nextKey == null) {
                    return MediatorResult.Success(endOfPaginationReached = remoteKeys != null)
                }
                nextKey
            }
            LoadType.PREPEND -> {
                val remoteKeys = getRemoteKeyForFirstItem(state)
                val prevKeys = remoteKeys?.prevKey
                if (prevKeys == null) {
                    return MediatorResult.Success(endOfPaginationReached = remoteKeys != null)
                }
                prevKeys
            }
            LoadType.REFRESH -> {
                val remoteKeys = getRemoteKeyClosestToCurrentPosition(state)
                remoteKeys?.nextKey?.minus(1) ?: GITHUB_STARTING_PAGE_INDEX
            }
        }
        val apiQuery = query + IN_QUALIFIER
        try {
            val apiResponse = service.searchRepos(apiQuery, page, state.config.pageSize)
            val repos = apiResponse.items
            val endOfPaginationReached = repos.isEmpty()
            repoDatabase.withTransaction {
                //clear all tables in db
                if (loadType == LoadType.REFRESH){
                    repoDatabase.remoteKeysDao().clearRemoteKeys()
                    repoDatabase.reposDao().clearRepos()
                }
                val prevKey = if (page == GITHUB_STARTING_PAGE_INDEX) null else page - 1
                val nextKey = if (endOfPaginationReached) null else page + 1
                val keys = repos.map {
                    RemoteKeys(repoId = it.id, prevKey, nextKey)
                }
                repoDatabase.remoteKeysDao().insertAll(keys)
                repoDatabase.reposDao().insertAll(repos)
            }
            return  MediatorResult.Success(endOfPaginationReached)
        } catch (ex : IOException) {
            return  MediatorResult.Error(ex)
        } catch (httpEx : HttpException) {
            return  MediatorResult.Error(httpEx)
        }
    }

    private suspend fun getRemoteKeyForLastItem(state: PagingState<Int, Repo>) : RemoteKeys? {
        // Get the last page that was retrieved, that contained items.
        // From that last page, get the last item

        return state.pages.lastOrNull() { it.data.isNullOrEmpty() }?.data?.lastOrNull() ?.let {
            repo -> repoDatabase.remoteKeysDao().remoteKeysRepoId(repo.id)
        }
    }

    private suspend fun getRemoteKeyForFirstItem(state: PagingState<Int, Repo>) : RemoteKeys? {
        // Get the first page that was retrieved, that contained items.
        // From that first page, get the first item

        return state.pages.firstOrNull() { it.data.isNotEmpty() }?.data?.firstOrNull() ?.let {
            // Get the remote keys of the first items retrieved
            repo -> repoDatabase.remoteKeysDao().remoteKeysRepoId(repo.id)
        }
    }
    private suspend fun getRemoteKeyClosestToCurrentPosition(state: PagingState<Int, Repo>) : RemoteKeys? {
        // The paging library is trying to load data after the anchor position
        // Get the item closest to the anchor position
        return state.anchorPosition?.let {
            position -> state.closestItemToPosition(position)?.id?.let {
                repoId -> repoDatabase.remoteKeysDao().remoteKeysRepoId(repoId)
        }
        }
    }
}