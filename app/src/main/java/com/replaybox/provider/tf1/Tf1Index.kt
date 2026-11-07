package com.replaybox.provider.tf1

import com.replaybox.provider.api.CatalogueIndex

/**
 * The TF1 Index: the unfiltered programs listing (483ce0f) paged by page index,
 * [Tf1Http.INDEX_PAGE_SIZE] programmes per page, until `data.programs.total` is reached
 * (SPEC §Provider TF1+). One instance is shared by the 5 group Chaînes.
 */
fun tf1CatalogueIndex(http: Tf1Http): CatalogueIndex = CatalogueIndex(
    fetchPage = { pageIndex ->
        val root = http.graphql(
            Tf1QueryIds.PROGRAMS,
            http.programsVariables(channelSlug = null, offset = pageIndex, limit = Tf1Http.INDEX_PAGE_SIZE),
        )
        Tf1Parsers.indexPage(root, pageIndex, Tf1Http.INDEX_PAGE_SIZE)
    },
)
