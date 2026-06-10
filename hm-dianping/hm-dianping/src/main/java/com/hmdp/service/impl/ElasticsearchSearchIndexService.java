package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.dto.ContentNoteDTO;
import com.hmdp.dto.ContentSearchResult;
import com.hmdp.dto.ContentTrendDTO;
import com.hmdp.dto.SearchIndexRebuildResult;
import com.hmdp.entity.Blog;
import com.hmdp.entity.ContentTopic;
import com.hmdp.entity.MallProduct;
import com.hmdp.entity.Shop;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IMallProductService;
import com.hmdp.service.IShopService;
import com.hmdp.service.SearchIndexService;
import com.hmdp.mapper.ContentTopicMapper;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.Resource;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ElasticsearchSearchIndexService implements SearchIndexService {

    private static final String TYPE_NOTE = "note";
    private static final String TYPE_PRODUCT = "product";
    private static final String TYPE_SHOP = "shop";
    private static final String TYPE_TOPIC = "topic";

    @Value("${hmdp.search.elasticsearch.enabled:false}")
    private boolean enabled;

    @Value("${hmdp.search.elasticsearch.uris:http://localhost:9200}")
    private String uris;

    @Value("${hmdp.search.elasticsearch.index:hmdp_search}")
    private String indexName;

    @Resource
    private IBlogService blogService;

    @Resource
    private IMallProductService mallProductService;

    @Resource
    private IShopService shopService;

    @Resource
    private ContentTopicMapper contentTopicMapper;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public ElasticsearchSearchIndexService(RestTemplateBuilder builder, ObjectMapper objectMapper) {
        this.restTemplate = builder
                .connectTimeout(Duration.ofSeconds(2))
                .readTimeout(Duration.ofSeconds(5))
                .build();
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean isAvailable() {
        if (!enabled) {
            return false;
        }
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(endpoint("/"), String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    public ContentSearchResult search(String keyword, int pageNo) {
        if (!enabled || StrUtil.isBlank(keyword)) {
            return null;
        }
        try {
            JsonNode root = exchangeJson("/" + indexName + "/_search", HttpMethod.POST, searchBody(keyword, pageNo));
            List<String> noteIds = new ArrayList<>();
            List<String> productIds = new ArrayList<>();
            List<String> shopIds = new ArrayList<>();
            List<ContentTrendDTO> topics = new ArrayList<>();
            JsonNode hits = root.path("hits").path("hits");
            if (hits.isArray()) {
                for (JsonNode hit : hits) {
                    JsonNode source = hit.path("_source");
                    String type = source.path("type").asText("");
                    String sourceId = source.path("sourceId").asText("");
                    if (TYPE_NOTE.equals(type)) {
                        noteIds.add(sourceId);
                    } else if (TYPE_PRODUCT.equals(type)) {
                        productIds.add(sourceId);
                    } else if (TYPE_SHOP.equals(type)) {
                        shopIds.add(sourceId);
                    } else if (TYPE_TOPIC.equals(type)) {
                        topics.add(new ContentTrendDTO(source.path("title").asText(keyword), source.path("heat").asLong(1L)));
                    }
                }
            }

            List<ContentNoteDTO> notes = notesByIds(toLongIds(noteIds));
            List<ContentNoteDTO> videos = notes.stream().filter(this::isVideoNote).toList();
            List<MallProduct> products = productsByIds(toLongIds(productIds));
            List<Shop> shops = shopsByIds(toLongIds(shopIds));

            ContentSearchResult result = new ContentSearchResult();
            result.setQuery(keyword);
            result.setNotes(notes);
            result.setVideos(videos);
            result.setProducts(products);
            result.setProductNotes(Map.of());
            result.setShops(shops);
            result.setTopics(topics.stream().limit(12).toList());
            result.setRelatedQueries(relatedQueries(keyword, notes, products, shops, topics));
            result.setSummary("已使用 Elasticsearch 按相关度、热度和新鲜度综合排序。\"" + keyword + "\" 命中 "
                    + notes.size() + " 条笔记、" + products.size() + " 个商品、" + shops.size() + " 个商家。");
            return result;
        } catch (RuntimeException e) {
            log.warn("Elasticsearch search failed, fallback to MySQL. keyword={}", keyword, e);
            return null;
        }
    }

    @Override
    public SearchIndexRebuildResult rebuildAll() {
        SearchIndexRebuildResult result = new SearchIndexRebuildResult()
                .setEnabled(enabled)
                .setAvailable(isAvailable());
        if (!enabled) {
            return result.setMessage("Elasticsearch is disabled");
        }
        if (!result.isAvailable()) {
            return result.setMessage("Elasticsearch is not available");
        }
        createIndex();

        List<Map<String, Object>> docs = new ArrayList<>();
        List<Blog> blogs = blogService.query().eq("status", 0).list();
        for (Blog blog : blogs) {
            docs.add(blogDocument(blog));
        }
        List<MallProduct> products = mallProductService.query().eq("status", 1).list();
        for (MallProduct product : products) {
            docs.add(productDocument(product));
        }
        List<Shop> shops = shopService.list();
        for (Shop shop : shops) {
            docs.add(shopDocument(shop));
        }
        List<ContentTopic> topics = contentTopicMapper.selectList(
                new QueryWrapper<ContentTopic>().orderByDesc("heat").last("limit 500"));
        for (ContentTopic topic : topics) {
            docs.add(topicDocument(topic));
        }
        bulkIndex(docs);
        return result
                .setNotes(blogs.size())
                .setProducts(products.size())
                .setShops(shops.size())
                .setTopics(topics.size())
                .setMessage("ok");
    }

    @Override
    public void indexBlog(Blog blog) {
        if (!enabled || blog == null || blog.getId() == null || !isAvailable()) {
            return;
        }
        try {
            createIndex();
            exchangeJson("/" + indexName + "/_doc/" + documentId(TYPE_NOTE, blog.getId()),
                    HttpMethod.PUT, blogDocument(blog));
        } catch (RuntimeException e) {
            log.warn("Index blog failed. blogId={}", blog.getId(), e);
        }
    }

    @Override
    public void deleteBlog(Long blogId) {
        if (!enabled || blogId == null || !isAvailable()) {
            return;
        }
        try {
            restTemplate.exchange(endpoint("/" + indexName + "/_doc/" + documentId(TYPE_NOTE, blogId)),
                    HttpMethod.DELETE, HttpEntity.EMPTY, String.class);
        } catch (RestClientException e) {
            log.warn("Delete blog index failed. blogId={}", blogId, e);
        }
    }

    private void createIndex() {
        if (indexExists()) {
            return;
        }
        exchangeJson("/" + indexName, HttpMethod.PUT, Map.of(
                "settings", Map.of(
                        "number_of_shards", 1,
                        "number_of_replicas", 0,
                        "analysis", Map.of(
                                "analyzer", Map.of(
                                        "hmdp_text", Map.of(
                                                "type", "custom",
                                                "tokenizer", "standard",
                                                "filter", List.of("lowercase"))
                                )
                        )
                ),
                "mappings", Map.of(
                        "properties", Map.ofEntries(
                                Map.entry("type", Map.of("type", "keyword")),
                                Map.entry("sourceId", Map.of("type", "long")),
                                Map.entry("title", Map.of("type", "text", "analyzer", "hmdp_text")),
                                Map.entry("content", Map.of("type", "text", "analyzer", "hmdp_text")),
                                Map.entry("tags", Map.of("type", "text", "analyzer", "hmdp_text")),
                                Map.entry("category", Map.of("type", "keyword")),
                                Map.entry("suggest", Map.of("type", "text", "analyzer", "hmdp_text")),
                                Map.entry("heat", Map.of("type", "long")),
                                Map.entry("createdAt", Map.of("type", "date"))
                        )
                )
        ));
    }

    private boolean indexExists() {
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    endpoint("/" + indexName), HttpMethod.HEAD, HttpEntity.EMPTY, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (RuntimeException e) {
            return false;
        }
    }

    private void bulkIndex(List<Map<String, Object>> docs) {
        if (docs.isEmpty()) {
            return;
        }
        StringBuilder payload = new StringBuilder();
        for (Map<String, Object> doc : docs) {
            payload.append(toJson(Map.of("index", Map.of(
                    "_index", indexName,
                    "_id", documentId(String.valueOf(doc.get("type")), doc.get("sourceId"))))))
                    .append('\n');
            payload.append(toJson(doc)).append('\n');
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/x-ndjson"));
        restTemplate.postForEntity(endpoint("/_bulk?refresh=true"), new HttpEntity<>(payload.toString(), headers), String.class);
    }

    private Map<String, Object> searchBody(String keyword, int pageNo) {
        int size = SystemConstants.MAX_PAGE_SIZE * 4;
        int from = Math.max(0, pageNo - 1) * size;
        Map<String, Object> query = Map.of(
                "function_score", Map.of(
                        "query", Map.of(
                                "multi_match", Map.of(
                                        "query", keyword,
                                        "fields", List.of("title^5", "tags^4", "content^2", "category^2", "suggest"),
                                        "type", "best_fields",
                                        "fuzziness", "AUTO"
                                )
                        ),
                        "field_value_factor", Map.of(
                                "field", "heat",
                                "factor", 0.08,
                                "modifier", "log1p",
                                "missing", 1
                        ),
                        "boost_mode", "sum",
                        "score_mode", "sum"
                )
        );
        return Map.of(
                "from", from,
                "size", size,
                "query", query,
                "sort", List.of(Map.of("_score", "desc"), Map.of("createdAt", Map.of("order", "desc", "missing", "_last")))
        );
    }

    private Map<String, Object> blogDocument(Blog blog) {
        return baseDocument(TYPE_NOTE, blog.getId(), blog.getTitle(), blog.getContent(), blog.getTags(),
                StrUtil.blankToDefault(blog.getContentType(), TYPE_NOTE),
                heat(blog.getLiked(), blog.getComments(), 0),
                blog.getCreateTime());
    }

    private Map<String, Object> productDocument(MallProduct product) {
        return baseDocument(TYPE_PRODUCT, product.getId(), product.getTitle(), product.getSubTitle(),
                StrUtil.blankToDefault(product.getSpecSummary(), ""),
                StrUtil.blankToDefault(product.getCategory(), TYPE_PRODUCT),
                heat(product.getSold(), product.getReviewCount(), product.getFavoriteCount()),
                product.getCreateTime());
    }

    private Map<String, Object> shopDocument(Shop shop) {
        return baseDocument(TYPE_SHOP, shop.getId(), shop.getName(), shop.getAddress(), shop.getArea(),
                TYPE_SHOP,
                heat(shop.getSold(), shop.getComments(), shop.getScore()),
                shop.getCreateTime());
    }

    private Map<String, Object> topicDocument(ContentTopic topic) {
        return baseDocument(TYPE_TOPIC, topic.getId(), topic.getKeyword(), topic.getKeyword(), "",
                TYPE_TOPIC,
                topic.getHeat() == null ? 1L : topic.getHeat(),
                topic.getCreateTime());
    }

    private Map<String, Object> baseDocument(String type, Object sourceId, String title, String content, String tags,
                                             String category, long heat, LocalDateTime createdAt) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("type", type);
        doc.put("sourceId", sourceId);
        doc.put("title", StrUtil.blankToDefault(title, ""));
        doc.put("content", StrUtil.blankToDefault(content, ""));
        doc.put("tags", StrUtil.blankToDefault(tags, ""));
        doc.put("category", StrUtil.blankToDefault(category, ""));
        doc.put("suggest", StrUtil.join(" ", doc.get("title"), doc.get("content"), doc.get("tags"), doc.get("category")));
        doc.put("heat", heat);
        doc.put("createdAt", createdAt == null
                ? "1970-01-01T00:00:00Z"
                : createdAt.atZone(ZoneId.systemDefault()).toInstant().toString());
        return doc;
    }

    private long heat(Number first, Number second, Number third) {
        return Math.max(1L, longValue(first) * 3 + longValue(second) * 2 + longValue(third));
    }

    private long longValue(Number number) {
        return number == null ? 0L : number.longValue();
    }

    private List<ContentNoteDTO> notesByIds(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        List<Blog> blogs = blogService.query().in("id", ids).eq("status", 0).list();
        Map<Long, Blog> map = blogs.stream().collect(Collectors.toMap(Blog::getId, Function.identity(), (a, b) -> a));
        List<ContentNoteDTO> result = new ArrayList<>();
        for (Long id : ids) {
            Blog blog = map.get(id);
            if (blog == null) {
                continue;
            }
            ContentNoteDTO dto = new ContentNoteDTO();
            dto.setId(blog.getId());
            dto.setNoteId(blog.getId());
            dto.setShopId(blog.getShopId());
            dto.setUserId(blog.getUserId());
            dto.setAuthorId(blog.getUserId());
            dto.setTitle(blog.getTitle());
            dto.setImages(blog.getImages());
            dto.setCover(firstImage(blog.getImages()));
            dto.setVideoUrl(blog.getVideoUrl());
            dto.setContentType(blog.getContentType());
            dto.setMediaType(blog.getContentType());
            dto.setTags(blog.getTags());
            dto.setContent(blog.getContent());
            dto.setLiked(blog.getLiked() == null ? 0 : blog.getLiked());
            dto.setLikedCount(dto.getLiked());
            dto.setComments(blog.getComments() == null ? 0 : blog.getComments());
            dto.setCommentCount(dto.getComments());
            dto.setStatus(blog.getStatus() == null ? 0 : blog.getStatus());
            dto.setCreateTime(blog.getCreateTime());
            result.add(dto);
        }
        return result;
    }

    private List<MallProduct> productsByIds(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, MallProduct> map = mallProductService.listByIds(ids).stream()
                .filter(product -> product.getStatus() == null || product.getStatus() == 1)
                .collect(Collectors.toMap(MallProduct::getId, Function.identity(), (a, b) -> a));
        return ids.stream().map(map::get).filter(item -> item != null).toList();
    }

    private List<Shop> shopsByIds(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, Shop> map = shopService.listByIds(ids).stream()
                .collect(Collectors.toMap(Shop::getId, Function.identity(), (a, b) -> a));
        return ids.stream().map(map::get).filter(item -> item != null).toList();
    }

    private boolean isVideoNote(ContentNoteDTO note) {
        String type = StrUtil.blankToDefault(note.getContentType(), "");
        return "VIDEO".equals(type);
    }

    private String firstImage(String images) {
        if (StrUtil.isBlank(images)) {
            return "";
        }
        return StrUtil.split(images, ',').stream().findFirst().orElse("");
    }

    private List<Long> toLongIds(List<String> ids) {
        return ids.stream().map(this::parseLong).filter(id -> id != null).distinct().toList();
    }

    private Long parseLong(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private List<String> relatedQueries(String keyword, List<ContentNoteDTO> notes, List<MallProduct> products,
                                        List<Shop> shops, List<ContentTrendDTO> topics) {
        Set<String> values = new LinkedHashSet<>();
        values.add(keyword);
        notes.stream().map(ContentNoteDTO::getTags).filter(StrUtil::isNotBlank)
                .flatMap(tags -> StrUtil.split(tags, ',').stream()).map(String::trim).filter(StrUtil::isNotBlank)
                .forEach(values::add);
        products.stream().map(MallProduct::getCategory).filter(StrUtil::isNotBlank).forEach(values::add);
        shops.stream().map(Shop::getArea).filter(StrUtil::isNotBlank).forEach(values::add);
        topics.stream().map(ContentTrendDTO::getKeyword).filter(StrUtil::isNotBlank).forEach(values::add);
        return values.stream().filter(value -> !value.equals(keyword)).limit(8).toList();
    }

    private JsonNode exchangeJson(String path, HttpMethod method, Object body) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<String> response = restTemplate.exchange(endpoint(path), method,
                    new HttpEntity<>(toJson(body), headers), String.class);
            return objectMapper.readTree(response.getBody() == null ? "{}" : response.getBody());
        } catch (Exception e) {
            throw new IllegalStateException("Elasticsearch request failed: " + path, e);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("JSON encode failed", e);
        }
    }

    private String documentId(String type, Object id) {
        return type + "-" + id;
    }

    private URI endpoint(String path) {
        String base = StrUtil.split(uris, ',').stream()
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .findFirst()
                .orElse("http://localhost:9200");
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return URI.create(base + (path.startsWith("/") ? path : "/" + path));
    }
}
