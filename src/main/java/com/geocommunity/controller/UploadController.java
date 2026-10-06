package com.geocommunity.controller;

import com.geocommunity.common.result.Result;
import com.geocommunity.common.utils.OssService;
import com.geocommunity.common.utils.UserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/upload")
public class UploadController {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
    private static final Set<String> ALLOWED_TYPES = new HashSet<>(Arrays.asList(
            "image/jpeg", "image/png", "image/gif", "image/webp"
    ));

    @Autowired
    private OssService ossService;

    /**
     * 上传图片（通用）
     *
     * @param file 图片文件
     * @param dir  目录：avatar（头像）/ post（帖子图片）
     * @return { url: "..." }
     */
    @PostMapping("/image")
    public Result<Map<String, String>> uploadImage(@RequestParam MultipartFile file,
                                                    @RequestParam(defaultValue = "post") String dir) {
        Long userId = UserContext.get();
        if (userId == null) {
            return Result.fail(401, "未登录");
        }
        if (!Set.of("avatar", "post").contains(dir)) return Result.fail(400, "上传目录无效");
        if (file.isEmpty()) {
            return Result.fail(400, "文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            return Result.fail(400, "文件大小不能超过 5MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            return Result.fail(400, "仅支持 JPG/PNG/GIF/WebP 格式的图片");
        }
        try (var stream = file.getInputStream()) {
            byte[] header = stream.readNBytes(12);
            boolean valid = switch (contentType) {
                case "image/jpeg" -> header.length >= 3 && (header[0] & 255) == 255 && (header[1] & 255) == 216 && (header[2] & 255) == 255;
                case "image/png" -> header.length >= 8 && java.util.Arrays.equals(java.util.Arrays.copyOf(header, 8), new byte[]{(byte)137,80,78,71,13,10,26,10});
                case "image/gif" -> header.length >= 6 && Set.of("GIF87a", "GIF89a").contains(new String(header, 0, 6, java.nio.charset.StandardCharsets.US_ASCII));
                case "image/webp" -> header.length >= 12 && new String(header, 0, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF")
                        && new String(header, 8, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("WEBP");
                default -> false;
            };
            if (!valid) return Result.fail(400, "图片内容与类型不符");
        } catch (java.io.IOException e) { return Result.fail(400, "无法读取图片"); }
        String url = ossService.upload(file, dir);
        return Result.ok(Map.of("url", url));
    }
}
