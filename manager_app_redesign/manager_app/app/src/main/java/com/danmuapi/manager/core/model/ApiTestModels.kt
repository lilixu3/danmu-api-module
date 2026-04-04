package com.danmuapi.manager.core.model

enum class ApiDebugFieldType {
    Text,
    Select,
    Json,
}

enum class ApiDebugFieldLocation {
    Path,
    Query,
    Body,
    RawBody,
}

data class ApiDebugFieldDefinition(
    val key: String,
    val label: String,
    val location: ApiDebugFieldLocation,
    val type: ApiDebugFieldType = ApiDebugFieldType.Text,
    val required: Boolean = false,
    val placeholder: String = "",
    val options: List<String> = emptyList(),
)

data class ApiDebugPreset(
    val key: String,
    val title: String,
    val method: String,
    val path: String,
    val subtitle: String,
    val fields: List<ApiDebugFieldDefinition> = emptyList(),
)

data class ApiTestAnimeItem(
    val animeId: String,
    val animeTitle: String,
    val episodeCount: Int? = null,
)

data class ApiTestEpisodeItem(
    val episodeId: String,
    val episodeNumber: String,
    val episodeTitle: String,
)

data class DanmuTestMatchSummary(
    val animeTitle: String,
    val episodeTitle: String,
    val episodeId: String,
    val matchCount: Int? = null,
)

data class DanmuPreviewItem(
    val timeLabel: String,
    val modeLabel: String,
    val colorHex: String,
    val text: String,
)

data class DanmuStatsSummary(
    val commentCount: Int,
    val durationLabel: String,
    val averageDensityLabel: String,
    val hotMomentLabel: String,
    val modeBreakdownLabel: String,
)

data class DanmuTestResult(
    val title: String,
    val matchSummary: DanmuTestMatchSummary? = null,
    val stats: DanmuStatsSummary,
    val preview: List<DanmuPreviewItem> = emptyList(),
    val rawBody: String,
)

enum class ManualDanmuStep {
    Search,
    Anime,
    Episodes,
    Result,
}

val ApiDebugPresetCatalog: List<ApiDebugPreset> = listOf(
    ApiDebugPreset(
        key = "searchAnime",
        title = "搜索动漫",
        method = "GET",
        path = "/api/v2/search/anime",
        subtitle = "按关键字搜索动漫",
        fields = listOf(
            ApiDebugFieldDefinition(
                key = "keyword",
                label = "关键词",
                location = ApiDebugFieldLocation.Query,
                required = true,
                placeholder = "示例: 生万物",
            ),
        ),
    ),
    ApiDebugPreset(
        key = "searchEpisodes",
        title = "搜索剧集",
        method = "GET",
        path = "/api/v2/search/episodes",
        subtitle = "按动漫名和集数搜索剧集",
        fields = listOf(
            ApiDebugFieldDefinition(
                key = "anime",
                label = "动漫名称",
                location = ApiDebugFieldLocation.Query,
                required = true,
                placeholder = "示例: 生万物",
            ),
            ApiDebugFieldDefinition(
                key = "episode",
                label = "集数",
                location = ApiDebugFieldLocation.Query,
                placeholder = "示例: 1 或 movie",
            ),
        ),
    ),
    ApiDebugPreset(
        key = "matchAnime",
        title = "匹配动漫",
        method = "POST",
        path = "/api/v2/match",
        subtitle = "按文件名自动匹配动漫和剧集",
        fields = listOf(
            ApiDebugFieldDefinition(
                key = "fileName",
                label = "文件名",
                location = ApiDebugFieldLocation.Body,
                required = true,
                placeholder = "示例: 生万物 S02E08",
            ),
        ),
    ),
    ApiDebugPreset(
        key = "getBangumi",
        title = "获取番剧详情",
        method = "GET",
        path = "/api/v2/bangumi/:animeId",
        subtitle = "查看番剧详细信息与剧集列表",
        fields = listOf(
            ApiDebugFieldDefinition(
                key = "animeId",
                label = "动漫 ID",
                location = ApiDebugFieldLocation.Path,
                required = true,
                placeholder = "示例: 236379",
            ),
        ),
    ),
    ApiDebugPreset(
        key = "getComment",
        title = "获取弹幕",
        method = "GET",
        path = "/api/v2/comment/:commentId",
        subtitle = "按弹幕 ID 获取完整弹幕内容",
        fields = listOf(
            ApiDebugFieldDefinition(
                key = "commentId",
                label = "弹幕 ID",
                location = ApiDebugFieldLocation.Path,
                required = true,
                placeholder = "示例: 10009",
            ),
            ApiDebugFieldDefinition(
                key = "format",
                label = "格式",
                location = ApiDebugFieldLocation.Query,
                type = ApiDebugFieldType.Select,
                placeholder = "默认 json",
                options = listOf("json", "xml"),
            ),
            ApiDebugFieldDefinition(
                key = "duration",
                label = "附带时长",
                location = ApiDebugFieldLocation.Query,
                type = ApiDebugFieldType.Select,
                options = listOf("true", "false"),
            ),
            ApiDebugFieldDefinition(
                key = "segmentflag",
                label = "分片标志",
                location = ApiDebugFieldLocation.Query,
                type = ApiDebugFieldType.Select,
                options = listOf("true", "false"),
            ),
        ),
    ),
    ApiDebugPreset(
        key = "getSegmentComment",
        title = "获取分片弹幕",
        method = "POST",
        path = "/api/v2/segmentcomment",
        subtitle = "直接提交分片请求体调试接口",
        fields = listOf(
            ApiDebugFieldDefinition(
                key = "format",
                label = "格式",
                location = ApiDebugFieldLocation.Query,
                type = ApiDebugFieldType.Select,
                options = listOf("json", "xml"),
            ),
            ApiDebugFieldDefinition(
                key = "bodyJson",
                label = "请求体",
                location = ApiDebugFieldLocation.RawBody,
                type = ApiDebugFieldType.Json,
                required = true,
                placeholder = """{
  "type": "qq",
  "segment_start": 0,
  "segment_end": 30000,
  "url": "https://dm.video.qq.com/..."
}""",
            ),
        ),
    ),
)
