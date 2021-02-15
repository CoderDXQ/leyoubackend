package com.leyou.search.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leyou.common.pojo.PageResult;
import com.leyou.item.bo.Sku;
import com.leyou.item.pojo.Brand;
import com.leyou.item.pojo.SpecParam;
import com.leyou.item.pojo.Spu;
import com.leyou.item.pojo.SpuDetail;
import com.leyou.search.client.BrandClient;
import com.leyou.search.client.CategoryClient;
import com.leyou.search.client.GoodsClient;
import com.leyou.search.client.SpecificationClient;
import com.leyou.search.pojo.Goods;
import com.leyou.search.pojo.SearchRequest;
import com.leyou.search.pojo.SearchResult;
import com.leyou.search.repository.GoodsRepository;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.LongTerms;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.aggregation.AggregatedPage;
import org.springframework.data.elasticsearch.core.query.FetchSourceFilter;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Service;


import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Duan Xiangqing
 * @version 1.0
 * @date 2021/2/10 1:19 上午
 */

@Service
public class SearchService {

    @Autowired
    private CategoryClient categoryClient;

    @Autowired
    private BrandClient brandClient;

    @Autowired
    private GoodsClient goodsClient;

    @Autowired
    private SpecificationClient specificationClient;

    @Autowired
    private GoodsRepository goodsRepository;

    //    json工具
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public Goods buildGoods(Spu spu) throws IOException {

        Goods goods = new Goods();

//        根据分类的id查询分类名称
        List<String> names = this.categoryClient.queryNamesByIds(Arrays.asList(spu.getCid1(), spu.getCid2(), spu.getCid3()));

//        查询品牌id查询品牌
        Brand brand = this.brandClient.queryBrandById(spu.getBrandId());

//        根据spuId查询所有的sku
        List<Sku> skus = this.goodsClient.querySkusBySpuId(spu.getId());
//        初始化一个价格集合，收集所有sku的价格
        List<Long> prices = new ArrayList<>();
//        收集sku的必要字段信息
        List<Map<String, Object>> skuMapList = new ArrayList<>();
        skus.forEach(sku -> {
            prices.add(sku.getPrice());

            Map<String, Object> map = new HashMap<>();

            map.put("id", sku.getId());
            map.put("title", sku.getTitle());
            map.put("price", sku.getPrice());
//            获取sku中的图片，数据库中的图片可能是多张 但是只放第一张
            map.put("image", StringUtils.isBlank(sku.getImages()) ? "" : StringUtils.split(sku.getImages(), ",")[0]);

            skuMapList.add(map);
        });

//        根据spu中的cid3查询出所有的搜索规格参数
        List<SpecParam> params = this.specificationClient.queryParams(null, spu.getCid3(), false, null);

//        根据spuId查询spuDetail
        SpuDetail spuDetail = this.goodsClient.querySpuDetailBySpuId(spu.getId());
//        把通用的规格参数值进行反序列化放到genericSpecMap中
        Map<String, Object> genericSpecMap = MAPPER.readValue(spuDetail.getGenericSpec(), new TypeReference<Map<String, Object>>() {
        });
//        把特殊的规格参数值进行反序列化
        Map<String, List<Object>> specialSpecMap = MAPPER.readValue(spuDetail.getSpecialSpec(), new TypeReference<Map<String, List<Object>>>() {
        });

        Map<String, Object> specs = new HashMap<>();
        params.forEach(param -> {

//            判断规格参数的类型 从genericSpecMap获取规格参数值
            if (param.getGeneric()) {
//                如果是通用类型的参数 从genericSpecMap获取规格参数值
                String value = genericSpecMap.get(param.getId().toString()).toString();
//                判断是否是数值类型，如果是就应该返回一个区间
                if (param.getNumeric()) {
                    value = chooseSegment(value, param);
                }

                specs.put(param.getName(), value);
            } else {
//                如果是特殊的规格参数 直接从specialSpecMap中获取值
                List<Object> value = specialSpecMap.get(param.getId().toString());
                specs.put(param.getName(), value);
            }
        });


        goods.setId(spu.getId());
        goods.setCid1(spu.getCid1());
        goods.setCid2(spu.getCid2());
        goods.setCid3(spu.getCid3());
        goods.setBrandId(spu.getBrandId());
        goods.setCreateTime(spu.getCreateTime());
        goods.setSubTitle(spu.getSubTitle());

//        拼接all字段 需要分类名称以及品牌名称
        goods.setAll(spu.getTitle() + " " + StringUtils.join(names, " ") + " " + null);
//        获取spu下的所有sku价格
        goods.setPrice(prices);
//        获取spu下的所有sku 并转化成json字符串
        goods.setSkus(MAPPER.writeValueAsString(skuMapList));
//        获取所有查询的规格参数{name:value}
        goods.setSpecs(specs);

        return goods;
    }


