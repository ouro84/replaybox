package com.replaybox.provider.m6

import com.replaybox.provider.api.CatalogueIndex

/**
 * The M6 Index: `/services/6play/programs` paged by item offset, [M6Http.PAGE_SIZE] per page,
 * until `x-rest-collection-count` is reached (1288 measured 2026-11-07, 13 pages). Varnish
 * 503s are retried and offset drift is de-duplicated by [CatalogueIndex]. One instance is
 * shared by the 5 M6 group Chaînes.
 */
fun m6CatalogueIndex(http: M6Http): CatalogueIndex = CatalogueIndex(
    fetchPage = { pageIndex ->
        val response = http.get(M6Http.catalogPath(offset = pageIndex * M6Http.PAGE_SIZE))
        M6Parsers.catalogPage(response.body, response.totalCount, pageIndex, M6Http.PAGE_SIZE)
    },
)
