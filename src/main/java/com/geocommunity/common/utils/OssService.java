package com.geocommunity.common.utils;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.geocommunity.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/**
 * 阿里云 OSS 文件上传服务
 *
 * 使用方式：
 *   String url = ossService.upload(file, "avatar");   // 上传头像
 *   String url = ossService.upload(file, "post");      // 上传帖子图片
 *
 * 返回的是可直接访问的图片 URL，调用方将 URL 存到对应用户/帖子的字段中即可。
 */
@Service
public class OssService implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(OssService.class);

    private final OSS ossClient;
    private final String bucketName;
    private final String endpoint;

    public OssService(@Value("${aliyun.oss.endpoint}") String endpoint,
                      @Value("${aliyun.oss.access-key-id:}") String accessKeyId,
                      @Value("${aliyun.oss.access-key-secret:}") String accessKeySecret,
                      @Value("${aliyun.oss.bucket-name:}") String bucketName) {
        this.endpoint = endpoint;
        this.bucketName = bucketName;
        if (accessKeyId.isBlank() || accessKeySecret.isBlank() || bucketName.isBlank()) {
            log.warn("阿里云 OSS 未配置（ACCESS_KEY_ID/SECRET/BUCKET_NAME 为空），图片上传功能不可用，其余功能正常");
            this.ossClient = null;
        } else {
            this.ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
        }
    }

    /**
     * 上传图片
     *
     * @param file     上传的文件
     * @param dir      目录名，如 avatar（用户头像）/ post（帖子图片）
     * @return 图片的可访问 URL
     */
    public String upload(MultipartFile file, String dir) {
        if (ossClient == null) {
            throw new BusinessException(500, "图片服务未配置，无法上传图片");
        }
        // 生成唯一文件名：目录/UUID_原文件名
        String ext = switch (file.getContentType()) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            default -> throw new BusinessException(400, "图片类型无效");
        };
        String fileName = dir + "/" + UUID.randomUUID() + ext;

        // 上传到 OSS
        try (InputStream input = file.getInputStream()) {
            ossClient.putObject(bucketName, fileName, input);
        } catch (IOException e) {
            throw new RuntimeException("文件上传失败", e);
        }

        // 返回可访问 URL
        String host = endpoint.replace("https://", "");
        return "https://" + bucketName + "." + host + "/" + fileName;
    }

    /**
     * 应用关闭时释放 OSS 客户端连接
     */
    @Override
    public void destroy() {
        if (ossClient != null) {
            ossClient.shutdown();
        }
    }
}
