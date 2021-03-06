
# 商城

---

## Question和以后规划的功能:

1. 大部分功能只有查询，尝试把增删改查补全  可能要写mapper.xml或者写mapper接口也行

2. 完成作业中的功能 https://github.com/ohmyray/leyou

3. Spring Cloud Config 集中配置中心的设计与实现

4. 使用Jenkins部署和持续集成

6. 秒杀升级  使用更多策略解决更多问题

-----

## 待测试的功能

#### 测试时需要注意的问题：

权限问题：调用接口登陆 localhost:8087/accredit

        然后调用接口鉴权 http://api.leyou.com/api/auth/accredit?username=liuyang&password=123456
        
        这个最好也调一下（可能是获得Cookie中的TOKEN） http://api.leyou.com/api/auth/verify
        

1. https://blog.csdn.net/lyj2018gyq/article/details/84075983

2. https://blog.csdn.net/lyj2018gyq/article/details/84261377

3. https://blog.csdn.net/lyj2018gyq/article/details/84109151

4. https://blog.csdn.net/lyj2018gyq/article/details/84679844
   
   这个测试需要调用接口发单然后逐步调用接口更新订单状态，然后按网页中的图文进行测试。




##  项目启动  
相关安装和配置需要自己来完成，部分服务在120机器的CentOS7.6系统的leyou账户上，启动步骤如下：

1. 启动nginx 

   在`/usr/local/etc/nginx`下执行`nginx`。
   
2. 启动ES

   在部署ES的机器上的`/home/leyou/elasticsearch/bin`路径下执行`./elasticsearch`。
   
   管理界面：http://localhost:5601/

3. 启动Kibana

   在`Kibana的解压文件夹`下启动二进制文件即可，访问 打开管理界面。
   
4. 启动数据库

   依次执行`nohup mysqld &`和`mysql -u root -p`。

5. 启动服务

   在IDEA中按下`command`+`8`，然后一次启动所有的微服务即可，注意需要先启动Eureka服务，然后启动其他服务。
   
6. 启动leyou-portal

   在IDEA的Terminal中执行`live-server --port=9002 `。

7. 启动leyou-manage-web

   在IDEA的Terminal中执行`npm run dev`。
   
8. 配置好hosts

   切换到root用户然后在`/private/etc`下使用`vim hosts`进行修改。
   添加以下内容：
   
   `127.0.0.1 api.leyou.com`
   
   `127.0.0.1 manage.leyou.com`
    
   `127.0.0.1 www.leyou.com`
    
   `10.108.163.120 image.leyou.com`
    
    然后在`/usr/local/etc/nginx`下执行`nginx -s reload`以重新启动nginx。

9. 启动消息队列RabbitMQ
   
   `service rabbitmq-server start`
   
   `rabbitmq-plugins enable rabbitmq_management`
   
   `service rabbitmq-server restart`
   
   管理界面：http://10.108.163.120:15672/
   
10. 启动Redis

    在部署了Redis的机器上使用`redis-server start`启动服务。
    
    或者在`/usr/local/bin`下执行二进制文件,需要以配置文件启动才能进行远程连接`nohup redis-server /usr/local/leyou/redis/redis.conf &`。
    
    
11. 启动MongoDB

    依次执行`whereis mongod`,然后cd到第一个路径，然后执行 `./mongod`以启动MongoDB服务。
    
    但是推荐`service mongod start`。
    
    依次执行`whereis mongo`,然后直接执行第一个路径即可。
   
    
 ## 其他
 
 1. 本项目运行过程中所有生成或者存储的文件路径均与源工程同级。
 
 2. 文档地址：https://github.com/ohmyray/leyou
 
    项目详细介绍：https://blog.csdn.net/lyj2018gyq/category_7963560.html
    
    参考代码（包含前后端和portal）：https://github.com/mundane799699/leyou
    
    带秒杀的参考代码：https://github.com/lyj8330328/leyou
 
    配置单参考：https://github.com/CoderDXQ/leyou-config

   
   
   
    