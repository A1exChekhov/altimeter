package com.chelmodeev.altimeter.maps

import android.net.Uri
import org.json.JSONObject
import java.io.File

/** Минимальный походный стиль Protomaps v4, не требующий сети, шрифтов или спрайтов. */
fun offlineMapStyle(path: String, includeOnlineFallback: Boolean = false): String {
    val baseMap = File(path)
    val archive = "pmtiles://${Uri.fromFile(baseMap)}"
    val sourceUrl = JSONObject.quote(archive)
    val terrain = File(baseMap.parentFile, "${baseMap.nameWithoutExtension}-dem.pmtiles")
    val terrainSource = if (terrain.isFile) {
        val terrainUrl = JSONObject.quote("pmtiles://${Uri.fromFile(terrain)}")
        """,
            "terrain": {
              "type": "raster-dem",
              "url": $terrainUrl,
              "tileSize": 512,
              "encoding": "terrarium",
              "attribution": "Terrain © Mapterhorn"
            }"""
    } else ""
    val hillshadeLayer = if (terrain.isFile) {
        """{"id":"hillshade","type":"hillshade","source":"terrain","paint":{"hillshade-exaggeration":0.55,"hillshade-shadow-color":"#4b4038","hillshade-highlight-color":"#fff7e2","hillshade-accent-color":"#776a59"}},"""
    } else ""
    val fallbackSource = if (includeOnlineFallback) {
        """,
            "online-topo": {
              "type": "raster",
              "tiles": [
                "https://a.tile.opentopomap.org/{z}/{x}/{y}.png",
                "https://b.tile.opentopomap.org/{z}/{x}/{y}.png",
                "https://c.tile.opentopomap.org/{z}/{x}/{y}.png"
              ],
              "tileSize": 256,
              "maxzoom": 17,
              "attribution": "© OpenStreetMap contributors, © OpenTopoMap"
            }"""
    } else ""
    val fallbackLayer = if (includeOnlineFallback) {
        """{"id":"online-topo","type":"raster","source":"online-topo"},"""
    } else ""
    return """
        {
          "version": 8,
          "name": "Errarium Offline Outdoor",
          "glyphs": "asset://fonts/{fontstack}/{range}.pbf",
          "sources": {
            "protomaps": {
              "type": "vector",
              "url": $sourceUrl,
              "attribution": "© OpenStreetMap contributors"
            }$terrainSource$fallbackSource
          },
          "layers": [
            {"id":"background","type":"background","paint":{"background-color":"#e8e2d1"}},
            $fallbackLayer
            {"id":"earth","type":"fill","source":"protomaps","source-layer":"earth","paint":{"fill-color":"#e8e2d1"}},
            {"id":"landcover","type":"fill","source":"protomaps","source-layer":"landcover","paint":{"fill-color":["match",["get","kind"],"forest","#bfd3ae","glacier","#d9edf1","scrub","#d8d6af","grassland","#d5d8ae","#d9d5bb"],"fill-opacity":0.8}},
            {"id":"landuse","type":"fill","source":"protomaps","source-layer":"landuse","paint":{"fill-color":["match",["get","kind"],"forest","#b6cda6","wood","#b6cda6","national_park","#c8dcb8","nature_reserve","#c8dcb8","glacier","#d9edf1","sand","#ead9aa","bare_rock","#c9c1b4","#d7d3bb"],"fill-opacity":0.62}},
            $hillshadeLayer
            {"id":"water-fill","type":"fill","source":"protomaps","source-layer":"water","filter":["==",["geometry-type"],"Polygon"],"paint":{"fill-color":"#8fc8dc"}},
            {"id":"water-line","type":"line","source":"protomaps","source-layer":"water","filter":["==",["geometry-type"],"LineString"],"paint":{"line-color":"#63a9c6","line-width":["interpolate",["linear"],["zoom"],8,0.7,14,2.2]}},
            {"id":"boundaries","type":"line","source":"protomaps","source-layer":"boundaries","paint":{"line-color":"#aa9285","line-width":0.7,"line-dasharray":[4,3],"line-opacity":0.6}},
            {"id":"buildings","type":"fill","source":"protomaps","source-layer":"buildings","minzoom":13,"paint":{"fill-color":"#bcb3aa","fill-outline-color":"#958c83"}},
            {"id":"major-road-casing","type":"line","source":"protomaps","source-layer":"roads","filter":["in",["get","kind"],["literal",["highway","major_road"]]],"paint":{"line-color":"#a79478","line-width":["interpolate",["linear"],["zoom"],7,1.2,15,7.0]}},
            {"id":"major-roads","type":"line","source":"protomaps","source-layer":"roads","filter":["in",["get","kind"],["literal",["highway","major_road"]]],"paint":{"line-color":"#f3dfb7","line-width":["interpolate",["linear"],["zoom"],7,0.7,15,5.0]}},
            {"id":"minor-roads","type":"line","source":"protomaps","source-layer":"roads","filter":["==",["get","kind"],"minor_road"],"paint":{"line-color":"#fff4d7","line-width":["interpolate",["linear"],["zoom"],10,0.6,16,3.4]}},
            {"id":"trails","type":"line","source":"protomaps","source-layer":"roads","filter":["==",["get","kind"],"path"],"paint":{"line-color":["match",["get","kind_detail"],"track","#9b6c3d","steps","#a13a3a","#e36a32"],"line-width":["interpolate",["linear"],["zoom"],10,0.7,16,2.4],"line-dasharray":[2,1.5]}},
            {"id":"hut-camp-poi","type":"circle","source":"protomaps","source-layer":"pois","minzoom":10,"filter":["in",["get","kind"],["literal",["alpine_hut","wilderness_hut","shelter","camp_site","ranger_station"]]],"paint":{"circle-radius":["interpolate",["linear"],["zoom"],10,2.5,15,5.5],"circle-color":"#2e8b57","circle-stroke-color":"#ffffff","circle-stroke-width":1.2}},
            {"id":"water-poi","type":"circle","source":"protomaps","source-layer":"pois","minzoom":12,"filter":["in",["get","kind"],["literal",["drinking_water","water_point","spring"]]],"paint":{"circle-radius":4,"circle-color":"#168bb7","circle-stroke-color":"#ffffff","circle-stroke-width":1}},
            {"id":"peak-poi","type":"circle","source":"protomaps","source-layer":"pois","minzoom":9,"filter":["==",["get","kind"],"peak"],"paint":{"circle-radius":4.5,"circle-color":"#b44037","circle-stroke-color":"#ffffff","circle-stroke-width":1.2}},
            {"id":"place-labels","type":"symbol","source":"protomaps","source-layer":"places","minzoom":4,"layout":{"text-field":["coalesce",["get","name:ru"],["get","name:en"],["get","pgf:name:en"],["get","name"]],"text-font":["Noto Sans Regular"],"text-size":["interpolate",["linear"],["zoom"],5,11,12,15],"text-max-width":8,"text-letter-spacing":0.03,"text-allow-overlap":false},"paint":{"text-color":"#3d3934","text-halo-color":"#f4eedf","text-halo-width":1.5}},
            {"id":"trail-labels","type":"symbol","source":"protomaps","source-layer":"roads","minzoom":13,"filter":["==",["get","kind"],"path"],"layout":{"symbol-placement":"line","text-field":["coalesce",["get","name:ru"],["get","name:en"],["get","pgf:name:en"],["get","name"]],"text-font":["Noto Sans Regular"],"text-size":10.5,"text-allow-overlap":false},"paint":{"text-color":"#743b22","text-halo-color":"#f4eedf","text-halo-width":1.3}},
            {"id":"outdoor-poi-labels","type":"symbol","source":"protomaps","source-layer":"pois","minzoom":11,"filter":["in",["get","kind"],["literal",["peak","alpine_hut","wilderness_hut","shelter","camp_site","ranger_station","drinking_water","water_point","viewpoint"]]],"layout":{"text-field":["coalesce",["get","name:ru"],["get","name:en"],["get","pgf:name:en"],["get","name"]],"text-font":["Noto Sans Regular"],"text-size":11,"text-offset":[0,1.0],"text-anchor":"top","text-max-width":9,"text-allow-overlap":false},"paint":{"text-color":"#332f2b","text-halo-color":"#f6f0df","text-halo-width":1.5}}
          ]
        }
    """.trimIndent()
}
