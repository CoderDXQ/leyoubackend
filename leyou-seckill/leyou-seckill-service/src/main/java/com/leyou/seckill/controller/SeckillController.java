package com.leyou.seckill.controller;

import com.leyou.common.pojo.UserInfo;
import com.leyou.item.bo.SeckillParameter;
import com.leyou.item.pojo.SeckillGoods;
import com.leyou.seckill.access.AccessLimit;
import com.leyou.seckill.client.GoodsClient;
import com.leyou.seckill.interceptor.LoginInterceptor;
import com.leyou.seckill.service.SeckillService;


import com.leyou.seckill.vo.SeckillMessage;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.text.ParseException;
import java.util.*;

/**
 * @author Duan Xiangqing
 * @version 1.0
 * @date 2021/2/23 11:34 下午
 */

//@Controller
@RestController
@RequestMapping
public class SeckillController/* implements InitializingBean */{

    @Autowired
    private SeckillService seckillService;

        @Autowired
//    @Resource
    public GoodsClient goodsClient;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    //ES中的索引前缀
    private static final String KEY_PREFIX = "leyou:seckill:stock";

    //    localOverMap里存的是库存不足的商品
    private Map<Long, Boolean> localOverMap = new HashMap<>();

    /**
     * 系统初始化，初始化秒杀商品数量
     *
     * @throws Exception
     */
    /*
    //实现InitializingBean接口需要重写这个方法
    @Override
    public void afterPropertiesSet() throws Exception {

//        查询可以秒杀的商品
        List<SeckillGoods> seckillGoods = this.goodsClient.querySeckillGoods();
        if (seckillGoods == null || seckillGoods.size() == 0) {
            System.out.println("这里出错了");
            return;
        }

//        原来的逻辑：从Redis中获取初始值 查询Redis
//        BoundHashOperations<String, Object, Object> hashOperations = this.stringRedisTemplate.boundHashOps(KEY_PREFIX);
//        if (hashOperations.hasKey(KEY_PREFIX)) {
//            hashOperations.entries().forEach((m, n) -> localOverMap.put(Long.parseLong(m.toString()), false));
//        }

//        新逻辑:从商品服务通过查询数据库获得初始数据写入Redis
        BoundHashOperations<String, Object, Object> hashOperations = this.stringRedisTemplate.boundHashOps(KEY_PREFIX);
        if (hashOperations.hasKey(KEY_PREFIX)) {
            hashOperations.delete(KEY_PREFIX);
        }
        seckillGoods.forEach(goods -> {
            hashOperations.put(goods.getSkuId().toString(), goods.getStock().toString());
        });
    }

     */

    //    创建秒杀订单
    //单个商品的订单
    //    ？？？这里还需要优化
    @PostMapping("seck")
    public ResponseEntity<Long> seckillOrder(@RequestBody SeckillGoods seckillGoods) {
//        创建订单
        Long id = this.seckillService.createOrder(seckillGoods);

//        判断是否秒杀成功
        if (id == null) {
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
        }
        return ResponseEntity.ok(id);
    }

    //    创建秒杀订单
    @PostMapping("/{path}/seck")
    public ResponseEntity<String> seckillOrder(@PathVariable("path") String path, SeckillGoods seckillGoods) {
        String result = "排队中";
        UserInfo userInfo = LoginInterceptor.getLoginUser();

//        验证路径 从Redis中查询路径
        boolean check = this.seckillService.checkSeckillPath(seckillGoods.getId(), userInfo.getId(), path);
        if (!check) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

//        内存标记 减少redis时间 可以认为localOverMap是Redis的缓存 查询localOverMap查询不到再去查询Redis
//        localOverMap里存的是库存不足的商品
        boolean over = localOverMap.get(seckillGoods.getSkuId());
        if (over) {
            return ResponseEntity.ok(result);
        }

//        读取库存 减一后更新缓存 默认每件商品买的数量都是1
        BoundHashOperations<String, Object, Object> hashOperations = this.stringRedisTemplate.boundHashOps(KEY_PREFIX);
//        先更新Redis缓存  这个方法末尾再发送消息到消息队列，监听器监听到消息后更新数据库
        Long stock = hashOperations.increment(seckillGoods.getSkuId().toString(), -1);

//        库存不足直接返回
        if (stock < 0) {
            localOverMap.put(seckillGoods.getSkuId(), true);
            return ResponseEntity.ok(result);
        }

//        库存充足 直接入队
        SeckillMessage seckillMessage = new SeckillMessage(userInfo, seckillGoods);
//        发送秒杀消息到消息队列
        this.seckillService.sendMessage(seckillMessage);

        return ResponseEntity.ok(result);
    }


    @PostMapping("addSeckill")
    public ResponseEntity<Boolean> addSeckillGoods(@RequestBody SeckillParameter seckillParameter) throws ParseException {
        if (seckillParameter != null){
            this.seckillService.addSeckillGoods(seckillParameter);
        }else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        return ResponseEntity.ok().build();
    }

    //    根据userId查询订单号
    @GetMapping("orderId")
    public ResponseEntity<Long> checkSeckillOrder(Long userId) {
        Long result = this.seckillService.checkSeckillOrder(userId);
        if (result == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(result);
    }

    //    自定义注解
    @AccessLimit(seconds = 20, maxCount = 5, needLogin = true)
    @GetMapping("get_path/{goodsId}")
    public ResponseEntity<String> getSeckillPath(@PathVariable("goodsId") Long goodsId) {
        UserInfo userInfo = LoginInterceptor.getLoginUser();
        if (userInfo == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String str = this.seckillService.createPath(goodsId, userInfo.getId());
        if (StringUtils.isEmpty(str)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(str);
    }

    @GetMapping("list")
    public ResponseEntity<List<SeckillGoods>> querySeckillGoods(){
        List<SeckillGoods> list = this.seckillService.querySeckillGoods();
        if (list == null || list.size() < 0){
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(list);
    }


}
