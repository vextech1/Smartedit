package com.vextech.smartedit.core.repository

import com.vextech.smartedit.core.database.SmartEditDatabase
import com.vextech.smartedit.core.library.SearchFilter
import com.vextech.smartedit.core.library.SearchPolicies
import com.vextech.smartedit.core.model.ResourceType
import com.vextech.smartedit.core.model.SearchHit
import com.vextech.smartedit.core.util.CorePolicies
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchRepository @Inject constructor(private val db: SmartEditDatabase) {
    /** Prefix full-text search over files and notes. Title matches are ranked first; snippets center on the match. */
    suspend fun search(raw: String, filter: SearchFilter = SearchFilter.ALL, limit: Int = 100): List<SearchHit> {
        val query = CorePolicies.ftsQuery(raw) ?: return emptyList()
        val terms = SearchPolicies.terms(raw)
        val hits = db.searchDao().search(query).map {
            SearchHit(
                resourceId = it.resourceId,
                type = runCatching { ResourceType.valueOf(it.resourceType) }.getOrDefault(ResourceType.DOCUMENT),
                title = it.title,
                snippet = SearchPolicies.snippet(it.body, terms),
                uri = it.uri,
            )
        }
        return SearchPolicies.rank(SearchPolicies.applyFilter(hits, filter), terms).take(limit)
    }
}
