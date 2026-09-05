package com.nv.navigation

import org.osmdroid.util.BoundingBox

fun RouteResult.pointsBoundingBox(): BoundingBox {
    require(points.isNotEmpty())
    val north = points.maxOf { it.latitude }
    val south = points.minOf { it.latitude }
    val east = points.maxOf { it.longitude }
    val west = points.minOf { it.longitude }
    return BoundingBox(north, east, south, west)
}