    //    划分区间
    private String chooseSegment(String value, SpecParam p) {

//        p的格式如下：0-5，6-10，10-
        double val = NumberUtils.toDouble(value);
        String result = "其它";
        // 保存数值段
        for (String segment : p.getSegments().split(",")) {
            String[] segs = segment.split("-");
            // 获取数值范围
            double begin = NumberUtils.toDouble(segs[0]);
            double end = Double.MAX_VALUE;
            if (segs.length == 2) {
                end = NumberUtils.toDouble(segs[1]);
            }
            // 判断是否在范围内
            if (val >= begin && val < end) {
                if (segs.length == 1) {
                    result = segs[0] + p.getUnit() + "以上";
                } else if (begin == 0) {
                    result = segs[1] + p.getUnit() + "以下";
                } else {
                    result = segment + p.getUnit();
                }
                break;
            }
        }
        return result;
    }

    public SearchResult search(SearchRequest request) {

//        查询关键字为空
        if (StringUtils.isBlank(request.getKey())) {
            return null;
        }

//        自定义查询构建器
        NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder();

//        添加查询条件
        queryBuilder.withQuery(QueryBuilders.matchQuery("all", request.getKey()).operator(Operator.AND));
//        添加分页，分页页码从0开始
        queryBuilder.withPageable(PageRequest.of(request.getPage() - 1, request.getSize()));
//        添加结果集过滤
        queryBuilder.withSourceFilter(new FetchSourceFilter(new String[]{"id", "skus", "subTitle"}, null));

//        添加分类和品牌聚合
        String catetegoryAggName = "categories";
        String brandAggName = "brands";
        queryBuilder.addAggregation(AggregationBuilders.terms(catetegoryAggName).field("cid3"));
        queryBuilder.addAggregation(AggregationBuilders.terms(brandAggName).field("brandId"));

//        执行查询，获取结果集
        AggregatedPage<Goods> goodsPage = (AggregatedPage<Goods>) this.goodsRepository.search(queryBuilder.build());

        //        获取聚合结果集并解析
        List<Map<String, Object>> categories = getCategoryAggResult(goodsPage.getAggregation(catetegoryAggName));
        List<Brand> brands = getBrandAggResult(goodsPage.getAggregation(brandAggName));

//        int totalpages=goodsPage.getTotalPages();
        return new SearchResult(goodsPage.getTotalElements(), Long.valueOf(goodsPage.getTotalPages()), goodsPage.getContent(), null, null);

    }

    private List<Map<String, Object>> getCategoryAggResult(Aggregation aggregation) {
        LongTerms terms = (LongTerms) aggregation;

        return terms.getBuckets().stream().map(bucket -> {
            Map<String, Object> map = new HashMap<>();
            Long id = bucket.getKeyAsNumber().longValue();
            List<String> names = this.categoryClient.queryNamesByIds(Arrays.asList(id));
            map.put("id", id);
            map.put("name", names.get(0));
            return map;
        }).collect(Collectors.toList());

    }

    private List<Brand> getBrandAggResult(Aggregation aggregation) {
        LongTerms terms = (LongTerms) aggregation;

        return terms.getBuckets().stream().map(bucket -> {
            return this.brandClient.queryBrandById(bucket.getKeyAsNumber().longValue());
        }).collect(Collectors.toList());
    }


}