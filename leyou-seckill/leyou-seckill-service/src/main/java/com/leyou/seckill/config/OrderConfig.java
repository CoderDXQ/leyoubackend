package com.leyou.seckill.config;

import feign.Feign;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.Enumeration;


/**
 * @author Duan Xiangqing
 * @version 1.0
 * @date 2021/2/23 11:33 下午
 */

//order服务配置，转发header 将header中的信息转发给订单微服务
@Configuration
public class OrderConfig {
    @Bean
    public Feign.Builder feignBuilder(){
        return Feign.builder().requestInterceptor(requestTemplate -> {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            HttpServletRequest request = attributes.getRequest();
            Enumeration<String> headerNames = request.getHeaderNames();
            if (headerNames != null) {
                while (headerNames.hasMoreElements()) {
                    String name = headerNames.nextElement();
                    String values = request.getHeader(name);
                    requestTemplate.header(name, values);
                }
            }
        });
    }
}
