package com.wtlx.controller;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.wtlx.entity.dto.Result;
import com.wtlx.utils.SystemConstants;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("upload")
@Api(tags = "文件上传")
public class UploadController {

    @PostMapping("/blog")
    @ApiOperation("上传图片")
    public Result uploadImage(@RequestParam("file") MultipartFile image) {
        try {
            // 获取原始文件名称
            String originalFilename = image.getOriginalFilename();
            // 生成新文件名
            String fileName = createNewFileName(originalFilename);
            // 保存文件
            image.transferTo(new File(SystemConstants.IMAGE_UPLOAD_DIR, fileName));
            fileName="/blogs"+fileName;
            // 返回结果
            log.debug("文件上传成功，{}", fileName);
            return Result.ok(fileName);
        } catch (IOException e) {
            throw new RuntimeException("文件上传失败", e);
        }
    }

    @PostMapping("/video")
    @ApiOperation("上传视频")
    public Result uploadVideo(@RequestParam("file") MultipartFile video) {
        try {
            // 获取原始文件名称
            String originalFilename = video.getOriginalFilename();
            // 生成新文件名
            String fileName = createNewFileName(originalFilename);
            // 保存文件
            video.transferTo(new File(SystemConstants.VIDEO_UPLOAD_DIR, fileName));
            // 返回结果
            log.debug("视频上传成功，{}", fileName);
            return Result.ok(fileName);
        } catch (IOException e) {
            throw new RuntimeException("视频上传失败", e);
        }
    }

    @GetMapping("/blog/delete")
    @ApiOperation("删除图片")
    public Result deleteBlogImg(@RequestParam("name") String filename) {
        File file = new File(SystemConstants.IMAGE_UPLOAD_DIR, filename);
        if (file.isDirectory()) {
            return Result.fail("错误的文件名称");
        }
        FileUtil.del(file);
        return Result.ok();
    }

    @GetMapping("/video/delete")
    @ApiOperation("删除视频")
    public Result deleteVideo(@RequestParam("name") String filename) {
        File file = new File(SystemConstants.VIDEO_UPLOAD_DIR, filename);
        if (file.isDirectory()) {
            return Result.fail("错误的文件名称");
        }
        FileUtil.del(file);
        return Result.ok();
    }

    /**
     * 生成文件名
     * @param originalFilename
     * @return
     */
    private String createNewFileName(String originalFilename) {
        // 获取后缀
        String suffix = StrUtil.subAfter(originalFilename, ".", true);
        // 生成目录
        String name = UUID.randomUUID().toString();
        int hash = name.hashCode();
        int d1 = hash & 0xF;
        int d2 = (hash >> 4) & 0xF;
        // 判断目录是否存在
        String baseDir;
        if (isImage(suffix)) {
            baseDir = SystemConstants.IMAGE_UPLOAD_DIR;
        } else {
            baseDir = SystemConstants.VIDEO_UPLOAD_DIR;
        }
        File dir = new File(baseDir, StrUtil.format("/{}/{}", d1, d2));
        if (!dir.exists()) {
            dir.mkdirs();
        }
        // 生成文件名
        return StrUtil.format("/{}/{}/{}.{}", d1, d2, name, suffix);
    }

    /**
     * 判断是否为图片文件
     * @param suffix 文件后缀
     * @return 是否为图片文件
     */
    private boolean isImage(String suffix) {
        String[] imageSuffixes = {"jpg", "jpeg", "png", "gif"};
        for (String imageSuffix : imageSuffixes) {
            if (imageSuffix.equalsIgnoreCase(suffix)) {
                return true;
            }
        }
        return false;
    }
}