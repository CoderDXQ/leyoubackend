package com.leyou.comments.listener;

import com.leyou.comments.dao.CommentDao;
import com.leyou.comments.pojo.Review;
import com.leyou.comments.utils.IdWorker;
import com.leyou.comments.utils.JsonUtils;
import com.leyou.order.vo.CommentsParameter;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.util.Date;


/**
 * @author Duan Xiangqing
 * @version 1.0
 * @date 2021/2/27 10:38 下午
 */

@Component
public class CommentsListener {

    @Autowired
    private IdWorker idWorker;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private CommentDao commentDao;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "leyou.comments.queue", durable = "true"),//队列持久化
            exchange = @Exchange(
                    value = "leyou.comments.exchange",
                    ignoreDeclarationExceptions = "true",
                    type = ExchangeTypes.TOPIC
            ),
            key = {"user.comments"}
    ))
    public void listenCommentsMessage(String string) {
        CommentsParameter commentsParameter = JsonUtils.parse(string, CommentsParameter.class);
        if (commentsParameter == null) {
            return;
        }
        Review review = commentsParameter.getReview();

        review.set_id(idWorker.nextId() + "");
        review.setPublishtime(new Date());
        review.setComment(0);
        review.setThumbup(0);
        review.setVisits(0);

//        更新上级评论的数据  这里是对上级评论的操作
        if (review.getParentid() != null && !"".equals(review.getParentid())) {
            Query query = new Query();
            query.addCriteria(Criteria.where("_id").is(review.getParentid()));

            Update update = new Update();
            update.inc("comment", 1);
            update.set("isparent", true);
            update.inc("visits", 1);
//            更新MongoDB 根据_id找到上级评论并更新数据
            this.mongoTemplate.updateFirst(query, update, "review");
        }
//        存储这次的评论 用的也是MongoDB
        commentDao.save(review);
    }


}
