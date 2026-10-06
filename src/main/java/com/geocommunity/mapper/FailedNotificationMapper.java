package com.geocommunity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.geocommunity.entity.FailedNotification;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface FailedNotificationMapper extends BaseMapper<FailedNotification> {
    @Insert("INSERT INTO notification_failure(event_id,payload,retry_count,status,next_retry_at,last_error,created_at) "
        + "VALUES(#{eventId},#{payload},0,#{status},DATE_ADD(NOW(),INTERVAL 1 MINUTE),#{lastError},NOW()) "
        + "ON DUPLICATE KEY UPDATE last_error=VALUES(last_error), "
        + "next_retry_at=DATE_ADD(NOW(),INTERVAL IF(retry_count=0,60,IF(retry_count=1,300,1800)) SECOND), "
        + "status=IF(status='RESOLVED','RESOLVED',IF(retry_count>=3 OR VALUES(status)='EXHAUSTED','EXHAUSTED','PENDING'))")
    int record(FailedNotification failure);

    @Select("SELECT * FROM notification_failure WHERE status='PENDING' AND next_retry_at<=NOW() "
        + "ORDER BY next_retry_at LIMIT 20 FOR UPDATE SKIP LOCKED")
    List<FailedNotification> lockDue();

    @Update("UPDATE notification_failure SET status='RESOLVED' WHERE event_id=#{eventId}")
    int resolve(String eventId);
}
