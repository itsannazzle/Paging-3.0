/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.codelabs.paging.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.liveData
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import com.example.android.codelabs.paging.data.GithubRepository
import com.example.android.codelabs.paging.model.Repo
import com.example.android.codelabs.paging.model.RepoSearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * ViewModel for the [SearchRepositoriesActivity] screen.
 * The ViewModel works with the [GithubRepository] to get the data.
 */
class SearchRepositoriesViewModel(private val repository: GithubRepository) : ViewModel() {

    private var currentQueryValue : String? = null
    private var currentSearchResult : Flow<PagingData<UiModel>>? = null


    /*companion object {
        private const val VISIBLE_THRESHOLD = 5
    }*/

    /*private val queryLiveData = MutableLiveData<String>()
    val repoResult: LiveData<RepoSearchResult> = queryLiveData.switchMap { queryString ->
        liveData {
            val repos = repository.getSearchResultStream(queryString).asLiveData(Dispatchers.Main)
            emitSource(repos)
        }
    }*/

    /**
     * Search a repository based on a query string.
     */
    suspend fun searchRepo(queryString: String) : Flow<PagingData<UiModel>> {
        val lastResult = currentSearchResult
        if (queryString == currentQueryValue && lastResult != null) {
            return lastResult
        }
        currentQueryValue = queryString
        val newResult : Flow<PagingData<UiModel>> = repository.getSearchResultStream(queryString)
                .map { pagingData -> pagingData.map { UiModel.RepoItem(it) } }
                .map {
                    it.insertSeparators<UiModel.RepoItem, UiModel>{
                        before, after ->
                        if (after == null){
                            //we are at the end of the list
                            return@insertSeparators null
                        }
                        if (before == null) {
                            //we are at the beggining of the list
                            return@insertSeparators UiModel.SeparatorItem("${after.roundedStarsCount}0.000+ stars")
                        }
                        //check between 2 items
                        if (before.roundedStarsCount > after.roundedStarsCount) {
                            if (after.roundedStarsCount >= 1) {
                                UiModel.SeparatorItem("${after.roundedStarsCount}0.000+ stars")
                            } else {
                                UiModel.SeparatorItem("< 10.000+ stars")
                            }
                        } else {
                            //no separator
                            null
                        }
                    }
                }
                .cachedIn(viewModelScope)
        currentSearchResult = newResult
        return newResult
    }

    sealed class UiModel {
        data class RepoItem (val repo: Repo) : UiModel()
        data class SeparatorItem (val desc : String) :  UiModel()
    }
    //extension property
    private val UiModel.RepoItem.roundedStarsCount : Int
    get() = this.repo.stars / 10_000

    /*fun listScrolled(visibleItemCount: Int, lastVisibleItemPosition: Int, totalItemCount: Int) {
        if (visibleItemCount + lastVisibleItemPosition + VISIBLE_THRESHOLD >= totalItemCount) {
            val immutableQuery = queryLiveData.value
            if (immutableQuery != null) {
                viewModelScope.launch {
                    repository.requestMore(immutableQuery)
                }
            }
        }
    }*/
}
